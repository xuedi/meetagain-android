package org.meetagain.app.feature.townhall

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.meetagain.app.core.data.AnswerCache
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.Conversation
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.GroupFeature
import org.meetagain.app.core.data.ImageHost
import org.meetagain.app.core.data.Keys
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.isNotFound
import org.meetagain.app.core.data.toConversation
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.CommentListDto
import org.meetagain.app.core.network.GalleryListDto
import org.meetagain.app.core.network.TopicListDto

/**
 * A group's Town Hall: its forum and its gallery, read through the same offline-first cache as everything else.
 *
 * A 404 from any of it means the group no longer opens its Town Hall to this member - switched off, left, or blocked
 * by the group - or that the topic is gone. Either way the memberships are asked again, so the bar and the group
 * page stop offering what is no longer there.
 */
class TownHallRepository(
    private val api: ApiClient,
    baseUrl: String,
    private val cache: AnswerCache,
    private val members: MemberRepository
) {
    private val images = ImageHost(baseUrl.toHttpUrl().host)

    /** The member's groups whose Town Hall is open to them, from the stored memberships; null before those are read. */
    fun groups(): Flow<List<Group>?> = members.myGroups().map { cached ->
        cached?.value?.filter { GroupFeature.TownHall in it.features }?.map { it.group }
    }

    // The forum

    fun forum(slug: String): Flow<Cached<Forum>?> =
        cache.observe(Keys.topics(slug), TopicListDto.serializer()) { it.toForum() }

    suspend fun refreshForum(slug: String): ApiResult<Unit> =
        checked(cache.refresh(Keys.topics(slug), TopicListDto.serializer()) { api.topics(slug) })

    /** A top-level topic, or a subtopic of [parentId]. Answers with the new topic's id. */
    suspend fun startTopic(slug: String, title: String, parentId: Int? = null): ApiResult<Int> =
        when (val result = checked(api.createTopic(slug, title, parentId))) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)

            is ApiResult.Success -> {
                refreshForum(slug)
                ApiResult.Success(result.value.id)
            }
        }

    suspend fun renameTopic(slug: String, id: Int, title: String): ApiResult<Unit> =
        after(api.renameTopic(slug, id, title)) { refreshForum(slug) }

    suspend fun deleteTopic(slug: String, id: Int): ApiResult<Unit> =
        after(api.deleteTopic(slug, id)) { refreshForum(slug) }

    // A topic's replies

    fun replies(slug: String, id: Int): Flow<Cached<Conversation>?> =
        cache.observe(Keys.replies(slug, id), CommentListDto.serializer()) { it.toConversation(images) }

    suspend fun refreshReplies(slug: String, id: Int): ApiResult<Unit> =
        checked(cache.refresh(Keys.replies(slug, id), CommentListDto.serializer()) { api.replies(slug, id) })

    /** The page before the one the member has read; only the newest page is stored. */
    suspend fun olderReplies(slug: String, id: Int, before: Int): ApiResult<Conversation> =
        when (val result = checked(api.replies(slug, id, before = before))) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)
            is ApiResult.Success -> ApiResult.Success(result.value.toConversation(images))
        }

    /** The forum is fetched again too: it shows how many replies each topic has. */
    suspend fun addReply(slug: String, id: Int, text: String): ApiResult<Unit> = after(api.addReply(slug, id, text)) {
        refreshReplies(slug, id)
        refreshForum(slug)
    }

    suspend fun deleteReply(slug: String, id: Int, replyId: Int): ApiResult<Unit> =
        after(api.deleteReply(slug, id, replyId)) {
            refreshReplies(slug, id)
            refreshForum(slug)
        }

    // The gallery

    fun gallery(slug: String): Flow<Cached<Gallery>?> =
        cache.observe(Keys.gallery(slug), GalleryListDto.serializer()) { it.toGallery(images) }

    suspend fun refreshGallery(slug: String): ApiResult<Unit> =
        checked(cache.refresh(Keys.gallery(slug), GalleryListDto.serializer()) { api.gallery(slug) })

    /** The page from [offset] on; like older replies it is not stored. */
    suspend fun moreGallery(slug: String, offset: Int): ApiResult<Gallery> =
        when (val result = checked(api.gallery(slug, offset = offset))) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)
            is ApiResult.Success -> ApiResult.Success(result.value.toGallery(images))
        }

    private suspend fun <T> checked(result: ApiResult<T>): ApiResult<T> {
        if (result is ApiResult.Failure && result.error.isNotFound) members.refreshMyGroups()
        return result
    }

    private suspend fun <T> after(result: ApiResult<T>, refresh: suspend () -> Unit): ApiResult<Unit> =
        when (val checkedResult = checked(result)) {
            is ApiResult.Failure -> ApiResult.Failure(checkedResult.error)

            is ApiResult.Success -> {
                refresh()
                ApiResult.Success(Unit)
            }
        }
}
