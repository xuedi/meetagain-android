package org.meetagain.app.feature.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.MemberProfile
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.CommunityError
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun MemberRoute(container: AppContainer, id: Int, onBack: () -> Unit, onOpenThread: (Int) -> Unit) {
    val viewModel = viewModel(key = "member-$id") { MemberViewModel(container.memberRepository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val text = problem?.let { memberProblemText(it) }
    LaunchedEffect(problem) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissProblem()
        }
    }
    MemberScreen(
        state = state,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenThread = onOpenThread,
        onToggleFollow = viewModel::toggleFollow,
        onToggleBlock = viewModel::toggleBlock
    )
}

@Composable
private fun memberProblemText(problem: MemberProblem): String = stringResource(
    when (problem) {
        MemberProblem.Refused -> R.string.member_refused
        MemberProblem.Blocked -> R.string.member_blocked_refusal
        MemberProblem.Offline -> R.string.error_offline
        MemberProblem.Failed -> R.string.messages_failed
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberScreen(
    state: Loadable<MemberProfile>,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenThread: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit
) {
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    val member = (state as? Loadable.Loaded)?.value
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(member?.name.orEmpty()) },
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
        when {
            state is Loadable.Loading -> LoadingState(modifier)

            // The other member has blocked the caller: one sentence, and nothing to do.
            state is Loadable.Failed && state.error.isRefusal -> Refused(modifier)

            state is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

            state is Loadable.Loaded -> Column(
                modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.stale?.let { StaleNotice(it, onRetry) }
                Profile(state.value)
                Actions(
                    member = state.value,
                    busy = busy,
                    onOpenThread = { onOpenThread(state.value.id) },
                    onToggleFollow = onToggleFollow,
                    onBlock = { confirmBlock = true },
                    onUnblock = onToggleBlock
                )
            }
        }
    }
    if (confirmBlock && member != null) {
        BlockSheet(
            name = member.name,
            onDismiss = { confirmBlock = false },
            onConfirm = {
                confirmBlock = false
                onToggleBlock()
            }
        )
    }
}

@Composable
private fun Profile(member: MemberProfile) {
    val time = rememberEventTime()
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_person),
            error = painterResource(R.drawable.ic_person),
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
        )
        Text(
            text = member.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        member.memberSince?.let {
            Text(
                text = stringResource(R.string.member_since, time.relativeDay(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (member.followsMe) {
            Text(
                text = stringResource(R.string.member_follows_you),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        member.bio?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** With a block of the member's own in place, unblocking is the only thing on offer. */
@Composable
private fun Actions(
    member: MemberProfile,
    busy: Boolean,
    onOpenThread: () -> Unit,
    onToggleFollow: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit
) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (member.blockedByMe) {
            Button(onClick = onUnblock, enabled = !busy) { Text(stringResource(R.string.member_unblock)) }
            return@FlowRow
        }
        if (member.canMessage) {
            Button(onClick = onOpenThread) { Text(stringResource(R.string.member_message)) }
        }
        OutlinedButton(onClick = onToggleFollow, enabled = !busy) {
            Text(
                if (member.following) {
                    stringResource(R.string.member_unfollow)
                } else {
                    stringResource(R.string.member_follow)
                }
            )
        }
        OutlinedButton(onClick = onBlock, enabled = !busy) { Text(stringResource(R.string.member_block)) }
    }
}

@Composable
private fun Refused(modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.member_refused), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Blocking is not undone from here, so it says what it does before it happens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockSheet(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.member_block_heading, name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.member_block_explained), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.member_block))
                }
            }
        }
    }
}

private val ApiError.isRefusal: Boolean
    get() = this is ApiError.Http && code == CommunityError.FORBIDDEN
