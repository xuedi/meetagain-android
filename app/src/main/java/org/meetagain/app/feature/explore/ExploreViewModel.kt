package org.meetagain.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.reloading
import org.meetagain.app.core.ui.toLoadable

data class ExploreUiState(
    val events: Loadable<Upcoming> = Loadable.Loading,
    val groups: Loadable<List<Group>> = Loadable.Loading
)

class ExploreViewModel(private val repository: PublicRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        loadEvents()
        loadGroups()
    }

    fun loadEvents() {
        _state.update { it.copy(events = it.events.reloading()) }
        viewModelScope.launch {
            val events = repository.upcomingEvents().toLoadable()
            _state.update { it.copy(events = events) }
        }
    }

    fun loadGroups() {
        _state.update { it.copy(groups = it.groups.reloading()) }
        viewModelScope.launch {
            val groups = repository.groups().toLoadable()
            _state.update { it.copy(groups = groups) }
        }
    }
}
