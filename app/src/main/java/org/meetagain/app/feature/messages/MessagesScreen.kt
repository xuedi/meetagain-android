package org.meetagain.app.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.InboxEntry
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun MessagesRoute(
    container: AppContainer,
    onOpenThread: (InboxEntry) -> Unit,
    onOpenMe: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val viewModel = viewModel { MessagesViewModel(container.memberRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val text = problem?.let { inboxProblemText(it) }
    LaunchedEffect(problem) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissProblem()
        }
    }
    MessagesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::load,
        onOpenThread = onOpenThread,
        onOpenMe = onOpenMe,
        onLoadMore = viewModel::loadMore,
        bottomBar = bottomBar
    )
}

@Composable
private fun inboxProblemText(problem: InboxProblem): String = stringResource(
    when (problem) {
        InboxProblem.Offline -> R.string.error_offline
        InboxProblem.Failed -> R.string.messages_failed
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: Loadable<Conversations>,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onOpenThread: (InboxEntry) -> Unit,
    onOpenMe: () -> Unit,
    onLoadMore: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
                actions = {
                    IconButton(onClick = onOpenMe) {
                        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.me_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state) {
            Loadable.Loading -> LoadingState(modifier)

            is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

            is Loadable.Loaded -> LazyColumn(modifier) {
                state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                items(state.value.entries, key = { "conversation-${it.partner.id}" }) { entry ->
                    ConversationRow(entry) { onOpenThread(entry) }
                }
                item(key = "end") {
                    if (state.value.hasMore) {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !state.value.loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.messages_more))
                        }
                    } else {
                        ListEnd(
                            if (state.value.entries.isEmpty()) {
                                stringResource(R.string.messages_empty)
                            } else {
                                stringResource(R.string.messages_end)
                            },
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** One stop for a screen reader: who it is with, how much was said, how much is unread and when the last one came. */
@Composable
private fun ConversationRow(entry: InboxEntry, onOpen: () -> Unit) {
    val time = rememberEventTime()
    val resources = LocalResources.current
    val messages = resources.getQuantityString(R.plurals.messages_count, entry.messages, entry.messages)
    val unread = entry.unread
        .takeIf { it > 0 }
        ?.let { resources.getQuantityString(R.plurals.messages_unread, it, it) }
    val last = entry.lastMessageAt?.let { time.relativeDay(it) }
    val sentence = listOfNotNull(entry.partner.name, messages, unread, last)
        .joinToString(resources.getString(R.string.list_separator))
    ListItem(
        leadingContent = {
            AsyncImage(
                model = entry.partner.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_person),
                error = painterResource(R.drawable.ic_person),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = { Text(entry.partner.name) },
        supportingContent = { Text(listOfNotNull(messages, unread).joinToString(SEPARATOR)) },
        trailingContent = last?.let { { Text(it) } },
        modifier = Modifier
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = sentence }
    )
}

private const val SEPARATOR = " · "
