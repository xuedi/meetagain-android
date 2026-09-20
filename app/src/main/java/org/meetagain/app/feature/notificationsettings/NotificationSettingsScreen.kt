package org.meetagain.app.feature.notificationsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.NotificationSetting
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun NotificationSettingsRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel = viewModel { NotificationSettingsViewModel(container.memberRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = stringResource(R.string.notification_settings_failed)
    LaunchedEffect(failed) {
        if (failed) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissFailure()
        }
    }
    NotificationSettingsScreen(
        state = state,
        pending = pending,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onSet = viewModel::set
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    state: Loadable<NotificationSettings>,
    pending: Set<NotificationSetting> = emptySet(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSet: (NotificationSetting, Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notification_settings_title)) },
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
            is Loadable.Loaded -> Settings(state, modifier, pending, onRetry, onSet)
        }
    }
}

@Composable
private fun Settings(
    state: Loadable.Loaded<NotificationSettings>,
    modifier: Modifier,
    pending: Set<NotificationSetting>,
    onRetry: () -> Unit,
    onSet: (NotificationSetting, Boolean) -> Unit
) {
    val settings = state.value
    Column(modifier.verticalScroll(rememberScrollState())) {
        state.stale?.let { StaleNotice(it, onRetry) }
        Text(
            text = stringResource(R.string.notification_settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        SettingRow(
            label = stringResource(R.string.notification_settings_master),
            supporting = stringResource(R.string.notification_settings_master_off).takeIf { !settings.master },
            checked = settings.master,
            enabled = NotificationSetting.Master !in pending,
            onChange = { onSet(NotificationSetting.Master, it) }
        )
        HorizontalDivider()
        // The master switch decides for all of them, so they are shown but cannot be changed while it is off.
        Switches.forEach { (setting, label) ->
            SettingRow(
                label = stringResource(label),
                supporting = null,
                checked = settings.valueOf(setting),
                enabled = settings.master && setting !in pending,
                onChange = { onSet(setting, it) }
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    supporting: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        },
        modifier = Modifier.semantics {
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        }
    )
}

private fun NotificationSettings.valueOf(setting: NotificationSetting): Boolean = when (setting) {
    NotificationSetting.Master -> master
    NotificationSetting.Announcements -> announcements
    NotificationSetting.FollowingUpdates -> followingUpdates
    NotificationSetting.ReceivedMessage -> receivedMessage
    NotificationSetting.EventReminder -> eventReminder
    NotificationSetting.UpcomingEvents -> upcomingEvents
    NotificationSetting.AttendedEventUpdate -> attendedEventUpdate
}

/** The six the website offers, in the order it offers them. */
private val Switches = listOf(
    NotificationSetting.Announcements to R.string.notification_settings_announcements,
    NotificationSetting.FollowingUpdates to R.string.notification_settings_following,
    NotificationSetting.ReceivedMessage to R.string.notification_settings_message,
    NotificationSetting.EventReminder to R.string.notification_settings_reminder,
    NotificationSetting.UpcomingEvents to R.string.notification_settings_upcoming,
    NotificationSetting.AttendedEventUpdate to R.string.notification_settings_event_update
)
