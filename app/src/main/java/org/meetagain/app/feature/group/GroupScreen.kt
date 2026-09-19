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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.GroupDetails
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
fun GroupRoute(container: AppContainer, slug: String, onBack: () -> Unit, onOpenEvent: (Event) -> Unit) {
    val viewModel = viewModel(key = "group-$slug") { GroupViewModel(container.publicRepository, slug) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openIntent = rememberOpenIntent()
    var manualFeedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.calendar_subscribe)
    val copied = stringResource(R.string.calendar_address_copied)
    GroupScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenEvent = onOpenEvent,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: Loadable<GroupPage>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEvent: (Event) -> Unit,
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
                GroupContent(state.value, onOpenEvent, onOpenWebsite, onSubscribe, Modifier.weight(1f))
            }
        }
    }
    manualFeedUrl?.let { SubscribeSheet(it, onDismissManualFeed, onCopyFeedUrl) }
}

@Composable
private fun GroupContent(
    page: GroupPage,
    onOpenEvent: (Event) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onSubscribe: (String) -> Unit,
    modifier: Modifier
) {
    val time = rememberEventTime()
    val language = AppLocale.current(LocalConfiguration.current.locales[0])
    val feedUrl = remember(page.details, language) { calendarFeedUrl(page.details, language) }
    LazyColumn(modifier) {
        item(key = "header") { Header(page.details, feedUrl, onOpenWebsite, onSubscribe) }
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
