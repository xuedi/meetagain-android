package org.meetagain.app.feature.group

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.meetagain.app.R
import org.meetagain.app.core.network.ApiError

/** Why the server did not let a membership change happen. Each one is a sentence the member can act on. */
enum class MembershipProblem {
    /** Only listed Public groups can be joined from the app; the others are joined on their own site. */
    NotJoinable,

    BlockedInGroup,

    LastOwner,

    MayNotLeavePlatform,

    /** The group turned this member down before; the website is where that is taken up again. */
    Rejected,

    InvitationGone,

    /** The group is on its own domain, so joining crosses into the platform and needs an answer first. */
    CrossingConsentNeeded,

    NotFound,

    Offline,

    Failed
}

fun membershipProblemOf(error: ApiError): MembershipProblem = when {
    error is ApiError.Offline || error is ApiError.Timeout -> MembershipProblem.Offline

    error !is ApiError.Http -> MembershipProblem.Failed

    else -> when (error.code) {
        "group_not_joinable" -> MembershipProblem.NotJoinable
        "blocked_in_group" -> MembershipProblem.BlockedInGroup
        "last_owner" -> MembershipProblem.LastOwner
        "may_not_leave_platform" -> MembershipProblem.MayNotLeavePlatform
        "membership_rejected" -> MembershipProblem.Rejected
        "invitation_not_found" -> MembershipProblem.InvitationGone
        "platform_crossing_required" -> MembershipProblem.CrossingConsentNeeded
        "not_found" -> MembershipProblem.NotFound
        else -> MembershipProblem.Failed
    }
}

@Composable
fun membershipProblemText(problem: MembershipProblem): String = stringResource(
    when (problem) {
        MembershipProblem.NotJoinable -> R.string.membership_not_joinable
        MembershipProblem.BlockedInGroup -> R.string.membership_blocked_refusal
        MembershipProblem.LastOwner -> R.string.membership_last_owner
        MembershipProblem.MayNotLeavePlatform -> R.string.membership_may_not_leave_platform
        MembershipProblem.Rejected -> R.string.membership_rejected_refusal
        MembershipProblem.InvitationGone -> R.string.membership_invitation_gone
        MembershipProblem.CrossingConsentNeeded -> R.string.membership_crossing_needed
        MembershipProblem.NotFound -> R.string.error_not_found
        MembershipProblem.Offline -> R.string.error_offline
        MembershipProblem.Failed -> R.string.membership_failed
    }
)
