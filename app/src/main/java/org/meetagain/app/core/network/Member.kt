package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

// Signing in

@Serializable
data class LoginRequestDto(val email: String, val password: String, val deviceName: String)

/** `LoginResult` in the API description. The token is shown once and never again. */
@Serializable
data class LoginResultDto(val token: String, val scopes: List<String>)

// The member

/** `MeProfile` in the API description. */
@Serializable
data class MeDto(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val status: Int,
    val locale: String,
    val bio: String? = null,
    val public: Boolean,
    val avatarUrl: String? = null,
    val createdAt: String? = null
)

/** The fields of a profile the app may change; an absent one keeps its stored value. */
@Serializable
data class ProfileChangeDto(
    val name: String? = null,
    val bio: String? = null,
    val locale: String? = null,
    val public: Boolean? = null
)

// RSVP

@Serializable
data class RsvpRequestDto(val going: Boolean, val guests: Int? = null)

/** `RsvpResult` in the API description. */
@Serializable
data class RsvpResultDto(val rsvp: Boolean, val rsvpCount: Int, val guests: Int, val attendeeCount: Int)

// Who is coming

/** `AttendeeList` in the API description. [total] counts the people, their guests and [externalCount]. */
@Serializable
data class AttendeeListDto(val items: List<AttendeeDto>, val externalCount: Int, val total: Int)

/** `Attendee` in the API description. */
@Serializable
data class AttendeeDto(val id: Int, val name: String, val avatarUrl: String? = null, val guests: Int, val mine: Boolean)

// The conversation

/** `CommentList` in the API description. [nextBefore] is the cursor for the next, older page. */
@Serializable
data class CommentListDto(val items: List<EventCommentDto>, val total: Int, val nextBefore: Int? = null)

/** `EventComment` in the API description. */
@Serializable
data class EventCommentDto(
    val id: Int,
    val author: CommentAuthorDto,
    val createdAt: String? = null,
    val content: String,
    val mine: Boolean,
    val canDelete: Boolean
)

/** `CommentAuthor` in the API description. [id] is null once that account is gone. */
@Serializable
data class CommentAuthorDto(val id: Int? = null, val name: String, val avatarUrl: String? = null)

@Serializable
data class CommentRequestDto(val content: String)

/** `CommentCreated` in the API description. */
@Serializable
data class CommentCreatedDto(val id: Int, val content: String, val createdAt: String? = null)

// The photos

/** `ImageList` in the API description. */
@Serializable
data class ImageListDto(val items: List<EventImageDto>, val total: Int)

/** `EventImage` in the API description. [urls] holds every generated size, keyed by its dimensions. */
@Serializable
data class EventImageDto(val id: Int, val url: String, val urls: Map<String, String> = emptyMap(), val mine: Boolean)

/** `ImageUploaded` in the API description. */
@Serializable
data class ImageUploadedDto(val id: Int, val url: String)

// Memberships

/** `MembershipList` in the API description. */
@Serializable
data class MembershipListDto(val items: List<MembershipDto>, val total: Int)

/** `Membership` in the API description. [status] is `pending`, `approved` or `rejected`. */
@Serializable
data class MembershipDto(
    val group: GroupSummaryDto,
    val role: String? = null,
    val status: String,
    val blocked: Boolean,
    val joinedAt: String? = null
)

/** `InvitationList` in the API description. */
@Serializable
data class InvitationListDto(val items: List<InvitationDto>, val total: Int)

/** `Invitation` in the API description. */
@Serializable
data class InvitationDto(
    val id: Int,
    val group: GroupSummaryDto,
    val role: String? = null,
    val invitedBy: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null
)

/** Answers the platform's question about announcement mail when joining crosses into the platform. */
@Serializable
data class ConsentDto(val platformMailConsent: Boolean? = null)
