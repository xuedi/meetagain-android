package org.meetagain.app.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.reloading

data class GroupPage(val details: GroupDetails, val upcoming: Upcoming)

class GroupViewModel(private val repository: PublicRepository, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<GroupPage>>(Loadable.Loading)
    val state: StateFlow<Loadable<GroupPage>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.reloading() }
        viewModelScope.launch {
            val events = async { repository.upcomingEvents(group = slug) }
            val details = repository.group(slug)
            _state.value = when (details) {
                is ApiResult.Failure -> Loadable.Failed(details.error)

                is ApiResult.Success -> when (val upcoming = events.await()) {
                    is ApiResult.Failure -> Loadable.Failed(upcoming.error)
                    is ApiResult.Success -> Loadable.Loaded(GroupPage(details.value, upcoming.value))
                }
            }
        }
    }
}
