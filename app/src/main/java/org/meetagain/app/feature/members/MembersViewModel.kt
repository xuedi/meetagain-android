package org.meetagain.app.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.MemberSummary
import org.meetagain.app.core.data.Members
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** A list of members as the member sees it: the stored first page, plus whatever they asked to see after it. */
data class MemberList(val people: List<MemberSummary>, val total: Int, val loadingMore: Boolean = false) {
    val hasMore: Boolean get() = people.size < total
}

/**
 * A group's member list and the blocked list are the same screen with a different source; the blocked list is
 * short and never pages, so [more] is null for it.
 */
class MembersViewModel(
    stored: Flow<Cached<Members>?>,
    refresh: suspend () -> ApiResult<Unit>,
    private val more: (suspend (offset: Int) -> ApiResult<Members>)? = null
) : ViewModel() {
    private val content = StoredContent(viewModelScope, stored, refresh)
    private val extra = MutableStateFlow<List<MemberSummary>>(emptyList())
    private val loadingMore = MutableStateFlow(false)

    val state: StateFlow<Loadable<MemberList>> =
        combine(content.state, extra, loadingMore) { loadable, extra, loading ->
            when (loadable) {
                Loadable.Loading -> Loadable.Loading

                is Loadable.Failed -> loadable

                is Loadable.Loaded -> Loadable.Loaded(
                    MemberList(loadable.value.people + extra, loadable.value.total, loading),
                    loadable.refreshing,
                    loadable.stale
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    fun load() {
        extra.value = emptyList()
        content.reload()
    }

    fun loadMore() {
        val fetch = more ?: return
        if (loadingMore.value) return
        val shown = (state.value as? Loadable.Loaded)?.value?.takeIf { it.hasMore } ?: return
        loadingMore.value = true
        viewModelScope.launch {
            val result = fetch(shown.people.size)
            if (result is ApiResult.Success) extra.update { it + result.value.people }
            loadingMore.value = false
        }
    }
}
