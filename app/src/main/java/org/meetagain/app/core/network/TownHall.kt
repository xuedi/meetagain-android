package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

// Town Hall, in the `community` section under `groups/{slug}/town-hall`. Every call answers 404 `not_found` when
// the group does not open its Town Hall to the caller - switched off, not a member, or blocked by the group - the
// same answer as a group that does not exist. Replies are the event comments' shapes; validation answers 422.

/** The deepest a topic sits: one at this depth takes no subtopics. */
const val MAX_TOPIC_DEPTH = 3

/** The longest topic title the server takes. */
const val MAX_TOPIC_TITLE = 120

/** A page of the gallery: three rows of a three-column grid, several times over. */
const val GALLERY_PAGE = 30

/** `TopicList` in the API description: the whole tree, depth first, each topic before its subtopics. Not paged. */
@Serializable
data class TopicListDto(val items: List<TopicDto>, val total: Int)

/** `Topic` in the API description. [depth] is 1 for a top-level topic. */
@Serializable
data class TopicDto(
    val id: Int,
    val parentId: Int? = null,
    val depth: Int,
    val title: String,
    val author: CommentAuthorDto,
    val createdAt: String? = null,
    val replyCount: Int,
    val mine: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean
)

/** Starting a topic, or a subtopic of [parentId]. */
@Serializable
data class TopicRequestDto(val title: String, val parentId: Int? = null)

@Serializable
data class TopicRenameDto(val title: String)

/** `GalleryList` in the API description: the photos of the group's events, newest first, reported ones left out. */
@Serializable
data class GalleryListDto(val items: List<GalleryImageDto>, val total: Int, val limit: Int, val offset: Int)

/** `GalleryImage` in the API description: an event's photo, and the event it was uploaded to. */
@Serializable
data class GalleryImageDto(
    val id: Int,
    val url: String,
    val urls: Map<String, String> = emptyMap(),
    val mine: Boolean,
    val uploadedAt: String? = null,
    val event: GalleryEventDto
)

@Serializable
data class GalleryEventDto(val id: Int, val title: String)

/** The codes a topic is refused with; a reply is refused with [CommunityError]'s content codes. */
object TownHallError {
    const val EMPTY_TITLE = "empty_title"
    const val TITLE_TOO_LONG = "title_too_long"
    const val TOO_DEEP = "too_deep"
}
