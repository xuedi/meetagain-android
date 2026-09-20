package org.meetagain.app.core.data

import java.time.Clock
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.AttendeeListDto
import org.meetagain.app.core.network.CommentListDto
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventListDto
import org.meetagain.app.core.network.GroupDetailDto
import org.meetagain.app.core.network.ImageListDto
import org.meetagain.app.core.network.InvitationListDto
import org.meetagain.app.core.network.MeDto
import org.meetagain.app.core.network.MembershipListDto
import org.meetagain.app.core.network.ProfileChangeDto
import org.meetagain.app.core.network.Upload

/**
 * Everything the app reads and writes as the signed-in member. The reads go through the same offline-first cache as
 * the public ones, under the member's own keys; the writes need a connection and refresh what they changed.
 */
class MemberRepository(
    private val api: ApiClient,
    baseUrl: String,
    private val cache: AnswerCache,
    private val clock: Clock = Clock.systemUTC()
) {
    private val images = ImageHost(baseUrl.toHttpUrl().host)

    // The member's own meetings

    fun myEvents(): Flow<Cached<Upcoming>?> =
        cache.observe(Keys.MY_EVENTS, EventListDto.serializer()) { it.toUpcoming(images, clock) }

    suspend fun refreshMyEvents(): ApiResult<Unit> = cache.refresh(Keys.MY_EVENTS, EventListDto.serializer()) {
        api.myEvents(OffsetDateTime.now(clock), EVENT_WINDOW)
    }

    fun occurrences(id: Int): Flow<Cached<Upcoming>?> =
        cache.observe(Keys.occurrences(id), EventListDto.serializer()) { it.toUpcoming(images, clock) }

    suspend fun refreshOccurrences(id: Int): ApiResult<Unit> =
        cache.refresh(Keys.occurrences(id), EventListDto.serializer()) { api.occurrences(id) }

    /** Says yes or no for the member. The lists that show the answer are refreshed after it. */
    suspend fun rsvp(id: Int, going: Boolean, guests: Int = 0): ApiResult<Rsvp> {
        val result = api.rsvp(id, going, guests)
        return when (result) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)

            is ApiResult.Success -> {
                refreshAround(id)
                ApiResult.Success(Rsvp(result.value.rsvp, result.value.guests))
            }
        }
    }

    // Who is coming, and what is said

    fun attendees(id: Int): Flow<Cached<Attendees>?> =
        cache.observe(Keys.attendees(id), AttendeeListDto.serializer()) { it.toAttendees(images) }

    suspend fun refreshAttendees(id: Int): ApiResult<Unit> =
        cache.refresh(Keys.attendees(id), AttendeeListDto.serializer()) { api.attendees(id) }

    fun conversation(id: Int): Flow<Cached<Conversation>?> =
        cache.observe(Keys.comments(id), CommentListDto.serializer()) { it.toConversation(images) }

    suspend fun refreshConversation(id: Int): ApiResult<Unit> =
        cache.refresh(Keys.comments(id), CommentListDto.serializer()) { api.comments(id) }

    /** The page before the one the member has read; it is not stored, only the newest page is. */
    suspend fun olderComments(id: Int, before: Int): ApiResult<Conversation> = when (
        val result = api.comments(id, before = before)
    ) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)
        is ApiResult.Success -> ApiResult.Success(result.value.toConversation(images))
    }

    suspend fun addComment(id: Int, text: String): ApiResult<Unit> = after(api.addComment(id, text)) {
        refreshConversation(id)
    }

    suspend fun deleteComment(id: Int, commentId: Int): ApiResult<Unit> =
        after(api.deleteComment(id, commentId)) { refreshConversation(id) }

    fun photos(id: Int): Flow<Cached<List<Photo>>?> =
        cache.observe(Keys.photos(id), ImageListDto.serializer()) { it.toPhotos(images) }

    suspend fun refreshPhotos(id: Int): ApiResult<Unit> =
        cache.refresh(Keys.photos(id), ImageListDto.serializer()) { api.images(id) }

    suspend fun addPhoto(id: Int, upload: Upload): ApiResult<Unit> = after(api.addImage(id, upload)) {
        refreshPhotos(id)
        refreshEvent(id)
    }

    suspend fun deletePhoto(id: Int, imageId: Int): ApiResult<Unit> = after(api.deleteImage(id, imageId)) {
        refreshPhotos(id)
        refreshEvent(id)
    }

    // Groups and invitations

    fun myGroups(): Flow<Cached<List<Membership>>?> =
        cache.observe(Keys.MY_GROUPS, MembershipListDto.serializer()) { it.toMemberships(images) }

    suspend fun refreshMyGroups(): ApiResult<Unit> =
        cache.refresh(Keys.MY_GROUPS, MembershipListDto.serializer()) { api.myGroups() }

    fun invitations(): Flow<Cached<List<Invitation>>?> =
        cache.observe(Keys.INVITATIONS, InvitationListDto.serializer()) { it.toInvitations(images) }

    suspend fun refreshInvitations(): ApiResult<Unit> =
        cache.refresh(Keys.INVITATIONS, InvitationListDto.serializer()) { api.invitations() }

    suspend fun join(slug: String, mailConsent: Boolean? = null): ApiResult<Membership> =
        when (val result = api.joinGroup(slug, mailConsent)) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)

            is ApiResult.Success -> {
                refreshAfterMembershipChange(slug)
                ApiResult.Success(result.value.toMembership(images))
            }
        }

    suspend fun leave(slug: String): ApiResult<Unit> = after(api.leaveGroup(slug)) {
        refreshAfterMembershipChange(slug)
    }

    suspend fun acceptInvitation(id: Int, slug: String, mailConsent: Boolean? = null): ApiResult<Membership> =
        when (val result = api.acceptInvitation(id, mailConsent)) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)

            is ApiResult.Success -> {
                refreshInvitations()
                refreshAfterMembershipChange(slug)
                ApiResult.Success(result.value.toMembership(images))
            }
        }

    suspend fun declineInvitation(id: Int): ApiResult<Unit> = after(api.declineInvitation(id)) {
        refreshInvitations()
    }

    // The profile

    fun profile(): Flow<Cached<Profile>?> = cache.observe(Keys.ME, MeDto.serializer()) { it.toProfile(images) }

    suspend fun refreshProfile(): ApiResult<Unit> = cache.refresh(Keys.ME, MeDto.serializer()) { api.me() }

    suspend fun updateProfile(change: ProfileChangeDto): ApiResult<Profile> = when (
        val result = api.updateMe(change)
    ) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)

        is ApiResult.Success -> {
            refreshProfile()
            ApiResult.Success(result.value.toProfile(images))
        }
    }

    suspend fun uploadAvatar(upload: Upload): ApiResult<Profile> = when (val result = api.uploadAvatar(upload)) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)

        is ApiResult.Success -> {
            refreshProfile()
            ApiResult.Success(result.value.toProfile(images))
        }
    }

    /** The member's own answer shows on the event, on their home list and in who is coming. */
    private suspend fun refreshAround(id: Int) {
        refreshEvent(id)
        refreshMyEvents()
        refreshAttendees(id)
    }

    /**
     * Joining or leaving changes what the server shows this member of that group, so those answers are fetched
     * again rather than forgotten: the page the member is looking at would otherwise go empty.
     */
    private suspend fun refreshAfterMembershipChange(slug: String) {
        refreshMyGroups()
        refreshMyEvents()
        cache.refresh(Keys.group(slug), GroupDetailDto.serializer()) { api.group(slug) }
        cache.refresh(Keys.events(slug), EventListDto.serializer()) {
            api.events(OffsetDateTime.now(clock), EVENT_WINDOW, group = slug)
        }
    }

    private suspend fun refreshEvent(id: Int) =
        cache.refresh(Keys.event(id), EventDetailDto.serializer()) { api.event(id) }

    private suspend fun <T> after(result: ApiResult<T>, refresh: suspend () -> Unit): ApiResult<Unit> = when (result) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)

        is ApiResult.Success -> {
            refresh()
            ApiResult.Success(Unit)
        }
    }

    private companion object {
        const val EVENT_WINDOW = 100
    }
}
