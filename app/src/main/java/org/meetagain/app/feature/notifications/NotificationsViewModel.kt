package org.meetagain.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Notification
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/**
 * The bell. The server computes it fresh on every call and stores nothing, so there is nothing to mark as read and
 * no history to page through: the screen shows what is waiting now, and refreshing is the only thing it can do.
 */
class NotificationsViewModel(repository: MemberRepository) : ViewModel() {
    private val content =
        StoredContent(viewModelScope, repository.notifications()) { repository.refreshNotifications() }

    val state: StateFlow<Loadable<List<Notification>>> = content.state

    fun load() = content.reload()
}
