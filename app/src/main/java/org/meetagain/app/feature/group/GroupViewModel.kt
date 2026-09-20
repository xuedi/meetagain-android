package org.meetagain.app.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.Invitation
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Membership
import org.meetagain.app.core.data.PublicRepository
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

data class GroupPage(val details: GroupDetails, val upcoming: Upcoming)

/** Where the member stands with this group, and therefore what the page offers them. */
data class GroupStanding(val membership: Membership?, val invitation: Invitation?)

class GroupViewModel(
    repository: PublicRepository,
    private val memberRepository: MemberRepository,
    private val signedIn: Boolean,
    private val slug: String
) : ViewModel() {
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

    private val memberships = signedIn.takeIf { it }?.let {
        StoredContent(viewModelScope, memberRepository.myGroups()) { memberRepository.refreshMyGroups() }
    }

    private val invitations = signedIn.takeIf { it }?.let {
        StoredContent(viewModelScope, memberRepository.invitations()) { memberRepository.refreshInvitations() }
    }

    val state: StateFlow<Loadable<GroupPage>> = page.state

    val standing: StateFlow<GroupStanding> =
        if (memberships == null || invitations == null) {
            MutableStateFlow(GroupStanding(null, null))
        } else {
            combine(memberships.state, invitations.state) { groups, invites ->
                GroupStanding(
                    membership = (groups as? Loadable.Loaded)?.value?.firstOrNull { it.group.slug == slug },
                    invitation = (invites as? Loadable.Loaded)?.value?.firstOrNull { it.group.slug == slug }
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupStanding(null, null))
        }

    private val currentProblem = MutableStateFlow<MembershipProblem?>(null)
    val problem: StateFlow<MembershipProblem?> = currentProblem

    private val currentBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = currentBusy

    /** True once the server has asked for the platform's question to be answered before joining. */
    private val currentNeedsConsent = MutableStateFlow(false)
    val needsConsent: StateFlow<Boolean> = currentNeedsConsent

    val canAct: Boolean get() = signedIn

    fun load() {
        page.reload()
        memberships?.reload()
        invitations?.reload()
    }

    fun join(mailConsent: Boolean? = null) = act {
        when (val result = memberRepository.join(slug, mailConsent)) {
            is ApiResult.Success -> currentNeedsConsent.value = false

            is ApiResult.Failure -> {
                val problem = membershipProblemOf(result.error)
                if (problem == MembershipProblem.CrossingConsentNeeded) {
                    currentNeedsConsent.value = true
                } else {
                    currentProblem.value = problem
                }
            }
        }
    }

    fun leave() = act {
        val result = memberRepository.leave(slug)
        if (result is ApiResult.Failure) currentProblem.value = membershipProblemOf(result.error)
    }

    fun acceptInvitation(id: Int, mailConsent: Boolean? = null) = act {
        when (val result = memberRepository.acceptInvitation(id, slug, mailConsent)) {
            is ApiResult.Success -> currentNeedsConsent.value = false

            is ApiResult.Failure -> {
                val problem = membershipProblemOf(result.error)
                if (problem == MembershipProblem.CrossingConsentNeeded) {
                    currentNeedsConsent.value = true
                } else {
                    currentProblem.value = problem
                }
            }
        }
    }

    fun declineInvitation(id: Int) = act {
        val result = memberRepository.declineInvitation(id)
        if (result is ApiResult.Failure) currentProblem.value = membershipProblemOf(result.error)
    }

    fun dismissConsent() {
        currentNeedsConsent.value = false
    }

    fun dismissProblem() {
        currentProblem.value = null
    }

    private fun act(work: suspend () -> Unit) {
        currentBusy.value = true
        viewModelScope.launch {
            work()
            currentBusy.value = false
        }
    }
}
