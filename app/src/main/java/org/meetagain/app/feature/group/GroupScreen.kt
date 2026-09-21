package org.meetagain.app.feature.group

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.auth.SessionState
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.GroupFeature
import org.meetagain.app.core.data.MembershipStatus
import org.meetagain.app.core.data.calendarFeedUrl
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.GroupLogo
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.rememberOpenIntent
import org.meetagain.app.core.ui.rememberOpenUrl
import org.meetagain.app.core.ui.subscribeIntent
import org.meetagain.app.feature.event.eventItems

@Composable
fun GroupRoute(
    container: AppContainer,
    slug: String,
    onBack: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenMembers: () -> Unit = {},
    onOpenTownHall: (Group) -> Unit = {}
) {
    val session by container.auth.state.collectAsStateWithLifecycle()
    val signedIn = session is SessionState.SignedIn
    val viewModel = viewModel(key = "group-$slug") {
        GroupViewModel(container.publicRepository, container.memberRepository, signedIn, slug)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val standing by viewModel.standing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val needsConsent by viewModel.needsConsent.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val openIntent = rememberOpenIntent()
    var manualFeedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.calendar_subscribe)
    val copied = stringResource(R.string.calendar_address_copied)
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val problemText = problem?.let { membershipProblemText(it) }
    LaunchedEffect(problem) {
        if (problemText != null) {
            snackbarHostState.showSnackbar(problemText)
            viewModel.dismissProblem()
        }
    }
    GroupScreen(
        state = state,
        standing = standing,
        signedIn = viewModel.canAct,
        busy = busy,
        onJoin = { viewModel.join() },
        onLeave = { confirmLeave = true },
        onAcceptInvitation = { viewModel.acceptInvitation(it) },
        onDeclineInvitation = { viewModel.declineInvitation(it) },
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenEvent = onOpenEvent,
        onOpenMembers = onOpenMembers,
        onOpenTownHall = onOpenTownHall,
        onOpenWebsite = rememberOpenUrl(),
        onSubscribe = { url -> if (!openIntent(subscribeIntent(url))) manualFeedUrl = url },
        manualFeedUrl = manualFeedUrl,
        onDismissManualFeed = { manualFeedUrl = null },
        onCopyFeedUrl = { url ->
            manualFeedUrl = null
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, url)))
                // From Android 13 on the system confirms a copy itself.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(copied)
            }
        },
        snackbarHostState = snackbarHostState
    )
    if (needsConsent) {
        CrossingConsentDialog(
            onDismiss = viewModel::dismissConsent,
            onAnswer = { consent ->
                val invitation = standing.invitation
                if (invitation != null) {
                    viewModel.acceptInvitation(invitation.id, consent)
                } else {
                    viewModel.join(consent)
                }
            }
        )
    }
    if (confirmLeave) {
        LeaveDialog(
            groupName = (state as? Loadable.Loaded)?.value?.details?.group?.name.orEmpty(),
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                viewModel.leave()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: Loadable<GroupPage>,
    standing: GroupStanding = GroupStanding(null, null),
    signedIn: Boolean = false,
    busy: Boolean = false,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    onAcceptInvitation: (Int) -> Unit = {},
    onDeclineInvitation: (Int) -> Unit = {},
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenMembers: () -> Unit = {},
    onOpenTownHall: (Group) -> Unit = {},
    onOpenWebsite: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    manualFeedUrl: String? = null,
    onDismissManualFeed: () -> Unit = {},
    onCopyFeedUrl: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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

            is Loadable.Loaded -> Column(modifier) {
                state.stale?.let { StaleNotice(it, onRetry = onRetry) }
                GroupContent(
                    page = state.value,
                    standing = standing,
                    signedIn = signedIn,
                    busy = busy,
                    onOpenEvent = onOpenEvent,
                    onOpenMembers = onOpenMembers,
                    onOpenTownHall = onOpenTownHall,
                    onOpenWebsite = onOpenWebsite,
                    onSubscribe = onSubscribe,
                    onJoin = onJoin,
                    onLeave = onLeave,
                    onAcceptInvitation = onAcceptInvitation,
                    onDeclineInvitation = onDeclineInvitation,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    manualFeedUrl?.let { SubscribeSheet(it, onDismissManualFeed, onCopyFeedUrl) }
}

@Composable
private fun GroupContent(
    page: GroupPage,
    standing: GroupStanding,
    signedIn: Boolean,
    busy: Boolean,
    onOpenEvent: (Event) -> Unit,
    onOpenMembers: () -> Unit,
    onOpenTownHall: (Group) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onAcceptInvitation: (Int) -> Unit,
    onDeclineInvitation: (Int) -> Unit,
    modifier: Modifier
) {
    val time = rememberEventTime()
    val language = AppLocale.current(LocalConfiguration.current.locales[0])
    val feedUrl = remember(page.details, language) { calendarFeedUrl(page.details, language) }
    LazyColumn(modifier) {
        item(key = "header") { Header(page.details, feedUrl, onOpenWebsite, onSubscribe) }
        if (signedIn) {
            item(key = "members") {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = onOpenMembers) { Text(stringResource(R.string.group_members_title)) }
                    // Only where the group opens its Town Hall to this member; nowhere else does it exist.
                    if (GroupFeature.TownHall in standing.membership?.features.orEmpty()) {
                        TextButton(onClick = { onOpenTownHall(page.details.group) }) {
                            Text(stringResource(R.string.nav_town_hall))
                        }
                    }
                }
            }
            item(key = "membership") {
                MembershipSection(
                    details = page.details,
                    standing = standing,
                    busy = busy,
                    onJoin = onJoin,
                    onLeave = onLeave,
                    onAcceptInvitation = onAcceptInvitation,
                    onDeclineInvitation = onDeclineInvitation,
                    onOpenWebsite = onOpenWebsite
                )
            }
        }
        item(key = "upcoming") {
            Text(
                text = stringResource(R.string.group_upcoming),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .semantics { heading() }
            )
        }
        eventItems(page.upcoming.events, time, onOpenEvent)
        item(key = "end") {
            val end = when {
                page.upcoming.events.isEmpty() -> R.string.events_empty
                page.upcoming.complete -> R.string.events_end
                else -> R.string.events_end_more
            }
            ListEnd(stringResource(end), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Header(
    details: GroupDetails,
    feedUrl: String?,
    onOpenWebsite: (String) -> Unit,
    onSubscribe: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupLogo(details.group, size = 64.dp)
            Column {
                Text(
                    text = details.group.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = pluralStringResource(R.plurals.group_members, details.memberCount, details.memberCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        details.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            details.websiteUrl?.let { url ->
                OutlinedButton(onClick = { onOpenWebsite(url) }) { Text(stringResource(R.string.group_website)) }
            }
            feedUrl?.let { url ->
                OutlinedButton(onClick = { onSubscribe(url) }) { Text(stringResource(R.string.calendar_subscribe)) }
            }
        }
        if (feedUrl != null) {
            Text(
                text = stringResource(R.string.calendar_subscribe_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown when no app on the phone takes a `webcal:` link: the address to add elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscribeSheet(feedUrl: String, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.calendar_subscribe_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.calendar_subscribe_no_app), style = MaterialTheme.typography.bodyLarge)
            SelectionContainer {
                Text(
                    text = feedUrl,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
            Button(onClick = { onCopy(feedUrl) }) { Text(stringResource(R.string.calendar_copy_address)) }
        }
    }
}

/**
 * Where the member stands with this group and what they can do about it. Joining from the app is for listed Public
 * groups; a Hidden or Private group is joined on its own site, or by an invitation.
 */
@Composable
private fun MembershipSection(
    details: GroupDetails,
    standing: GroupStanding,
    busy: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onAcceptInvitation: (Int) -> Unit,
    onDeclineInvitation: (Int) -> Unit,
    onOpenWebsite: (String) -> Unit
) {
    val membership = standing.membership
    val invitation = standing.invitation
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            membership != null && membership.blocked ->
                Text(stringResource(R.string.membership_blocked), color = MaterialTheme.colorScheme.error)

            membership?.status == MembershipStatus.Approved -> {
                Text(stringResource(R.string.membership_member))
                TextButton(onClick = onLeave, enabled = !busy) {
                    Text(stringResource(R.string.group_leave))
                }
            }

            membership?.status == MembershipStatus.Pending -> Text(stringResource(R.string.membership_pending))

            membership?.status == MembershipStatus.Rejected -> Text(stringResource(R.string.membership_rejected))

            invitation != null -> {
                Text(stringResource(R.string.group_invited))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAcceptInvitation(invitation.id) }, enabled = !busy) {
                        Text(stringResource(R.string.mygroups_accept))
                    }
                    OutlinedButton(onClick = { onDeclineInvitation(invitation.id) }, enabled = !busy) {
                        Text(stringResource(R.string.mygroups_decline))
                    }
                }
            }

            // Every group the app can show is one the server lets a member ask to join; where it does not, it
            // refuses with its own reason, and the page says so then.
            else -> Button(onClick = onJoin, enabled = !busy) { Text(stringResource(R.string.group_join)) }
        }
    }
}

/** The platform's own question, worded and unchecked as on the website, asked only when the server asks for it. */
@Composable
private fun CrossingConsentDialog(onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    var consent by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crossing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.crossing_body))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(stringResource(R.string.crossing_mail_consent))
                }
            }
        },
        confirmButton = { Button(onClick = { onAnswer(consent) }) { Text(stringResource(R.string.group_join)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Leaving cannot be taken back cleanly - coming back can need approval - so it is confirmed first. */
@Composable
private fun LeaveDialog(groupName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_leave)) },
        text = { Text(stringResource(R.string.group_leave_confirm, groupName)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.group_leave)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
