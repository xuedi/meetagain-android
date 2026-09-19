package org.meetagain.app.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

data class GroupPage(val details: GroupDetails, val upcoming: Upcoming)

class GroupViewModel(repository: PublicRepository, slug: String) : ViewModel() {
    /** The page is as old as the older of its two stored answers. */
    private val stored = combine(repository.group(slug), repository.upcomingEvents(group = slug)) { group, events ->
        if (group == null || events == null) {
            null
        } else {
            Cached(GroupPage(group.value, events.value), minOf(group.syncedAt, events.syncedAt))
        }
    }

    /** The group's own answer decides first, so a group that is gone is "not found" whatever its events did. */
    private val page = StoredContent(viewModelScope, stored) {
        coroutineScope {
            val events = async { repository.refreshUpcomingEvents(group = slug) }
            val group = repository.refreshGroup(slug)
            group as? ApiResult.Failure ?: events.await()
        }
    }

    val state: StateFlow<Loadable<GroupPage>> = page.state

    fun load() = page.reload()
}
