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
import org.meetagain.app.core.data.InboxEntry
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** The inbox as the member sees it: the first page from the cache, plus whatever they asked to see after it. */
data class Conversations(val entries: List<InboxEntry>, val total: Int, val loadingMore: Boolean = false) {
    val hasMore: Boolean get() = entries.size < total
}

/** What the member is told after something they did; each one is one sentence in the screen's language. */
enum class InboxProblem { Offline, Failed }

/**
 * The first page is stored like every other list; the pages after it are held here and dropped on a reload, the
 * same shape the event conversation uses for its older pages.
 */
class MessagesViewModel(private val repository: MemberRepository) : ViewModel() {
    private val stored = StoredContent(viewModelScope, repository.inbox()) { repository.refreshInbox() }
    private val more = MutableStateFlow<List<InboxEntry>>(emptyList())
    private val loadingMore = MutableStateFlow(false)

    val state: StateFlow<Loadable<Conversations>> =
        combine(stored.state, more, loadingMore) { loadable, more, loading ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> Loadable.Loaded(
                    Conversations(loadable.value.entries + more, loadable.value.total, loading),
                    loadable.refreshing,
                    loadable.stale
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    private val currentProblem = MutableStateFlow<InboxProblem?>(null)
    val problem: StateFlow<InboxProblem?> = currentProblem

    fun load() {
        more.value = emptyList()
        stored.reload()
    }

    fun loadMore() {
        if (loadingMore.value) return
        val shown = (state.value as? Loadable.Loaded)?.value?.takeIf { it.hasMore } ?: return
        loadingMore.value = true
        viewModelScope.launch {
            when (val result = repository.moreConversations(shown.entries.size)) {
                is ApiResult.Success -> more.update { it + result.value.entries }
                is ApiResult.Failure -> currentProblem.value = problemOf(result.error)
            }
            loadingMore.value = false
        }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }
}

internal fun problemOf(error: ApiError): InboxProblem =
    if (error is ApiError.Offline || error is ApiError.Timeout) InboxProblem.Offline else InboxProblem.Failed
