package org.meetagain.app.core.data

import java.time.Clock
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.AttendeeListDto
import org.meetagain.app.core.network.CommentListDto
import org.meetagain.app.core.network.ConversationListDto
import org.meetagain.app.core.network.EventDetailDto
import org.meetagain.app.core.network.EventListDto
import org.meetagain.app.core.network.GroupDetailDto
import org.meetagain.app.core.network.ImageListDto
import org.meetagain.app.core.network.InvitationListDto
import org.meetagain.app.core.network.MeDto
import org.meetagain.app.core.network.MemberListDto
import org.meetagain.app.core.network.MemberProfileDto
import org.meetagain.app.core.network.MembershipListDto
import org.meetagain.app.core.network.MessageThreadDto
import org.meetagain.app.core.network.NotificationListDto
import org.meetagain.app.core.network.NotificationSettingsChangeDto
import org.meetagain.app.core.network.NotificationSettingsDto
import org.meetagain.app.core.network.ProfileChangeDto
import org.meetagain.app.core.network.PushRegistrationDto
import org.meetagain.app.core.network.THREAD_PAGE
import org.meetagain.app.core.network.TRANSPORT_UNIFIEDPUSH
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

    // What the member is told, and what they want to hear about

    fun notifications(): Flow<Cached<List<Notification>>?> =
        cache.observe(Keys.NOTIFICATIONS, NotificationListDto.serializer()) { it.toNotifications() }

    suspend fun refreshNotifications(): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATIONS, NotificationListDto.serializer()) { api.notifications() }

    fun notificationSettings(): Flow<Cached<NotificationSettings>?> =
        cache.observe(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) { it.toSettings() }

    suspend fun refreshNotificationSettings(): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) {
            api.notificationSettings()
        }

    /**
     * Turns one switch, sending that key alone. The server answers with everything it has stored, which is what is
     * kept, so a setting this app has no screen for cannot be written back stale.
     */
    suspend fun setNotificationSetting(setting: NotificationSetting, value: Boolean): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) {
            api.updateNotificationSettings(setting.change(value))
        }

    // Push devices

    /**
     * Registering is not cached: it is a write whose answer only matters for its id, and the list is read fresh
     * whenever the app needs to know what is registered.
     */
    suspend fun pushDevices(): ApiResult<PushDevices> = when (val result = api.pushSubscriptions()) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)
        is ApiResult.Success -> ApiResult.Success(result.value.toDevices())
    }

    suspend fun registerPush(registration: PushRegistration): ApiResult<Int> = when (
        val result = api.registerPush(
            PushRegistrationDto(
                registration.endpoint,
                registration.p256dh,
                registration.auth,
                TRANSPORT_UNIFIEDPUSH
            )
        )
    ) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)
        is ApiResult.Success -> ApiResult.Success(result.value.id)
    }

    suspend fun deletePushDevice(id: Int): ApiResult<Unit> = api.deletePushSubscription(id)

    /** Turns one push category on or off, sending that category alone inside the push map. */
    suspend fun setPushCategory(category: PushCategory, value: Boolean): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) {
            api.updateNotificationSettings(pushChange(mapOf(category.key to value)))
        }

    suspend fun setQuietHours(quietHours: QuietHours): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) {
            api.updateNotificationSettings(NotificationSettingsChangeDto(quietHours = quietHours.toDto()))
        }

    /** Copies the six email switches onto the four push categories, as one explicit act by the member. */
    suspend fun matchPushToEmail(settings: NotificationSettings): ApiResult<Unit> =
        cache.refresh(Keys.NOTIFICATION_SETTINGS, NotificationSettingsDto.serializer()) {
            api.updateNotificationSettings(
                pushChange(
                    mapOf(
                        PushCategory.EventChanges.key to settings.attendedEventUpdate,
                        PushCategory.Reminders.key to settings.eventReminder,
                        PushCategory.Messages.key to settings.receivedMessage,
                        PushCategory.Announcements.key to settings.announcements
                    )
                )
            )
        }

    // Messages

    fun inbox(): Flow<Cached<Inbox>?> =
        cache.observe(Keys.CONVERSATIONS, ConversationListDto.serializer()) { it.toInbox(images) }

    suspend fun refreshInbox(): ApiResult<Unit> =
        cache.refresh(Keys.CONVERSATIONS, ConversationListDto.serializer()) { api.conversations() }

    /** The page after the one the member has read; only the first page is stored. */
    suspend fun moreConversations(offset: Int): ApiResult<Inbox> = when (
        val result = api.conversations(offset = offset)
    ) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)
        is ApiResult.Success -> ApiResult.Success(result.value.toInbox(images))
    }

    fun thread(userId: Int): Flow<Cached<MessageThread>?> =
        cache.observe(Keys.conversation(userId), MessageThreadDto.serializer()) { it.toThread(images, clock) }

    suspend fun refreshThread(userId: Int): ApiResult<Unit> =
        cache.refresh(Keys.conversation(userId), MessageThreadDto.serializer()) { newestThreadPage(userId) }

    /**
     * A thread reads oldest first, so its newest page is its last one. One call is enough for a thread that fits in
     * a page; a longer one takes a second, at the offset the first answer's total gives.
     */
    private suspend fun newestThreadPage(userId: Int): ApiResult<MessageThreadDto> {
        val first = api.thread(userId)
        val page = (first as? ApiResult.Success)?.value ?: return first
        if (page.total <= THREAD_PAGE) return first
        return api.thread(userId, offset = page.total - THREAD_PAGE)
    }

    /** The page before the one on screen, asked for by where that one starts; like older comments it is not stored. */
    suspend fun earlierMessages(userId: Int, before: Int): ApiResult<MessageThread> {
        val offset = (before - THREAD_PAGE).coerceAtLeast(0)
        return when (val result = api.thread(userId, limit = before - offset, offset = offset)) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)
            is ApiResult.Success -> ApiResult.Success(result.value.toThread(images, clock))
        }
    }

    suspend fun sendMessage(userId: Int, text: String): ApiResult<Unit> = after(api.sendMessage(userId, text)) {
        refreshThread(userId)
        refreshInbox()
    }

    suspend fun editMessage(userId: Int, messageId: Int, text: String): ApiResult<Unit> =
        after(api.editMessage(messageId, text)) { refreshThread(userId) }

    /** The website marks a thread read while rendering it; here the screen says so once it has shown the newest page. */
    suspend fun markThreadRead(userId: Int): ApiResult<Unit> = after(api.markThreadRead(userId)) { refreshInbox() }

    // Members, following and blocking

    fun member(id: Int): Flow<Cached<MemberProfile>?> =
        cache.observe(Keys.member(id), MemberProfileDto.serializer()) { it.toMemberProfile(images) }

    suspend fun refreshMember(id: Int): ApiResult<Unit> =
        cache.refresh(Keys.member(id), MemberProfileDto.serializer()) { api.member(id) }

    fun groupMembers(slug: String): Flow<Cached<Members>?> =
        cache.observe(Keys.groupMembers(slug), MemberListDto.serializer()) { it.toMembers(images) }

    suspend fun refreshGroupMembers(slug: String): ApiResult<Unit> =
        cache.refresh(Keys.groupMembers(slug), MemberListDto.serializer()) { api.groupMembers(slug) }

    suspend fun moreGroupMembers(slug: String, offset: Int): ApiResult<Members> = when (
        val result = api.groupMembers(slug, offset = offset)
    ) {
        is ApiResult.Failure -> ApiResult.Failure(result.error)
        is ApiResult.Success -> ApiResult.Success(result.value.toMembers(images))
    }

    fun blocked(): Flow<Cached<Members>?> =
        cache.observe(Keys.BLOCKS, MemberListDto.serializer()) { it.toMembers(images) }

    suspend fun refreshBlocked(): ApiResult<Unit> =
        cache.refresh(Keys.BLOCKS, MemberListDto.serializer()) { api.blocks() }

    suspend fun follow(id: Int): ApiResult<Unit> = after(api.follow(id)) { refreshMember(id) }

    suspend fun unfollow(id: Int): ApiResult<Unit> = after(api.unfollow(id)) { refreshMember(id) }

    suspend fun block(id: Int): ApiResult<Unit> = after(api.block(id)) { refreshAfterBlockChange(id) }

    suspend fun unblock(id: Int): ApiResult<Unit> = after(api.unblock(id)) { refreshAfterBlockChange(id) }

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

    /**
     * Blocking is mutual in effect: the conversation leaves the inbox, the member joins the blocked list, and their
     * page answers differently. All three are fetched again rather than forgotten.
     */
    private suspend fun refreshAfterBlockChange(id: Int) {
        refreshInbox()
        refreshBlocked()
        refreshMember(id)
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
