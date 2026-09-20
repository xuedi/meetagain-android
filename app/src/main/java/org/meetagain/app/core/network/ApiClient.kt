package org.meetagain.app.core.network

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source

class ApiClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val json: Json,
    private val languageTag: () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val base = baseUrl.toHttpUrl()

    suspend fun status(): ApiResult<Status> = get(url("api/status"), Status.serializer())

    // Signing in and out

    suspend fun login(email: String, password: String, deviceName: String): ApiResult<LoginResultDto> = send(
        request(url("api/v1/auth/login")).post(body(LoginRequestDto(email, password, deviceName))),
        LoginResultDto.serializer()
    )

    suspend fun logout(): ApiResult<Unit> = noContent(request(url("api/v1/auth/logout")).post(EMPTY))

    // Public reads, which answer with more when the call carries a member's token

    /** Upcoming events from [from] on, optionally of one group. */
    suspend fun events(
        from: OffsetDateTime,
        limit: Int,
        offset: Int = 0,
        group: String? = null
    ): ApiResult<EventListDto> {
        val url = url("api/v1/events").newBuilder()
            .addQueryParameter(
                "from",
                from.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
            .addQueryParameter("limit", limit.toString())
            .apply { if (offset > 0) addQueryParameter("offset", offset.toString()) }
            .apply { if (group != null) addQueryParameter("group", group) }
            .build()
        return get(url, EventListDto.serializer())
    }

    suspend fun event(id: Int): ApiResult<EventDetailDto> =
        get(url("api/v1/events").newBuilder().addPathSegment(id.toString()).build(), EventDetailDto.serializer())

    suspend fun groups(): ApiResult<GroupListDto> = get(url("api/v1/groups"), GroupListDto.serializer())

    suspend fun group(slug: String): ApiResult<GroupDetailDto> =
        get(url("api/v1/groups").newBuilder().addPathSegment(slug).build(), GroupDetailDto.serializer())

    // The member themselves

    suspend fun me(): ApiResult<MeDto> = get(url("api/v1/me"), MeDto.serializer())

    suspend fun updateMe(change: ProfileChangeDto): ApiResult<MeDto> =
        send(request(url("api/v1/me")).patch(body(change)), MeDto.serializer())

    suspend fun uploadAvatar(upload: Upload): ApiResult<MeDto> =
        send(request(url("api/v1/me/avatar")).post(multipart(upload)), MeDto.serializer())

    suspend fun myEvents(from: OffsetDateTime, limit: Int, offset: Int = 0): ApiResult<EventListDto> {
        val url = url("api/v1/me/events").newBuilder()
            .addQueryParameter(
                "from",
                from.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
            .addQueryParameter("limit", limit.toString())
            .apply { if (offset > 0) addQueryParameter("offset", offset.toString()) }
            .build()
        return get(url, EventListDto.serializer())
    }

    suspend fun myGroups(): ApiResult<MembershipListDto> = get(url("api/v1/me/groups"), MembershipListDto.serializer())

    /** The bell, as the website shows it, in the language this call asks for. */
    suspend fun notifications(): ApiResult<NotificationListDto> =
        get(url("api/v1/me/notifications"), NotificationListDto.serializer())

    suspend fun notificationSettings(): ApiResult<NotificationSettingsDto> =
        get(url("api/v1/me/notification-settings"), NotificationSettingsDto.serializer())

    /** Only the keys [change] names are sent, so the settings this app has no screen for keep their stored value. */
    suspend fun updateNotificationSettings(change: NotificationSettingsChangeDto): ApiResult<NotificationSettingsDto> =
        send(
            request(url("api/v1/me/notification-settings")).patch(body(change)),
            NotificationSettingsDto.serializer()
        )

    /** The devices registered for push, and the key one needs before it can register. */
    suspend fun pushSubscriptions(): ApiResult<PushSubscriptionListDto> =
        get(url("api/v1/me/push-subscriptions"), PushSubscriptionListDto.serializer())

    suspend fun registerPush(registration: PushRegistrationDto): ApiResult<PushSubscriptionDto> = send(
        request(url("api/v1/me/push-subscriptions")).post(body(registration)),
        PushSubscriptionDto.serializer()
    )

    suspend fun deletePushSubscription(id: Int): ApiResult<Unit> = noContent(
        request(
            url("api/v1/me/push-subscriptions").newBuilder().addPathSegment(id.toString()).build()
        ).delete()
    )

    // What happens around an event

    suspend fun rsvp(id: Int, going: Boolean, guests: Int): ApiResult<RsvpResultDto> = send(
        request(eventUrl(id, "rsvp")).put(body(RsvpRequestDto(going, guests.takeIf { going }))),
        RsvpResultDto.serializer()
    )

    suspend fun occurrences(id: Int): ApiResult<EventListDto> =
        get(eventUrl(id, "occurrences"), EventListDto.serializer())

    suspend fun attendees(id: Int): ApiResult<AttendeeListDto> =
        get(eventUrl(id, "attendees"), AttendeeListDto.serializer())

    suspend fun comments(id: Int, before: Int? = null, limit: Int = COMMENT_PAGE): ApiResult<CommentListDto> {
        val url = eventUrl(id, "comments").newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { if (before != null) addQueryParameter("before", before.toString()) }
            .build()
        return get(url, CommentListDto.serializer())
    }

    suspend fun addComment(id: Int, content: String): ApiResult<CommentCreatedDto> = send(
        request(eventUrl(id, "comments")).post(body(CommentRequestDto(content))),
        CommentCreatedDto.serializer()
    )

    suspend fun deleteComment(id: Int, commentId: Int): ApiResult<Unit> = noContent(
        request(eventUrl(id, "comments").newBuilder().addPathSegment(commentId.toString()).build()).delete()
    )

    suspend fun images(id: Int): ApiResult<ImageListDto> = get(eventUrl(id, "images"), ImageListDto.serializer())

    suspend fun addImage(id: Int, upload: Upload): ApiResult<ImageUploadedDto> =
        send(request(eventUrl(id, "images")).post(multipart(upload)), ImageUploadedDto.serializer())

    suspend fun deleteImage(id: Int, imageId: Int): ApiResult<Unit> = noContent(
        request(eventUrl(id, "images").newBuilder().addPathSegment(imageId.toString()).build()).delete()
    )

    // Memberships

    suspend fun invitations(): ApiResult<InvitationListDto> =
        get(url("api/v1/memberships/invitations"), InvitationListDto.serializer())

    suspend fun acceptInvitation(id: Int, mailConsent: Boolean?): ApiResult<MembershipDto> = send(
        request(invitationUrl(id, "accept")).post(body(ConsentDto(mailConsent))),
        MembershipDto.serializer()
    )

    suspend fun declineInvitation(id: Int): ApiResult<Unit> =
        noContent(request(invitationUrl(id, "decline")).post(EMPTY))

    suspend fun joinGroup(slug: String, mailConsent: Boolean?): ApiResult<MembershipDto> =
        send(request(membershipUrl(slug)).post(body(ConsentDto(mailConsent))), MembershipDto.serializer())

    suspend fun leaveGroup(slug: String): ApiResult<Unit> = noContent(request(membershipUrl(slug)).delete())

    // The community: messages, members, following and blocking

    suspend fun conversations(limit: Int = COMMUNITY_PAGE, offset: Int = 0): ApiResult<ConversationListDto> =
        get(paged(communityUrl("conversations"), limit, offset), ConversationListDto.serializer())

    /** One page of a thread, oldest first. */
    suspend fun thread(userId: Int, limit: Int = THREAD_PAGE, offset: Int = 0): ApiResult<MessageThreadDto> =
        get(paged(conversationUrl(userId), limit, offset), MessageThreadDto.serializer())

    suspend fun sendMessage(userId: Int, content: String): ApiResult<ThreadMessageDto> = send(
        request(conversationUrl(userId)).post(body(MessageRequestDto(content))),
        ThreadMessageDto.serializer()
    )

    /** Refused with `edit_window_expired` once the ten minutes since the message was sent have passed. */
    suspend fun editMessage(id: Int, content: String): ApiResult<ThreadMessageDto> = send(
        request(communityUrl("messages", id.toString())).patch(body(MessageRequestDto(content))),
        ThreadMessageDto.serializer()
    )

    /** Reading a thread does not mark it read, unlike the website's page; this is how the app says it has. */
    suspend fun markThreadRead(userId: Int): ApiResult<Unit> =
        noContent(request(conversationUrl(userId).newBuilder().addPathSegment("read").build()).post(EMPTY))

    suspend fun member(id: Int): ApiResult<MemberProfileDto> =
        get(communityUrl("members", id.toString()), MemberProfileDto.serializer())

    suspend fun follow(id: Int): ApiResult<Unit> =
        noContent(request(communityUrl("members", id.toString(), "follow")).post(EMPTY))

    suspend fun unfollow(id: Int): ApiResult<Unit> =
        noContent(request(communityUrl("members", id.toString(), "follow")).delete())

    suspend fun blocks(): ApiResult<MemberListDto> = get(communityUrl("blocks"), MemberListDto.serializer())

    suspend fun block(id: Int): ApiResult<Unit> = noContent(request(communityUrl("blocks", id.toString())).post(EMPTY))

    suspend fun unblock(id: Int): ApiResult<Unit> = noContent(request(communityUrl("blocks", id.toString())).delete())

    suspend fun groupMembers(slug: String, limit: Int = COMMUNITY_PAGE, offset: Int = 0): ApiResult<MemberListDto> =
        get(paged(communityUrl("groups", slug, "members"), limit, offset), MemberListDto.serializer())

    private fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    private fun eventUrl(id: Int, action: String): HttpUrl = base.newBuilder()
        .addPathSegments("api/v1/events")
        .addPathSegment(id.toString())
        .addPathSegment(action)
        .build()

    private fun invitationUrl(id: Int, action: String): HttpUrl = base.newBuilder()
        .addPathSegments("api/v1/memberships/invitations")
        .addPathSegment(id.toString())
        .addPathSegment(action)
        .build()

    private fun communityUrl(vararg segments: String): HttpUrl = base.newBuilder()
        .addPathSegments("api/v1/community")
        .apply { segments.forEach { addPathSegment(it) } }
        .build()

    private fun conversationUrl(userId: Int): HttpUrl = communityUrl("conversations", userId.toString())

    private fun paged(url: HttpUrl, limit: Int, offset: Int): HttpUrl = url.newBuilder()
        .addQueryParameter("limit", limit.toString())
        .apply { if (offset > 0) addQueryParameter("offset", offset.toString()) }
        .build()

    private fun membershipUrl(slug: String): HttpUrl = base.newBuilder()
        .addPathSegments("api/v1/memberships/groups")
        .addPathSegment(slug)
        .build()

    private fun request(url: HttpUrl) = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("Accept-Language", languageTag())

    private inline fun <reified T> body(value: T): RequestBody = json.encodeToString(value).toRequestBody(JSON_TYPE)

    private fun multipart(upload: Upload): RequestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", upload.fileName, upload.asRequestBody())
        .build()

    private suspend fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        send(request(url), deserializer)

    private suspend fun <T> send(builder: Request.Builder, deserializer: DeserializationStrategy<T>): ApiResult<T> =
        call(builder) { response -> response.toResult(deserializer) }

    /** For the answers that carry nothing but their status. */
    private suspend fun noContent(builder: Request.Builder): ApiResult<Unit> = call(builder) { response ->
        if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure(response.toError())
    }

    private suspend fun <T> call(builder: Request.Builder, handle: (Response) -> ApiResult<T>): ApiResult<T> =
        withContext(io) {
            try {
                http.newCall(builder.build()).await().use(handle)
            } catch (_: InterruptedIOException) {
                ApiResult.Failure(ApiError.Timeout)
            } catch (_: IOException) {
                ApiResult.Failure(ApiError.Offline)
            }
        }

    private fun <T> Response.toResult(deserializer: DeserializationStrategy<T>): ApiResult<T> {
        val text = body.string()
        if (!isSuccessful) return ApiResult.Failure(toError(text))
        return try {
            ApiResult.Success(json.decodeFromString(deserializer, text))
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiError.Malformed)
        } catch (_: IllegalArgumentException) {
            ApiResult.Failure(ApiError.Malformed)
        }
    }

    private fun Response.toError(text: String = body.string()): ApiError.Http =
        parseHttpError(code, header("Content-Type"), text, json).copy(retryAfter = retryAfterSeconds())

    /** `Retry-After` comes as seconds on the answers the app shows a waiting time for. */
    private fun Response.retryAfterSeconds(): Int? = header("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY = ByteArray(0).toRequestBody(null)
        const val COMMENT_PAGE = 25
    }
}

/** A picture on its way to the server, read from wherever the member picked it only while it is being sent. */
class Upload(val fileName: String, val contentType: String, val length: Long, val open: () -> InputStream)

private fun Upload.asRequestBody(): RequestBody = object : RequestBody() {
    override fun contentType() = contentType.toMediaType()

    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        open().use { sink.writeAll(it.source()) }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }
        }
    )
}
