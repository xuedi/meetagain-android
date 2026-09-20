package org.meetagain.app.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent
import org.meetagain.app.feature.home.joinedSlugs
import org.meetagain.app.feature.rsvp.RsvpActions

/**
 * One meeting. The page itself is public; what a signed-in member can do with it - answer, see the other dates of
 * the series - comes from the member API and is only asked for when someone is signed in.
 */
class EventViewModel(
    publicRepository: PublicRepository,
    private val memberRepository: MemberRepository,
    private val signedIn: Boolean,
    id: Int
) : ViewModel() {
    private val event = StoredContent(viewModelScope, publicRepository.event(id)) { publicRepository.refreshEvent(id) }

    val state: StateFlow<Loadable<EventDetails>> = event.state

    private val series = signedIn.takeIf { it }?.let {
        StoredContent(viewModelScope, memberRepository.occurrences(id)) { memberRepository.refreshOccurrences(id) }
    }

    /** The other dates of the same meeting, without this one; empty while nobody is signed in. */
    val occurrences: StateFlow<List<Event>> = series?.state
        ?.map { loadable -> (loadable as? Loadable.Loaded)?.value?.events?.filterNot { it.id == id }.orEmpty() }
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        ?: MutableStateFlow(emptyList())

    private val groups = signedIn.takeIf { it }?.let {
        StoredContent(viewModelScope, memberRepository.myGroups()) { memberRepository.refreshMyGroups() }
    }

    val joined: StateFlow<Set<String>?> = groups?.state
        ?.map { loadable -> (loadable as? Loadable.Loaded)?.value?.let(::joinedSlugs) }
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        ?: MutableStateFlow(null)

    val rsvp = RsvpActions(viewModelScope, memberRepository)

    val canAnswer: Boolean get() = signedIn

    fun load() {
        event.reload()
        series?.reload()
        groups?.reload()
    }
}
