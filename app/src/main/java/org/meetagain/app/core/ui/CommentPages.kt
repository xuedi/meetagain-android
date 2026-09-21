package org.meetagain.app.core.ui

import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.Comment
import org.meetagain.app.core.data.Conversation
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/** The page as the member sees it: the comments they have loaded, minus the ones on their way out. */
data class Comments(
    val visible: List<Comment>,
    val total: Int,
    val hasOlder: Boolean,
    val loadingOlder: Boolean = false
)

/** How long a delete waits, so the member can take it back before the server hears about it. */
val UNDO_WINDOW: Duration = Duration.ofSeconds(5)

/**
 * Comments read newest first, as an event's conversation and a Town Hall topic's replies are: the newest page is
 * stored, the older ones are fetched on request and held here, and a delete waits for its undo before it is sent,
 * because the server deletes for good.
 */
class CommentPages(
    private val scope: CoroutineScope,
    stored: Flow<Cached<Conversation>?>,
    refresh: suspend () -> ApiResult<Unit>,
    private val fetchOlder: suspend (before: Int) -> ApiResult<Conversation>,
    private val sendDelete: suspend (commentId: Int) -> ApiResult<Unit>,
    private val onProblem: (ApiError) -> Unit,
    private val undoWindow: Duration = UNDO_WINDOW
) {
    private val newest = StoredContent(scope, stored, refresh)
    private val older = MutableStateFlow<List<Comment>>(emptyList())
    private val cursor = MutableStateFlow<Int?>(null)
    private val loadingOlder = MutableStateFlow(false)
    private val leaving = MutableStateFlow<Set<Int>>(emptySet())
    private val undoJobs = mutableMapOf<Int, Job>()

    val state: StateFlow<Loadable<Comments>> =
        combine(newest.state, older, leaving, loadingOlder) { loadable, older, leaving, loadingOlder ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> {
                    val conversation = loadable.value
                    // Once older pages are held, the next one follows the last of them, not the newest page.
                    if (older.isEmpty()) cursor.value = conversation.olderBefore
                    val all = (conversation.comments + older).filterNot { it.id in leaving }
                    val hasOlder = if (older.isEmpty()) conversation.olderBefore != null else cursor.value != null
                    Loadable.Loaded(
                        Comments(all, conversation.total, hasOlder, loadingOlder),
                        loadable.refreshing,
                        loadable.stale
                    )
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    /** Starts over from the newest page, as after the member's own comment. */
    fun reload() {
        older.value = emptyList()
        newest.reload()
    }

    /** Drops the older pages without a new request; the stored page already holds what was just written. */
    fun forgetOlder() {
        older.value = emptyList()
    }

    fun loadOlder() {
        val before = cursor.value ?: return
        if (loadingOlder.value) return
        loadingOlder.value = true
        scope.launch {
            when (val result = fetchOlder(before)) {
                is ApiResult.Success -> {
                    older.update { it + result.value.comments }
                    cursor.value = result.value.olderBefore
                }

                is ApiResult.Failure -> onProblem(result.error)
            }
            loadingOlder.value = false
        }
    }

    /** The comment goes at once, the request only after the member's chance to take it back has passed. */
    fun delete(commentId: Int) {
        leaving.update { it + commentId }
        undoJobs[commentId] = scope.launch {
            delay(undoWindow.toMillis())
            undoJobs.remove(commentId)
            when (val result = sendDelete(commentId)) {
                is ApiResult.Success -> older.update { comments -> comments.filterNot { it.id == commentId } }

                is ApiResult.Failure -> {
                    leaving.update { it - commentId }
                    onProblem(result.error)
                }
            }
        }
    }

    fun undoDelete(commentId: Int) {
        undoJobs.remove(commentId)?.cancel()
        leaving.update { it - commentId }
    }
}
