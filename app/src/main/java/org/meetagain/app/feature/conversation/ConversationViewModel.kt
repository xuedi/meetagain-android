package org.meetagain.app.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Comment
import org.meetagain.app.core.data.Conversation
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Photo
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.Upload
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** What the member is told after something they did; each one is one sentence in the screen's language. */
enum class ConversationProblem { NotAMember, TooLong, Empty, PhotoRejected, Offline, Failed }

/** The page as the member sees it: the comments they have loaded, minus the ones on their way out. */
data class Comments(
    val visible: List<Comment>,
    val total: Int,
    val hasOlder: Boolean,
    val loadingOlder: Boolean = false
)

/** How long a delete waits, so the member can take it back before the server hears about it. */
val UNDO_WINDOW: Duration = Duration.ofSeconds(5)

private const val MAX_COMMENT_LENGTH = 5000

class ConversationViewModel(
    private val repository: MemberRepository,
    private val id: Int,
    private val undoWindow: Duration = UNDO_WINDOW
) : ViewModel() {
    private val newest = StoredContent(viewModelScope, repository.conversation(id)) {
        repository.refreshConversation(id)
    }

    private val photoContent = StoredContent(viewModelScope, repository.photos(id)) { repository.refreshPhotos(id) }

    private val older = MutableStateFlow<List<Comment>>(emptyList())
    private val cursor = MutableStateFlow<Int?>(null)
    private val loadingOlder = MutableStateFlow(false)
    private val leaving = MutableStateFlow<Set<Int>>(emptySet())
    private val leavingPhotos = MutableStateFlow<Set<Int>>(emptySet())
    private val undoJobs = mutableMapOf<Int, Job>()
    private val photoUndoJobs = mutableMapOf<Int, Job>()

    val state: StateFlow<Loadable<Comments>> =
        combine(newest.state, older, leaving, loadingOlder) { loadable, older, leaving, loadingOlder ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> {
                    val conversation: Conversation = loadable.value
                    cursor.value = conversation.olderBefore
                    val all = (conversation.comments + older).filterNot { it.id in leaving }
                    Loadable.Loaded(
                        Comments(all, conversation.total, conversation.olderBefore != null, loadingOlder),
                        loadable.refreshing,
                        loadable.stale
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    val photos: StateFlow<List<Photo>> = combine(photoContent.state, leavingPhotos) { loadable, leaving ->
        (loadable as? Loadable.Loaded)?.value?.filterNot { it.id in leaving }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val currentDraft = MutableStateFlow("")
    val draft: StateFlow<String> = currentDraft

    private val currentBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = currentBusy

    private val currentProblem = MutableStateFlow<ConversationProblem?>(null)
    val problem: StateFlow<ConversationProblem?> = currentProblem

    fun load() {
        older.value = emptyList()
        newest.reload()
        photoContent.reload()
    }

    fun draft(text: String) {
        currentDraft.value = text.take(MAX_COMMENT_LENGTH)
        currentProblem.value = null
    }

    fun send() {
        val text = currentDraft.value.trim()
        if (text.isEmpty()) {
            currentProblem.value = ConversationProblem.Empty
            return
        }
        currentBusy.value = true
        viewModelScope.launch {
            when (val result = repository.addComment(id, text)) {
                is ApiResult.Success -> {
                    currentDraft.value = ""
                    older.value = emptyList()
                }

                is ApiResult.Failure -> currentProblem.value = problemOf(result.error)
            }
            currentBusy.value = false
        }
    }

    fun loadOlder() {
        val before = cursor.value ?: return
        if (loadingOlder.value) return
        loadingOlder.value = true
        viewModelScope.launch {
            when (val result = repository.olderComments(id, before)) {
                is ApiResult.Success -> {
                    older.update { it + result.value.comments }
                    cursor.value = result.value.olderBefore
                }

                is ApiResult.Failure -> currentProblem.value = problemOf(result.error)
            }
            loadingOlder.value = false
        }
    }

    /** The comment goes at once, the request only after the member's chance to take it back has passed. */
    fun deleteComment(commentId: Int) {
        leaving.update { it + commentId }
        undoJobs[commentId] = viewModelScope.launch {
            delay(undoWindow.toMillis())
            undoJobs.remove(commentId)
            when (val result = repository.deleteComment(id, commentId)) {
                is ApiResult.Success -> older.update { comments -> comments.filterNot { it.id == commentId } }

                is ApiResult.Failure -> {
                    leaving.update { it - commentId }
                    currentProblem.value = problemOf(result.error)
                }
            }
        }
    }

    fun undoDeleteComment(commentId: Int) {
        undoJobs.remove(commentId)?.cancel()
        leaving.update { it - commentId }
    }

    fun deletePhoto(photoId: Int) {
        leavingPhotos.update { it + photoId }
        photoUndoJobs[photoId] = viewModelScope.launch {
            delay(undoWindow.toMillis())
            photoUndoJobs.remove(photoId)
            if (repository.deletePhoto(id, photoId) is ApiResult.Failure) {
                leavingPhotos.update { it - photoId }
                currentProblem.value = ConversationProblem.Failed
            }
        }
    }

    fun undoDeletePhoto(photoId: Int) {
        photoUndoJobs.remove(photoId)?.cancel()
        leavingPhotos.update { it - photoId }
    }

    fun addPhoto(upload: Upload) {
        currentBusy.value = true
        viewModelScope.launch {
            if (repository.addPhoto(id, upload) is ApiResult.Failure) {
                currentProblem.value = ConversationProblem.PhotoRejected
            }
            currentBusy.value = false
        }
    }

    fun photoTooBig() {
        currentProblem.value = ConversationProblem.PhotoRejected
    }

    fun dismissProblem() {
        currentProblem.value = null
    }

    private fun problemOf(error: ApiError): ConversationProblem = when {
        error is ApiError.Offline || error is ApiError.Timeout -> ConversationProblem.Offline
        error !is ApiError.Http -> ConversationProblem.Failed
        error.code == "forbidden" -> ConversationProblem.NotAMember
        error.code == "content_too_long" -> ConversationProblem.TooLong
        error.code == "content_required" -> ConversationProblem.Empty
        error.code == "file_rejected" || error.code == "upload_failed" -> ConversationProblem.PhotoRejected
        else -> ConversationProblem.Failed
    }
}
