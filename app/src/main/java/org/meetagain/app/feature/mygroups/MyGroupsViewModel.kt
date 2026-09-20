package org.meetagain.app.feature.mygroups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.Invitation
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Membership
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent
import org.meetagain.app.feature.group.MembershipProblem
import org.meetagain.app.feature.group.membershipProblemOf

/** Every group the member belongs to or asked to belong to, and the invitations waiting for an answer. */
data class MyGroups(val memberships: List<Membership>, val invitations: List<Invitation>)

class MyGroupsViewModel(private val repository: MemberRepository) : ViewModel() {
    private val stored = combine(repository.myGroups(), repository.invitations()) { groups, invitations ->
        when {
            groups == null -> null

            // Invitations are a separate read; before it lands the page is still worth showing.
            else -> Cached(
                MyGroups(groups.value, invitations?.value.orEmpty()),
                minOf(groups.syncedAt, invitations?.syncedAt ?: groups.syncedAt)
            )
        }
    }

    private val content = StoredContent(viewModelScope, stored) {
        val groups = repository.refreshMyGroups()
        groups as? ApiResult.Failure ?: repository.refreshInvitations()
    }

    val state: StateFlow<Loadable<MyGroups>> = content.state

    private val currentProblem = MutableStateFlow<MembershipProblem?>(null)
    val problem: StateFlow<MembershipProblem?> = currentProblem

    fun load() = content.reload()

    fun accept(invitation: Invitation) {
        viewModelScope.launch {
            val result = repository.acceptInvitation(invitation.id, invitation.group.slug)
            if (result is ApiResult.Failure) currentProblem.value = membershipProblemOf(result.error)
        }
    }

    fun decline(invitation: Invitation) {
        viewModelScope.launch {
            val result = repository.declineInvitation(invitation.id)
            if (result is ApiResult.Failure) currentProblem.value = membershipProblemOf(result.error)
        }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }
}
