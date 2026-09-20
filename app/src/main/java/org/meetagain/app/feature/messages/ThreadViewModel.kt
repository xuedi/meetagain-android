package org.meetagain.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.MemberSummary
import org.meetagain.app.core.data.Message
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.CommunityError
import org.meetagain.app.core.network.MAX_MESSAGE_LENGTH
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** What the member is told after something they did; each one is one sentence in the screen's language. */
enum class ThreadProblem { Blocked, EditWindowExpired, TooLong, Empty, NotFound, Offline, Failed }

/**
 * The thread as the member sees it: the newest page from the cache with whatever earlier pages they asked for in
 * front of it, oldest first.
 */
data class Thread(
    val partner: MemberSummary,
    val messages: List<Message>,
    val total: Int,
    /** Where the oldest message on screen sits in the whole thread; zero means the beginning is shown. */
    val earliestOffset: Int,
    /** A block in either direction: the thread reads, the composer does not. */
    val blocked: Boolean,
    val loadingEarlier: Boolean = false
) {
    val hasEarlier: Boolean get() = earliestOffset > 0
}

/** What the member is writing: a new message, or a change to one of their own inside the ten-minute window. */
data class Draft(val text: String = "", val editing: Int? = null)

class ThreadViewModel(private val repository: MemberRepository, private val partnerId: Int) : ViewModel() {
    private val newest = StoredContent(viewModelScope, repository.thread(partnerId)) {
        repository.refreshThread(partnerId)
    }

    private val earlier = MutableStateFlow<List<Message>>(emptyList())
    private val earliestOffset = MutableStateFlow<Int?>(null)
    private val loadingEarlier = MutableStateFlow(false)

    /** The read call goes once per open, and only after the newest page has actually been shown. */
    private var markedRead = false

    val state: StateFlow<Loadable<Thread>> =
        combine(newest.state, earlier, loadingEarlier) { loadable, earlier, loading ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> {
                    val thread = loadable.value
                    if (earliestOffset.value == null) earliestOffset.value = thread.offset
                    markRead(thread.messages)
                    Loadable.Loaded(
                        Thread(
                            partner = thread.partner,
                            messages = earlier + thread.messages,
                            total = thread.total,
                            earliestOffset = earliestOffset.value ?: thread.offset,
                            blocked = thread.blocked,
                            loadingEarlier = loading
                        ),
                        loadable.refreshing,
                        loadable.stale
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    private val currentDraft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = currentDraft

    private val currentBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = currentBusy

    private val currentProblem = MutableStateFlow<ThreadProblem?>(null)
    val problem: StateFlow<ThreadProblem?> = currentProblem

    fun load() {
        earlier.value = emptyList()
        earliestOffset.value = null
        newest.reload()
    }

    fun draft(text: String) {
        currentDraft.update { it.copy(text = text.take(MAX_MESSAGE_LENGTH)) }
        currentProblem.value = null
    }

    /** Only an own message inside the window can be changed, and the window is checked here as well. */
    fun edit(message: Message) {
        if (!message.editable) {
            currentProblem.value = ThreadProblem.EditWindowExpired
            return
        }
        currentDraft.value = Draft(message.text, editing = message.id)
    }

    fun cancelEdit() {
        currentDraft.value = Draft()
    }

    fun send() {
        val draft = currentDraft.value
        val text = draft.text.trim()
        if (text.isEmpty()) {
            currentProblem.value = ThreadProblem.Empty
            return
        }
        currentBusy.value = true
        viewModelScope.launch {
            val result = draft.editing
                ?.let { repository.editMessage(partnerId, it, text) }
                ?: repository.sendMessage(partnerId, text)
            when (result) {
                is ApiResult.Success -> {
                    currentDraft.value = Draft()
                    earlier.value = emptyList()
                    earliestOffset.value = null
                }

                is ApiResult.Failure -> {
                    val problem = threadProblemOf(result.error)
                    currentProblem.value = problem
                    // The window closed while they were typing: the thread is fetched again so the control goes.
                    if (problem == ThreadProblem.EditWindowExpired) {
                        currentDraft.value = Draft()
                        newest.reload()
                    }
                }
            }
            currentBusy.value = false
        }
    }

    fun loadEarlier() {
        val before = earliestOffset.value ?: return
        if (before <= 0 || loadingEarlier.value) return
        loadingEarlier.value = true
        viewModelScope.launch {
            when (val result = repository.earlierMessages(partnerId, before)) {
                is ApiResult.Success -> {
                    earlier.update { result.value.messages + it }
                    earliestOffset.value = result.value.offset
                }

                is ApiResult.Failure -> currentProblem.value = threadProblemOf(result.error)
            }
            loadingEarlier.value = false
        }
    }

    fun block() {
        act { repository.block(partnerId) }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }

    private fun act(call: suspend () -> ApiResult<Unit>) {
        currentBusy.value = true
        viewModelScope.launch {
            val result = call()
            if (result is ApiResult.Failure) currentProblem.value = threadProblemOf(result.error)
            currentBusy.value = false
        }
    }

    /** Once per open, and only when the newest page on screen actually holds something unread. */
    private fun markRead(shown: List<Message>) {
        if (markedRead || shown.none { !it.mine && !it.read }) return
        markedRead = true
        viewModelScope.launch { repository.markThreadRead(partnerId) }
    }
}

internal fun threadProblemOf(error: ApiError): ThreadProblem = when {
    error is ApiError.Offline || error is ApiError.Timeout -> ThreadProblem.Offline
    error !is ApiError.Http -> ThreadProblem.Failed
    error.code == CommunityError.BLOCKED -> ThreadProblem.Blocked
    error.code == CommunityError.EDIT_WINDOW_EXPIRED -> ThreadProblem.EditWindowExpired
    error.code == CommunityError.CONTENT_TOO_LONG -> ThreadProblem.TooLong
    error.code == CommunityError.CONTENT_REQUIRED -> ThreadProblem.Empty
    error.code == CommunityError.NOT_FOUND -> ThreadProblem.NotFound
    else -> ThreadProblem.Failed
}
