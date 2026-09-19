package org.meetagain.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.meetagain.app.core.cache.CachedAnswer
import org.meetagain.app.core.cache.CachedAnswerDao
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventListDto
import org.meetagain.app.core.network.EventSummaryDto
import org.meetagain.app.core.network.GroupDetailDto
import org.meetagain.app.core.network.GroupListDto
import org.meetagain.app.core.network.GroupSummaryDto

/**
 * The public events and groups anyone can see without signing in. It turns the server's answers into the app's
 * models and hides what the server shows but should not: groups that are not public, events without a text in the
 * member's language.
 *
 * Every read is offline-first: the screens observe the last answer stored for it in the member's language, and a
 * refresh fetches a new one and stores it. A failed refresh keeps the stored answer, except when the server says the
 * content is gone (404).
 */
class PublicRepository(
    private val api: ApiClient,
    baseUrl: String,
    private val answers: CachedAnswerDao,
    private val json: Json,
    private val language: () -> String,
    private val clock: Clock = Clock.systemUTC()
) {
    private val baseHost = baseUrl.toHttpUrl().host

    /** Ended events are dropped on every read, so a list stored days ago never shows them as upcoming. */
    fun upcomingEvents(group: String? = null): Flow<Cached<Upcoming>?> =
        observe(eventsKey(group), EventListDto.serializer()) { list ->
            val now = clock.instant()
            Upcoming(
                list.items.mapNotNull { it.toEvent() }.filter { it.calendarEnd.isAfter(now) },
                complete = list.items.size >= list.total
            )
        }

    suspend fun refreshUpcomingEvents(group: String? = null): ApiResult<Unit> =
        // Paging by offset returns short, overlapping pages, so the app asks once for the most the server allows.
        refresh(eventsKey(group), EventListDto.serializer()) {
            api.events(OffsetDateTime.now(clock), EVENT_LIMIT, group)
        }

    fun event(id: Int): Flow<Cached<EventDetails>?> = observe(eventKey(id), EventDetailDto.serializer()) {
        it.toDetails()
    }

    /** An event without a text in the member's language is not found, as on the website. */
    suspend fun refreshEvent(id: Int): ApiResult<Unit> = refresh(
        eventKey(id),
        EventDetailDto.serializer(),
        check = {
            when {
                it.title.isBlank() -> ApiError.Http(404)
                it.toDetails() == null -> ApiError.Malformed
                else -> null
            }
        }
    ) { api.event(id) }

    fun groups(): Flow<Cached<List<Group>>?> = observe(GROUPS_KEY, GroupListDto.serializer()) { list ->
        list.items.filter { it.isListed() }.map { it.toGroup() }
    }

    suspend fun refreshGroups(): ApiResult<Unit> = refresh(GROUPS_KEY, GroupListDto.serializer()) { api.groups() }

    fun group(slug: String): Flow<Cached<GroupDetails>?> =
        observe(groupKey(slug), GroupDetailDto.serializer()) { dto -> dto.takeIf { it.isListed() }?.toDetails() }

    suspend fun refreshGroup(slug: String): ApiResult<Unit> = refresh(
        groupKey(slug),
        GroupDetailDto.serializer(),
        check = { if (it.isListed()) null else ApiError.Http(404) }
    ) { api.group(slug) }

    /** Forgets what has not been shown for [unused], so the cache holds only what the member still looks at. */
    suspend fun forgetUnused(unused: Duration = CACHE_RETENTION) = answers.deleteUnusedSince(clock.instant() - unused)

    /** A stored body that no longer decodes, after an app update changed a DTO, counts as nothing stored. */
    private fun <D, T : Any> observe(key: String, serializer: KSerializer<D>, map: (D) -> T?): Flow<Cached<T>?> {
        val language = language()
        return answers.observe(key, language)
            .map { row ->
                row?.let { stored ->
                    decode(stored.body, serializer)?.let(map)?.let { Cached(it, stored.syncedAt) }
                }
            }
            .distinctUntilChanged()
            .onStart { answers.markUsed(key, language, clock.instant()) }
    }

    /** [check] turns an answer the app must not show into an error: 404 forgets the stored answer, others keep it. */
    private suspend fun <D> refresh(
        key: String,
        serializer: KSerializer<D>,
        check: (D) -> ApiError? = { null },
        fetch: suspend () -> ApiResult<D>
    ): ApiResult<Unit> {
        val language = language()
        val error = when (val result = fetch()) {
            is ApiResult.Failure -> result.error

            is ApiResult.Success -> check(result.value) ?: run {
                val now = clock.instant()
                answers.put(CachedAnswer(key, language, json.encodeToString(serializer, result.value), now, now))
                return ApiResult.Success(Unit)
            }
        }
        if (error.isNotFound) answers.delete(key, language)
        return ApiResult.Failure(error)
    }

    private fun <D> decode(body: String, serializer: KSerializer<D>): D? = try {
        json.decodeFromString(serializer, body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun EventSummaryDto.toEvent(): Event? =
        eventOf(id, title, teaser, start, stop, type, rsvpCount, previewImageUrl, webUrl)

    private fun EventDetailDto.toDetails(): EventDetails? {
        val event = eventOf(id, title, teaser, start, stop, type, rsvpCount, previewImageUrl, webUrl) ?: return null
        return EventDetails(
            event = event,
            description = description?.takeIf { it.isNotBlank() },
            location = location
                ?.let { Location(it.name.orNull(), it.street.orNull(), it.postcode.orNull(), it.city.orNull()) }
                ?.takeIf { it.query.isNotEmpty() },
            photoUrls = images.mapNotNull { ownImage(it) }
        )
    }

    /**
     * Null for an event without a title in the member's language, which the website does not list either, or without
     * a readable start.
     */
    private fun eventOf(
        id: Int,
        title: String,
        teaser: String?,
        start: String,
        stop: String?,
        type: Int?,
        going: Int,
        imageUrl: String?,
        webUrl: String
    ): Event? {
        if (title.isBlank()) return null
        val startsAt = instant(start) ?: return null
        return Event(
            id = id,
            title = title,
            teaser = teaser.orNull(),
            start = startsAt,
            end = stop?.let(::instant),
            kind = when (type) {
                3 -> EventKind.Outdoor
                4 -> EventKind.Dinner
                else -> null
            },
            going = going,
            imageUrl = ownImage(imageUrl),
            webUrl = webUrl
        )
    }

    private fun GroupSummaryDto.isListed() = visibility == PUBLIC && slug != PLATFORM_GROUP

    private fun GroupDetailDto.isListed() = visibility == PUBLIC && slug != PLATFORM_GROUP

    private fun GroupSummaryDto.toGroup() = Group(slug, name, ownImage(logoUrl))

    private fun GroupDetailDto.toDetails() = GroupDetails(
        group = Group(slug, name, ownImage(logoUrl)),
        description = description.orNull(),
        memberCount = memberCount,
        websiteUrl = domain.orNull()?.let { "https://$it" }?.toHttpUrlOrNull()?.toString(),
        languages = languages.mapNotNull { it.orNull() }
    )

    /** Only images on the app's own server, so content can never make the app contact anyone else. */
    private fun ownImage(url: String?): String? = url?.toHttpUrlOrNull()?.takeIf { it.host == baseHost }?.toString()

    private fun instant(value: String): Instant? = try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    private fun String?.orNull() = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val GROUPS_KEY = "groups"
        val CACHE_RETENTION: Duration = Duration.ofDays(30)

        fun eventsKey(group: String?) = if (group == null) "events" else "events?group=$group"

        fun eventKey(id: Int) = "event/$id"

        fun groupKey(slug: String) = "group/$slug"

        const val EVENT_LIMIT = 100
        const val PUBLIC = "public"
        const val PLATFORM_GROUP = "main-site"
    }
}

/** The last answer stored for a read, as the app's model, and when the server gave it. */
data class Cached<T>(val value: T, val syncedAt: Instant)

val ApiError.isNotFound: Boolean get() = this is ApiError.Http && status == 404
