package org.meetagain.app.core.data

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventSummaryDto
import org.meetagain.app.core.network.GroupDetailDto
import org.meetagain.app.core.network.GroupSummaryDto

/**
 * The public events and groups anyone can see without signing in. It turns the server's answers into the app's
 * models and hides what the server shows but should not: groups that are not public, events without a text in the
 * member's language.
 */
class PublicRepository(private val api: ApiClient, baseUrl: String, private val clock: Clock = Clock.systemUTC()) {
    private val baseHost = baseUrl.toHttpUrl().host

    suspend fun upcomingEvents(group: String? = null): ApiResult<Upcoming> {
        // Paging by offset returns short, overlapping pages, so the app asks once for the most the server allows.
        val result = api.events(OffsetDateTime.now(clock), EVENT_LIMIT, group)
        return result.map { list ->
            Upcoming(list.items.mapNotNull { it.toEvent() }, complete = list.items.size >= list.total)
        }
    }

    /** An event without a text in the member's language is not found, as on the website. */
    suspend fun event(id: Int): ApiResult<EventDetails> = when (val result = api.event(id)) {
        is ApiResult.Failure -> result

        is ApiResult.Success -> when {
            result.value.title.isBlank() -> ApiResult.Failure(ApiError.Http(404))
            else -> result.value.toDetails()?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.Malformed)
        }
    }

    suspend fun groups(): ApiResult<List<Group>> =
        api.groups().map { list -> list.items.filter { it.isListed() }.map { it.toGroup() } }

    suspend fun group(slug: String): ApiResult<GroupDetails> = when (val result = api.group(slug)) {
        is ApiResult.Failure -> result

        is ApiResult.Success -> if (result.value.isListed()) {
            ApiResult.Success(result.value.toDetails())
        } else {
            ApiResult.Failure(ApiError.Http(404))
        }
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
        websiteUrl = domain.orNull()?.let { "https://$it" }?.toHttpUrlOrNull()?.toString()
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
        const val EVENT_LIMIT = 100
        const val PUBLIC = "public"
        const val PLATFORM_GROUP = "main-site"
    }
}

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
