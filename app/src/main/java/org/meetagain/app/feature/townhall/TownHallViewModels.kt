package org.meetagain.app.feature.townhall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.CommunityError
import org.meetagain.app.core.network.MAX_MESSAGE_LENGTH
import org.meetagain.app.core.network.MAX_TOPIC_TITLE
import org.meetagain.app.core.network.TownHallError
import org.meetagain.app.core.ui.CommentPages
import org.meetagain.app.core.ui.Comments
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent
import org.meetagain.app.core.ui.UNDO_WINDOW

/** What the member is told after something they did in Town Hall; each one is one sentence. */
enum class TownHallProblem {
    EmptyTitle,
    TitleTooLong,
    TooDeep,
    EmptyReply,
    ReplyTooLong,
    NotAllowed,

    /** 404: the Town Hall is no longer open to the member, or the topic is gone. */
    Gone,
    Offline,
    Failed
}

/** Town Hall validates with 422 and its own codes, not the 400 of an event's comments. */
fun townHallProblemOf(error: ApiError): TownHallProblem = when {
    error is ApiError.Offline || error is ApiError.Timeout -> TownHallProblem.Offline
    error !is ApiError.Http -> TownHallProblem.Failed
    error.status == 404 -> TownHallProblem.Gone
    error.code == TownHallError.EMPTY_TITLE -> TownHallProblem.EmptyTitle
    error.code == TownHallError.TITLE_TOO_LONG -> TownHallProblem.TitleTooLong
    error.code == TownHallError.TOO_DEEP -> TownHallProblem.TooDeep
    error.code == CommunityError.CONTENT_REQUIRED -> TownHallProblem.EmptyReply
    error.code == CommunityError.CONTENT_TOO_LONG -> TownHallProblem.ReplyTooLong
    error.code == CommunityError.FORBIDDEN -> TownHallProblem.NotAllowed
    else -> TownHallProblem.Failed
}

/** The three things a topic title is asked for. */
enum class TitleKind { NewTopic, NewSubtopic, Rename }

/** The title dialog while it is open: what is typed, whether it is on its way, and why the server refused it. */
data class TitleDialog(
    val kind: TitleKind,
    val text: String = "",
    val busy: Boolean = false,
    val problem: TownHallProblem? = null
)

/** The one title dialog of a screen. It closes when the server takes the title and stays open with the reason when not. */
class TitleEditor(private val scope: CoroutineScope, private val submit: suspend (TitleKind, String) -> ApiResult<*>) {
    private val current = MutableStateFlow<TitleDialog?>(null)
    val dialog: StateFlow<TitleDialog?> = current

    fun open(kind: TitleKind, text: String = "") {
        current.value = TitleDialog(kind, text)
    }

    fun edit(text: String) = current.update { it?.copy(text = text.take(MAX_TOPIC_TITLE), problem = null) }

    fun dismiss() {
        current.value = null
    }

    fun confirm() {
        val dialog = current.value ?: return
        val title = dialog.text.trim()
        if (title.isEmpty()) {
            current.value = dialog.copy(problem = TownHallProblem.EmptyTitle)
            return
        }
        current.value = dialog.copy(busy = true, problem = null)
        scope.launch {
            when (val result = submit(dialog.kind, title)) {
                is ApiResult.Success -> current.value = null

                is ApiResult.Failure -> current.update {
                    it?.copy(busy = false, problem = townHallProblemOf(result.error))
                }
            }
        }
    }
}

/** A group's forum: the whole tree, and starting a topic in it. */
class ForumViewModel(private val repository: TownHallRepository, private val slug: String) : ViewModel() {
    private val content = StoredContent(viewModelScope, repository.forum(slug)) { repository.refreshForum(slug) }
    private val editor = TitleEditor(viewModelScope) { _, title -> repository.startTopic(slug, title) }

    val state: StateFlow<Loadable<Forum>> = content.state
    val dialog: StateFlow<TitleDialog?> = editor.dialog

    fun load() = content.reload()

    fun newTopic() = editor.open(TitleKind.NewTopic)

    fun editTitle(text: String) = editor.edit(text)

    fun confirmTitle() = editor.confirm()

    fun dismissTitle() = editor.dismiss()
}

/** The gallery as far as it is loaded: the stored first page, and the pages the member asked for after it. */
data class GalleryPage(val photos: List<GalleryPhoto>, val hasMore: Boolean, val loadingMore: Boolean = false)

class GalleryViewModel(private val repository: TownHallRepository, private val slug: String) : ViewModel() {
    private val first = StoredContent(viewModelScope, repository.gallery(slug)) { repository.refreshGallery(slug) }
    private val more = MutableStateFlow<Gallery?>(null)
    private val loadingMore = MutableStateFlow(false)

