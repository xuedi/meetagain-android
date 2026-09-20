package org.meetagain.app.core.data

import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventListDto
import org.meetagain.app.core.network.GroupDetailDto
import org.meetagain.app.core.network.GroupListDto
import org.meetagain.app.core.network.GroupSummaryDto

/**
 * The events and groups the server shows for this request: everything public while nobody is signed in, and the
 * member's own Hidden and Private groups on top of that once someone is.
 *
 * Every read is offline-first through [cache]: the screens observe the last answer stored for it in the member's
 * language, and a refresh fetches a new one. A failed refresh keeps the stored answer, except when the server says
 * the content is gone (404).
 */
class PublicRepository(
    private val api: ApiClient,
    baseUrl: String,
    private val cache: AnswerCache,
    private val clock: Clock = Clock.systemUTC()
) {
    private val images = ImageHost(baseUrl.toHttpUrl().host)

    /** Ended events are dropped on every read, so a list stored days ago never shows them as upcoming. */
    fun upcomingEvents(group: String? = null): Flow<Cached<Upcoming>?> =
        cache.observe(Keys.events(group), EventListDto.serializer()) { list -> list.toUpcoming(images, clock) }

    suspend fun refreshUpcomingEvents(group: String? = null): ApiResult<Unit> =
        cache.refresh(Keys.events(group), EventListDto.serializer()) {
            api.events(OffsetDateTime.now(clock), EVENT_WINDOW, group = group)
        }

    fun event(id: Int): Flow<Cached<EventDetails>?> =
        cache.observe(Keys.event(id), EventDetailDto.serializer()) { it.toDetails(images) }

    /** An answer the app cannot make an event of, for instance without a readable start, is not stored. */
    suspend fun refreshEvent(id: Int): ApiResult<Unit> = cache.refresh(
        Keys.event(id),
        EventDetailDto.serializer(),
        check = { if (it.toDetails(images) == null) ApiError.Malformed else null }
    ) { api.event(id) }

    fun groups(): Flow<Cached<List<Group>>?> = cache.observe(Keys.GROUPS, GroupListDto.serializer()) { list ->
        list.items.map { it.toGroup(images) }
    }

    suspend fun refreshGroups(): ApiResult<Unit> = cache.refresh(Keys.GROUPS, GroupListDto.serializer()) {
        api.groups()
    }

    fun group(slug: String): Flow<Cached<GroupDetails>?> =
        cache.observe(Keys.group(slug), GroupDetailDto.serializer()) { it.toDetails(images) }

    suspend fun refreshGroup(slug: String): ApiResult<Unit> =
        cache.refresh(Keys.group(slug), GroupDetailDto.serializer()) { api.group(slug) }

    suspend fun forgetUnused(unused: Duration? = null) =
        if (unused == null) cache.forgetUnused() else cache.forgetUnused(unused)

    private companion object {
        /** One window of upcoming events, which is as much as the explore screen ever shows. */
        const val EVENT_WINDOW = 100
    }
}

private fun GroupSummaryDto.toGroup(images: ImageHost) = Group(slug, name, images.own(logoUrl))

private fun GroupDetailDto.toDetails(images: ImageHost) = GroupDetails(
    group = Group(slug, name, images.own(logoUrl)),
    description = description.orNull(),
    memberCount = memberCount,
    websiteUrl = domain.orNull()?.let { "https://$it" }?.toHttpUrlOrNull()?.toString(),
    languages = languages.mapNotNull { it.orNull() }
)
