package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

// The `community` section: direct messages, members, following and blocking. Every list answers the same envelope -
// `items`, `total`, `limit`, `offset` - with the server's clamp, and every call needs `community:read` or
// `community:write`.

/** The page the thread asks for, which is the server's maximum: an ordinary thread is then one call. */
const val THREAD_PAGE = 100

/** The server's own default page for the community lists. */
const val COMMUNITY_PAGE = 20

/** The longest message the server takes, on sending and on editing alike. */
const val MAX_MESSAGE_LENGTH = 5000

/** `ConversationList` in the API description: one entry per partner, newest first. */
@Serializable
data class ConversationListDto(val items: List<ConversationSummaryDto>, val total: Int, val limit: Int, val offset: Int)

/**
 * `ConversationSummary` in the API description. There is no message preview: an entry says who, how many and when,
 * which is what an inbox needs and all the server sends.
 */
@Serializable
data class ConversationSummaryDto(
    val partner: MemberSummaryDto,
    val messages: Int,
    val unread: Int,
    val lastMessageAt: String? = null
)

/** `MemberSummary` in the API description, the shape a member list and a conversation partner share. */
@Serializable
data class MemberSummaryDto(val id: Int, val name: String, val avatarUrl: String? = null)

/**
 * `MessageThread` in the API description: one page of a thread, oldest first. [blocked] is true when a block in
 * either direction stops the caller from sending, so the thread still reads while the composer cannot.
 */
@Serializable
data class MessageThreadDto(
    val partner: MemberSummaryDto,
    val items: List<ThreadMessageDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val blocked: Boolean = false
)

/**
 * `ThreadMessage` in the API description. [editable] is the server's own answer about the ten-minute window;
 * [systemNote] marks a support question carried into the thread rather than something the partner typed.
 */
@Serializable
data class ThreadMessageDto(
    val id: Int,
    val content: String,
    val createdAt: String? = null,
    val mine: Boolean,
    val wasRead: Boolean,
    val editable: Boolean,
    val editedAt: String? = null,
    val systemNote: Boolean
)

/** `MemberList` in the API description: a group's members, or the ones the caller has blocked. */
@Serializable
data class MemberListDto(val items: List<MemberSummaryDto>, val total: Int, val limit: Int, val offset: Int)

/** `MemberProfile` in the API description: what a member's page on the website shows, and nothing else. */
@Serializable
data class MemberProfileDto(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val public: Boolean,
    val memberSince: String? = null,
    val following: Boolean,
    val followsMe: Boolean,
    val blockedByMe: Boolean,
    val canMessage: Boolean
)

/** The body of a send and of an edit alike. */
@Serializable
data class MessageRequestDto(val content: String)

/** The codes the community endpoints refuse with, each one its own sentence on the screen that asked. */
object CommunityError {
    const val NOT_FOUND = "not_found"

    /** The other member has blocked the caller: their page says so and offers nothing. */
    const val FORBIDDEN = "forbidden"

    /** A send or a follow across a block, in either direction. */
    const val BLOCKED = "blocked"

    const val EDIT_WINDOW_EXPIRED = "edit_window_expired"
    const val CONTENT_REQUIRED = "content_required"
    const val CONTENT_TOO_LONG = "content_too_long"
    const val SELF_TARGET = "self_target"
}