    private val currentProblem = MutableStateFlow<TownHallProblem?>(null)
    val problem: StateFlow<TownHallProblem?> = currentProblem

    val state: StateFlow<Loadable<GalleryPage>> =
        combine(first.state, more, loadingMore) { loadable, more, loadingMore ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> {
                    val photos = loadable.value.photos + more?.photos.orEmpty()
                    val total = more?.total ?: loadable.value.total
                    Loadable.Loaded(
                        GalleryPage(photos.distinctBy { it.id }, photos.size < total, loadingMore),
                        loadable.refreshing,
                        loadable.stale
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    fun load() {
        more.value = null
        first.reload()
    }

    fun loadMore() {
        val page = (state.value as? Loadable.Loaded)?.value ?: return
        if (!page.hasMore || loadingMore.value) return
        loadingMore.value = true
        viewModelScope.launch {
            when (val result = repository.moreGallery(slug, offset = page.photos.size)) {
                is ApiResult.Success -> more.update { held ->
                    Gallery(held?.photos.orEmpty() + result.value.photos, result.value.total)
                }

                is ApiResult.Failure -> currentProblem.value = townHallProblemOf(result.error)
            }
            loadingMore.value = false
        }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }
}

/** The topic a page is about, and the subtopics under it; both come from the stored forum. */
data class TopicHeader(val topic: Topic, val subtopics: List<Topic>)

class TopicViewModel(
    private val repository: TownHallRepository,
    private val slug: String,
    private val id: Int,
    undoWindow: Duration = UNDO_WINDOW
) : ViewModel() {
    private val currentProblem = MutableStateFlow<TownHallProblem?>(null)
    val problem: StateFlow<TownHallProblem?> = currentProblem

    private val forum = StoredContent(viewModelScope, repository.forum(slug)) { repository.refreshForum(slug) }

    private val pages = CommentPages(
        viewModelScope,
        repository.replies(slug, id),
        refresh = { repository.refreshReplies(slug, id) },
        fetchOlder = { before -> repository.olderReplies(slug, id, before) },
        sendDelete = { replyId -> repository.deleteReply(slug, id, replyId) },
        onProblem = { currentProblem.value = townHallProblemOf(it) },
        undoWindow = undoWindow
    )

    private val editor = TitleEditor(viewModelScope) { kind, title ->
        if (kind == TitleKind.NewSubtopic) {
            repository.startTopic(slug, title, parentId = id)
        } else {
            repository.renameTopic(slug, id, title)
        }
    }

    val header: StateFlow<TopicHeader?> = forum.state.map { loadable ->
        (loadable as? Loadable.Loaded)?.value?.let { forum ->
            forum.topic(id)?.let { TopicHeader(it, forum.subtopicsOf(id)) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val replies: StateFlow<Loadable<Comments>> = pages.state
    val dialog: StateFlow<TitleDialog?> = editor.dialog

    private val currentDraft = MutableStateFlow("")
    val draft: StateFlow<String> = currentDraft

    private val currentBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = currentBusy

    /** True once the topic is deleted, so the screen can leave it. */
    private val currentDeleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = currentDeleted

    fun load() {
        forum.reload()
        pages.reload()
    }

    fun draft(text: String) {
        currentDraft.value = text.take(MAX_MESSAGE_LENGTH)
        currentProblem.value = null
    }

    fun send() {
        val text = currentDraft.value.trim()
        if (text.isEmpty()) {
            currentProblem.value = TownHallProblem.EmptyReply
            return
        }
        currentBusy.value = true
        viewModelScope.launch {
            when (val result = repository.addReply(slug, id, text)) {
                is ApiResult.Success -> {
                    currentDraft.value = ""
                    pages.forgetOlder()
                }

                is ApiResult.Failure -> currentProblem.value = townHallProblemOf(result.error)
            }
            currentBusy.value = false
        }
    }

    fun loadOlder() = pages.loadOlder()

    fun deleteReply(replyId: Int) = pages.delete(replyId)

    fun undoDeleteReply(replyId: Int) = pages.undoDelete(replyId)

    fun newSubtopic() = editor.open(TitleKind.NewSubtopic)

    fun rename() = editor.open(TitleKind.Rename, header.value?.topic?.title.orEmpty())

    fun editTitle(text: String) = editor.edit(text)

    fun confirmTitle() = editor.confirm()

    fun dismissTitle() = editor.dismiss()

    /** Asked for behind a confirmation, because the server deletes the topic for good and there is nothing to undo. */
    fun deleteTopic() {
        currentBusy.value = true
        viewModelScope.launch {
            when (val result = repository.deleteTopic(slug, id)) {
                is ApiResult.Success -> currentDeleted.value = true
                is ApiResult.Failure -> currentProblem.value = townHallProblemOf(result.error)
            }
            currentBusy.value = false
        }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }
}
