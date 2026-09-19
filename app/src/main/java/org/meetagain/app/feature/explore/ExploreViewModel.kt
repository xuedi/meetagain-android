package org.meetagain.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

data class ExploreUiState(
    val events: Loadable<Upcoming> = Loadable.Loading,
    val groups: Loadable<List<Group>> = Loadable.Loading
)

class ExploreViewModel(repository: PublicRepository) : ViewModel() {
    private val events = StoredContent(viewModelScope, repository.upcomingEvents()) {
        repository.refreshUpcomingEvents()
    }
    private val groups = StoredContent(viewModelScope, repository.groups()) { repository.refreshGroups() }

    val state: StateFlow<ExploreUiState> = combine(events.state, groups.state, ::ExploreUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExploreUiState())

    fun loadEvents() = events.reload()

    fun loadGroups() = groups.reload()
}
