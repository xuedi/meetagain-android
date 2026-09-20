package org.meetagain.app.feature.mygroups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Invitation
import org.meetagain.app.core.data.Membership
import org.meetagain.app.core.data.MembershipStatus
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.GroupLogo
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.feature.group.membershipProblemText

@Composable
fun MyGroupsRoute(container: AppContainer, onBack: () -> Unit, onOpenGroup: (String) -> Unit) {
    val viewModel = viewModel { MyGroupsViewModel(container.memberRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val text = problem?.let { membershipProblemText(it) }
    LaunchedEffect(problem) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissProblem()
        }
    }
    MyGroupsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenGroup = onOpenGroup,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGroupsScreen(
    state: Loadable<MyGroups>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onAccept: (Invitation) -> Unit,
    onDecline: (Invitation) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.me_my_groups)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state) {
            Loadable.Loading -> LoadingState(modifier)

            is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

            is Loadable.Loaded -> LazyColumn(modifier) {
                state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                if (state.value.invitations.isNotEmpty()) {
                    item(key = "invitations") { Heading(stringResource(R.string.mygroups_invitations)) }
                    items(state.value.invitations, key = { "invitation-${it.id}" }) { invitation ->
                        InvitationRow(invitation, onAccept = { onAccept(invitation) }, onDecline = {
                            onDecline(invitation)
                        })
                    }
                }
                item(key = "memberships") { Heading(stringResource(R.string.mygroups_memberships)) }
                items(state.value.memberships, key = { "membership-${it.group.slug}" }) { membership ->
                    MembershipRow(membership) { onOpenGroup(membership.group.slug) }
                }
                item(key = "end") {
                    ListEnd(
                        if (state.value.memberships.isEmpty()) {
                            stringResource(R.string.mygroups_empty)
                        } else {
                            stringResource(R.string.mygroups_end)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun MembershipRow(membership: Membership, onOpen: () -> Unit) {
    ListItem(
        leadingContent = { GroupLogo(membership.group) },
        headlineContent = { Text(membership.group.name) },
        supportingContent = { Text(membershipState(membership)) },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}

@Composable
private fun membershipState(membership: Membership): String = when {
    membership.blocked -> stringResource(R.string.membership_blocked)
    membership.status == MembershipStatus.Pending -> stringResource(R.string.membership_pending)
    membership.status == MembershipStatus.Rejected -> stringResource(R.string.membership_rejected)
    membership.role == OWNER -> stringResource(R.string.membership_owner)
    membership.role == ORGANIZER -> stringResource(R.string.membership_organizer)
    else -> stringResource(R.string.membership_member)
}

@Composable
private fun InvitationRow(invitation: Invitation, onAccept: () -> Unit, onDecline: () -> Unit) {
    ListItem(
        leadingContent = { GroupLogo(invitation.group) },
        headlineContent = { Text(invitation.group.name) },
        supportingContent = {
            invitation.invitedBy?.let { Text(stringResource(R.string.mygroups_invited_by, it)) }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDecline) { Text(stringResource(R.string.mygroups_decline)) }
                Button(onClick = onAccept) { Text(stringResource(R.string.mygroups_accept)) }
            }
        }
    )
}

private const val OWNER = "owner"
private const val ORGANIZER = "organizer"
