package org.meetagain.app.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.reloading
import org.meetagain.app.core.ui.toLoadable

class EventViewModel(private val repository: PublicRepository, private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<EventDetails>>(Loadable.Loading)
    val state: StateFlow<Loadable<EventDetails>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.reloading() }
        viewModelScope.launch { _state.value = repository.event(id).toLoadable() }
    }
}
