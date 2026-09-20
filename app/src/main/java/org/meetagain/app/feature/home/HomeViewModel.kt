package org.meetagain.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Membership
import org.meetagain.app.core.data.MembershipStatus
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent
import org.meetagain.app.feature.rsvp.RsvpActions

/**
 * The member's next meetings: the very next one on its own, then the rest of the window, day by day.
 * [beyondWindow] is true when the member has meetings further out than the app shows here.
 */
data class Home(val next: Event?, val later: List<Event>, val beyondWindow: Boolean)

/** How far ahead home looks. Everyone gets the same window, so the list says the same thing to everyone. */
val HOME_WINDOW: Duration = Duration.ofDays(28)

class HomeViewModel(private val repository: MemberRepository, private val clock: Clock = Clock.systemUTC()) :
    ViewModel() {
    private val events = StoredContent(viewModelScope, repository.myEvents(), repository::refreshMyEvents)

    private val groups = StoredContent(viewModelScope, repository.myGroups(), repository::refreshMyGroups)

    val state: StateFlow<Loadable<Home>> = events.state
        .map { loadable -> loadable.map { it.events.toHome() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    /** The groups the member may answer in: approved and not blocked, as the server sees it. */
    val joined: StateFlow<Set<String>?> = groups.state
        .map { loadable -> (loadable as? Loadable.Loaded)?.value?.let(::joinedSlugs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rsvp = RsvpActions(viewModelScope, repository)

    fun load() {
        events.reload()
        groups.reload()
    }

    private fun List<Event>.toHome(): Home {
        val until = clock.instant() + HOME_WINDOW
        val inside = filter { it.start.isBefore(until) }
        return Home(
            next = inside.firstOrNull(),
            later = inside.drop(1),
            beyondWindow = inside.size < size
        )
    }

    private fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failed -> this
        is Loadable.Loaded -> Loadable.Loaded(transform(value), refreshing, stale)
    }
}

/** Also used by the screens that need to know whether an answer would be taken. */
fun joinedSlugs(memberships: List<Membership>): Set<String> = memberships
    .filter { it.status == MembershipStatus.Approved && !it.blocked }
    .map { it.group.slug }
    .toSet()
