package org.meetagain.app.feature.townhall

import java.time.Instant
import org.meetagain.app.core.data.GRID_SIZE
import org.meetagain.app.core.data.ImageHost
import org.meetagain.app.core.data.instant
import org.meetagain.app.core.network.GalleryListDto
import org.meetagain.app.core.network.MAX_TOPIC_DEPTH
import org.meetagain.app.core.network.TopicDto
import org.meetagain.app.core.network.TopicListDto

/** One forum topic: a title and who started it; what is said about it are its replies. */
data class Topic(
    val id: Int,
    val parentId: Int?,
    /** 1 for a top-level topic. */
    val depth: Int,
    val title: String,
    val authorName: String,
    val startedAt: Instant?,
    val replies: Int,
    val mine: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean
) {
    val canHaveSubtopics: Boolean get() = depth < MAX_TOPIC_DEPTH
}

/** A group's whole forum, in the server's order: each topic comes before its subtopics. */
data class Forum(val topics: List<Topic>) {
    fun topic(id: Int): Topic? = topics.firstOrNull { it.id == id }

    fun subtopicsOf(id: Int): List<Topic> = topics.filter { it.parentId == id }
}

/** A photo of one of the group's meetings, and the meeting it was taken at. */
data class GalleryPhoto(
    val id: Int,
    val url: String,
    val thumbnailUrl: String,
    val uploadedAt: Instant?,
    val eventId: Int,
    val eventTitle: String
)

/** The gallery as far as it is loaded; [total] counts every photo the server has for it. */
data class Gallery(val photos: List<GalleryPhoto>, val total: Int) {
    val hasMore: Boolean get() = photos.size < total
}

fun TopicListDto.toForum() = Forum(items.map { it.toTopic() })

fun TopicDto.toTopic() = Topic(
    id = id,
    parentId = parentId,
    depth = depth,
    title = title,
    authorName = author.name,
    startedAt = createdAt?.let(::instant),
    replies = replyCount,
    mine = mine,
    canRename = canRename,
    canDelete = canDelete
)

/** Only photos on the app's own server are kept, as everywhere else. */
fun GalleryListDto.toGallery(images: ImageHost) = Gallery(
    photos = items.mapNotNull { image ->
        val full = images.own(image.url) ?: return@mapNotNull null
        val thumbnail = image.urls[GRID_SIZE]?.let(images::own) ?: full
        GalleryPhoto(image.id, full, thumbnail, image.uploadedAt?.let(::instant), image.event.id, image.event.title)
    },
    total = total
)
