package org.meetagain.app.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

class EventViewModel(repository: PublicRepository, id: Int) : ViewModel() {
    private val event = StoredContent(viewModelScope, repository.event(id)) { repository.refreshEvent(id) }
    val state: StateFlow<Loadable<EventDetails>> = event.state

    fun load() = event.reload()
}
