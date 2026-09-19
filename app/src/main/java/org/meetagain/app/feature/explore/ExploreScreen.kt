package org.meetagain.app.feature.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.GroupLogo
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.rememberOpenUrl
import org.meetagain.app.feature.event.eventItems

@Composable
fun ExploreRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenGroup: (Group) -> Unit
) {
    val viewModel = viewModel { ExploreViewModel(container.publicRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openUrl = rememberOpenUrl()
    val language = AppLocale.current(LocalConfiguration.current.locales[0])
    ExploreScreen(
        state = state,
        onBack = onBack,
        onRefreshEvents = viewModel::loadEvents,
        onRefreshGroups = viewModel::loadGroups,
        onOpenEvent = onOpenEvent,
        onOpenGroup = onOpenGroup,
        onOpenAllEvents = { openUrl("${container.appInfo.baseUrl}/$language/events") }
    )
}

enum class ExploreTab { Events, Groups }

/** The public events and groups, reached only on request from the start screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onBack: () -> Unit,
    onRefreshEvents: () -> Unit,
    onRefreshGroups: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenGroup: (Group) -> Unit,
    onOpenAllEvents: () -> Unit,
    initialTab: ExploreTab = ExploreTab.Events
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == ExploreTab.Events.ordinal,
                    onClick = { tab = ExploreTab.Events.ordinal },
                    text = { Text(stringResource(R.string.explore_tab_events)) }
                )
                Tab(
                    selected = tab == ExploreTab.Groups.ordinal,
                    onClick = { tab = ExploreTab.Groups.ordinal },
                    text = { Text(stringResource(R.string.explore_tab_groups)) }
                )
            }
            when (ExploreTab.entries[tab]) {
                ExploreTab.Events -> Content(state.events, onRefreshEvents) { upcoming ->
                    EventList(upcoming, onOpenEvent, onOpenAllEvents)
                }

                ExploreTab.Groups -> Content(state.groups, onRefreshGroups) { groups ->
                    GroupList(groups, onOpenGroup)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Content(loadable: Loadable<T>, onRefresh: () -> Unit, content: @Composable (T) -> Unit) {
    when (loadable) {
        Loadable.Loading -> LoadingState()

        is Loadable.Failed -> ErrorState(loadable.error, onRetry = onRefresh)

        is Loadable.Loaded -> Column(Modifier.fillMaxSize()) {
            loadable.stale?.let { StaleNotice(it, onRetry = onRefresh) }
            PullToRefreshBox(
                isRefreshing = loadable.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) { content(loadable.value) }
        }
    }
}

@Composable
private fun EventList(upcoming: Upcoming, onOpenEvent: (Event) -> Unit, onOpenAllEvents: () -> Unit) {
    val time = rememberEventTime()
    LazyColumn(Modifier.fillMaxSize()) {
        eventItems(upcoming.events, time, onOpenEvent)
        item(key = "end") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val end = when {
                    upcoming.events.isEmpty() && upcoming.complete -> R.string.events_empty
                    upcoming.complete -> R.string.events_end
                    else -> R.string.events_end_more
                }
                ListEnd(stringResource(end))
                if (!upcoming.complete) {
                    TextButton(onClick = onOpenAllEvents, modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(stringResource(R.string.events_open_website))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<Group>, onOpenGroup: (Group) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.slug }) { group ->
            ListItem(
                leadingContent = { GroupLogo(group, size = 40.dp) },
                headlineContent = { Text(group.name) },
                modifier = Modifier.clickable(role = Role.Button) { onOpenGroup(group) }
            )
        }
        item(key = "end") {
            ListEnd(
                stringResource(if (groups.isEmpty()) R.string.groups_empty else R.string.groups_end),
                Modifier.fillMaxWidth()
            )
        }
    }
}
