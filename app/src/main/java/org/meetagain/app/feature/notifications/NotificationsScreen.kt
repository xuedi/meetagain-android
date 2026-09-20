package org.meetagain.app.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Notification
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.rememberOpenUrl

/**
 * What is waiting for the member, the same list the website's bell shows. An item opens the app's own screen where
 * there is one and the website where there is not - a member who is also staff gets items no screen here covers.
 */
@Composable
fun NotificationsRoute(container: AppContainer, onBack: () -> Unit, onOpen: (Notification) -> Boolean) {
    val viewModel = viewModel { NotificationsViewModel(container.memberRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openUrl = rememberOpenUrl()
    NotificationsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpen = { notification ->
            if (!onOpen(notification)) notification.webUrl?.let(openUrl)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: Loadable<List<Notification>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Notification) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
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

            is Loadable.Loaded -> LazyColumn(modifier) {
                state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                items(state.value, key = { it.key + it.text }) { notification ->
                    NotificationRow(notification) { onOpen(notification) }
                }
                item(key = "end") {
                    ListEnd(
                        if (state.value.isEmpty()) {
                            stringResource(R.string.notifications_empty)
                        } else {
                            stringResource(R.string.notifications_end)
                        }
                    )
                }
            }
        }
    }
}

/**
 * The label is the sentence the server already wrote in the member's language; the app shows it and never takes it
 * apart. One row is one screen-reader stop.
 */
@Composable
private fun NotificationRow(notification: Notification, onOpen: () -> Unit) {
    ListItem(
        headlineContent = { Text(notification.text) },
        modifier = Modifier.clickable(enabled = notification.webUrl != null, onClick = onOpen)
    )
}
