package org.meetagain.app.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.MemberProfile
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult
import org.meetagain.app.core.network.CommunityError
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.StoredContent

/** What the member is told after something they did; each one is one sentence in the screen's language. */
enum class MemberProblem { Refused, Blocked, Offline, Failed }

/**
 * A member's page. Follow and block apply at once and go back where they were when the server refuses, the shape
 * the RSVP card already uses.
 */
class MemberViewModel(private val repository: MemberRepository, private val id: Int) : ViewModel() {
    private val stored = StoredContent(viewModelScope, repository.member(id)) { repository.refreshMember(id) }

    /** What the member just asked for, shown before the server has confirmed it. */
    private val pending = MutableStateFlow<MemberProfile?>(null)

    val state: StateFlow<Loadable<MemberProfile>> = combine(stored.state, pending) { loadable, pending ->
        if (loadable is Loadable.Loaded && pending != null) loadable.copy(value = pending) else loadable
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Loadable.Loading)

    private val currentBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = currentBusy

    private val currentProblem = MutableStateFlow<MemberProblem?>(null)
    val problem: StateFlow<MemberProblem?> = currentProblem

    fun load() = stored.reload()

    fun toggleFollow() {
        val member = shown() ?: return
        apply(member.copy(following = !member.following)) {
            if (member.following) repository.unfollow(id) else repository.follow(id)
        }
    }

    fun toggleBlock() {
        val member = shown() ?: return
        apply(member.copy(blockedByMe = !member.blockedByMe, canMessage = member.blockedByMe)) {
            if (member.blockedByMe) repository.unblock(id) else repository.block(id)
        }
    }

    fun dismissProblem() {
        currentProblem.value = null
    }

    private fun shown(): MemberProfile? = (state.value as? Loadable.Loaded)?.value

    private fun apply(optimistic: MemberProfile, call: suspend () -> ApiResult<Unit>) {
        if (currentBusy.value) return
        pending.value = optimistic
        currentBusy.value = true
        viewModelScope.launch {
            val result = call()
            if (result is ApiResult.Failure) currentProblem.value = memberProblemOf(result.error)
            // Either way the stored answer is now the truth: a refused change was never made, and a written one
            // has just been fetched again.
            pending.value = null
            currentBusy.value = false
        }
    }
}

internal fun memberProblemOf(error: ApiError): MemberProblem = when {
    error is ApiError.Offline || error is ApiError.Timeout -> MemberProblem.Offline
    error !is ApiError.Http -> MemberProblem.Failed
    error.code == CommunityError.FORBIDDEN -> MemberProblem.Refused
    error.code == CommunityError.BLOCKED -> MemberProblem.Blocked
    else -> MemberProblem.Failed
}
