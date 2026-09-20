package org.meetagain.app.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Message
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun ThreadRoute(container: AppContainer, partnerId: Int, onBack: () -> Unit, onOpenMember: (Int) -> Unit) {
    val viewModel = viewModel(key = "thread-$partnerId") {
        ThreadViewModel(container.memberRepository, partnerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val text = problem?.let { threadProblemText(it) }
    LaunchedEffect(problem) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissProblem()
        }
    }
    ThreadScreen(
        state = state,
        draft = draft,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onDraft = viewModel::draft,
        onSend = viewModel::send,
        onEdit = viewModel::edit,
        onCancelEdit = viewModel::cancelEdit,
        onLoadEarlier = viewModel::loadEarlier,
        onBlock = viewModel::block,
        onOpenMember = onOpenMember
    )
}

@Composable
private fun threadProblemText(problem: ThreadProblem): String = stringResource(
    when (problem) {
        ThreadProblem.Blocked -> R.string.thread_blocked_refusal
        ThreadProblem.EditWindowExpired -> R.string.thread_edit_window_expired
        ThreadProblem.TooLong -> R.string.conversation_too_long
        ThreadProblem.Empty -> R.string.conversation_empty_comment
        ThreadProblem.NotFound -> R.string.error_not_found
        ThreadProblem.Offline -> R.string.error_offline
        ThreadProblem.Failed -> R.string.messages_failed
    }
)

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: Loadable<Thread>,
    draft: Draft,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onEdit: (Message) -> Unit,
    onCancelEdit: () -> Unit,
    onLoadEarlier: () -> Unit,
    onBlock: () -> Unit,
    onOpenMember: (Int) -> Unit
) {
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    val thread = (state as? Loadable.Loaded)?.value
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = thread?.partner?.name.orEmpty()
                    Text(
                        text = name,
                        modifier = Modifier
                            .clickable(enabled = thread != null) { thread?.let { onOpenMember(it.partner.id) } }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (thread != null && !thread.blocked) {
                        IconButton(onClick = { confirmBlock = true }, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_block), stringResource(R.string.member_block))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (thread?.blocked == true) {
                BlockedNotice()
            } else {
                Composer(draft, busy, onDraft, onSend, onCancelEdit)
            }
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state) {
            Loadable.Loading -> LoadingState(modifier)

            is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

            is Loadable.Loaded -> LazyColumn(modifier) {
                state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                item(key = "earlier") {
                    if (state.value.hasEarlier) {
                        TextButton(
                            onClick = onLoadEarlier,
                            enabled = !state.value.loadingEarlier,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.thread_earlier))
                        }
                    }
                }
                items(state.value.messages, key = { "message-${it.id}" }) { message ->
                    MessageRow(message) { onEdit(message) }
                }
                item(key = "end") {
                    if (state.value.messages.isEmpty()) {
                        ListEnd(stringResource(R.string.thread_empty), Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (confirmBlock) {
        BlockSheet(
            name = thread?.partner?.name.orEmpty(),
            onDismiss = { confirmBlock = false },
            onConfirm = {
                confirmBlock = false
                onBlock()
            }
        )
    }
}

/** A message of the member's own sits to the right and in the app's own colour; a system note is neither. */
@Composable
private fun MessageRow(message: Message, onEdit: () -> Unit) {
    val time = rememberEventTime()
    val resources = LocalResources.current
    val who = when {
        message.systemNote -> resources.getString(R.string.thread_system_note)
        message.mine -> resources.getString(R.string.thread_from_you)
        else -> resources.getString(R.string.thread_from_them)
    }
    val edited = message.editedAt?.let { resources.getString(R.string.thread_edited) }
    val sentAt = message.sentAt?.let { "${time.relativeDay(it)}, ${time.time(it)}" }
    val sentence = listOfNotNull(who, message.text, sentAt, edited)
        .joinToString(resources.getString(R.string.list_separator))
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = sentence },
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                message.systemNote -> colors.surfaceContainerHigh
                message.mine -> colors.primaryContainer
                else -> colors.secondaryContainer
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(BUBBLE_WIDTH)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (message.systemNote) {
                    Text(
                        text = stringResource(R.string.thread_system_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(sentAt, edited).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (message.editable) {
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.thread_edit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(draft: Draft, busy: Boolean, onDraft: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (draft.editing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.thread_editing),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = onDraft,
                label = { Text(stringResource(R.string.thread_write)) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            )
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                IconButton(onClick = onSend, enabled = draft.text.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_send), stringResource(R.string.conversation_send))
                }
            }
        }
    }
}

/** Where the composer would be when a block in either direction stops anything from going out. */
@Composable
private fun BlockedNotice() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.thread_blocked),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp)
        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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

private const val SEPARATOR = " · "
private const val BUBBLE_WIDTH = 0.85f
