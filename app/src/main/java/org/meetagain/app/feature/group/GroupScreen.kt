package org.meetagain.app.feature.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.GroupLogo
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.rememberOpenUrl
import org.meetagain.app.feature.event.eventItems

@Composable
fun GroupRoute(container: AppContainer, slug: String, onBack: () -> Unit, onOpenEvent: (Event) -> Unit) {
    val viewModel = viewModel(key = "group-$slug") { GroupViewModel(container.publicRepository, slug) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenEvent = onOpenEvent,
        onOpenWebsite = rememberOpenUrl()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: Loadable<GroupPage>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenWebsite: (String) -> Unit
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
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state) {
            Loadable.Loading -> LoadingState(modifier)
            is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)
            is Loadable.Loaded -> GroupContent(state.value, onOpenEvent, onOpenWebsite, modifier)
        }
    }
}

@Composable
private fun GroupContent(
    page: GroupPage,
    onOpenEvent: (Event) -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier
) {
    val time = rememberEventTime()
    LazyColumn(modifier) {
        item(key = "header") { Header(page.details, onOpenWebsite) }
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
private fun Header(details: GroupDetails, onOpenWebsite: (String) -> Unit) {
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
        details.websiteUrl?.let { url ->
            OutlinedButton(onClick = { onOpenWebsite(url) }) { Text(stringResource(R.string.group_website)) }
        }
    }
}
