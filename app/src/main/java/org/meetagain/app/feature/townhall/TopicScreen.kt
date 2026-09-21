package org.meetagain.app.feature.townhall

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.CommentComposer
import org.meetagain.app.core.ui.CommentRow
import org.meetagain.app.core.ui.Comments
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun TopicRoute(
    container: AppContainer,
    slug: String,
    id: Int,
    onBack: () -> Unit,
    onOpenTopic: (Int) -> Unit,
    onOpenMember: (Int) -> Unit
) {
    val viewModel = viewModel(key = "topic-$slug-$id") { TopicViewModel(container.townHallRepository, slug, id) }
    val header by viewModel.header.collectAsStateWithLifecycle()
    val replies by viewModel.replies.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val problemText = problem?.let { townHallProblemText(it) }
    LaunchedEffect(problem) {
        if (problemText != null) {
            snackbarHostState.showSnackbar(problemText)
            viewModel.dismissProblem()
        }
    }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    TopicScreen(
        header = header,
        replies = replies,
        draft = draft,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onNewSubtopic = viewModel::newSubtopic,
        onRename = viewModel::rename,
        onDelete = { confirmDelete = true },
        onOpenTopic = onOpenTopic,
        onOpenMember = onOpenMember,
        onDraft = viewModel::draft,
        onSend = viewModel::send,
        onLoadOlder = viewModel::loadOlder,
        onDeleteReply = viewModel::deleteReply,
        onUndoDeleteReply = viewModel::undoDeleteReply
    )
    dialog?.let { TitleDialogView(it, viewModel::editTitle, viewModel::confirmTitle, viewModel::dismissTitle) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.town_hall_delete_topic_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteTopic()
                    }
                ) { Text(stringResource(R.string.town_hall_delete_topic)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    header: TopicHeader?,
    replies: Loadable<Comments>,
    draft: String,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNewSubtopic: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenTopic: (Int) -> Unit,
    onOpenMember: (Int) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteReply: (Int) -> Unit,
    onUndoDeleteReply: (Int) -> Unit
) {
    val undo = stringResource(R.string.undo)
    val removed = stringResource(R.string.town_hall_reply_removed)
    val scope = rememberCoroutineScope()
    val time = rememberEventTime()
    val topic = header?.topic
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (topic?.canHaveSubtopics == true) {
                        IconButton(onClick = onNewSubtopic, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.town_hall_new_subtopic))
                        }
                    }
                    if (topic?.canRename == true) {
                        IconButton(onClick = onRename, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.town_hall_rename))
                        }
                    }
                    if (topic?.canDelete == true) {
                        IconButton(onClick = onDelete, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.town_hall_delete_topic))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { CommentComposer(draft, busy, onDraft, onSend) }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (replies) {
            Loadable.Loading -> LoadingState(modifier)

            is Loadable.Failed -> ErrorState(replies.error, onRetry, modifier)

            is Loadable.Loaded -> LazyColumn(modifier) {
                replies.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                topic?.let {
                    item(key = "started") {
                        Text(
                            listOfNotNull(it.authorName, it.startedAt?.let { at -> time.relativeDay(at) })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                val subtopics = header?.subtopics.orEmpty()
                if (subtopics.isNotEmpty()) {
                    item(key = "subtopics") { Heading(stringResource(R.string.town_hall_subtopics)) }
                    items(subtopics, key = { "subtopic-${it.id}" }) { subtopic ->
                        TopicRow(subtopic, indent = false) { onOpenTopic(subtopic.id) }
                    }
                }
                item(key = "replies") { Heading(stringResource(R.string.town_hall_replies_title)) }
                items(replies.value.visible, key = { "reply-${it.id}" }) { reply ->
                    CommentRow(
                        comment = reply,
                        onDelete = {
                            onDeleteReply(reply.id)
                            snackbarHostState.currentSnackbarData?.dismiss()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(removed, actionLabel = undo)
                                if (result == SnackbarResult.ActionPerformed) onUndoDeleteReply(reply.id)
                            }
                        },
                        onOpenAuthor = onOpenMember
                    )
                    HorizontalDivider()
                }
                item(key = "end") {
                    if (replies.value.hasOlder) {
                        TextButton(
                            onClick = onLoadOlder,
                            enabled = !replies.value.loadingOlder,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.conversation_older)) }
                    } else {
                        ListEnd(
                            stringResource(
                                if (replies.value.visible.isEmpty()) {
                                    R.string.conversation_empty
                                } else {
                                    R.string.conversation_end
                                }
                            ),
                            Modifier.fillMaxWidth()
                        )
                    }
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
