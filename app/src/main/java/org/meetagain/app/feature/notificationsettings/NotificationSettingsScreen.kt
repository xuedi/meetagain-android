package org.meetagain.app.feature.notificationsettings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.NotificationSetting
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.data.PushCategory
import org.meetagain.app.core.data.QuietHours
import org.meetagain.app.core.i18n.websiteUrl
import org.meetagain.app.core.push.PushInterval
import org.meetagain.app.core.push.PushNotifier
import org.meetagain.app.core.push.PushObstacle
import org.meetagain.app.core.push.PushTimer
import org.meetagain.app.core.push.channelName
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.rememberOpenUrl

@Composable
fun NotificationSettingsRoute(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifier = remember(context) { PushNotifier(context) }
    val viewModel = viewModel {
        NotificationSettingsViewModel(
            repository = container.memberRepository,
            preferences = container.pushPreferences,
            registrar = container.pushRegistrar(context.applicationContext),
            onPushWanted = { wanted, interval ->
                if (wanted) {
                    PushTimer.start(context.applicationContext, interval)
                } else {
                    PushTimer.stop(context.applicationContext)
                }
            },
            canPostNotifications = notifier::canPost
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val push by viewModel.push.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = stringResource(R.string.notification_settings_failed)
    val openUrl = rememberOpenUrl()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    LaunchedEffect(push.askPermission) {
        if (push.askPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onPermissionResult(granted = true)
            }
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissFailure()
        }
    }
    NotificationSettingsScreen(
        state = state,
        push = push,
        pending = pending,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onSet = viewModel::set,
        onSetCategory = viewModel::setCategory,
        onSetQuietHours = viewModel::setQuietHours,
        onSetInterval = viewModel::setInterval,
        onMatchEmail = viewModel::matchEmailSettings,
        onLearnMore = { openUrl(websiteUrl(container.appInfo.baseUrl, PRIVACY)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    state: Loadable<NotificationSettings>,
    push: PushUiState = PushUiState(),
    pending: Set<String> = emptySet(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSet: (NotificationSetting, Boolean) -> Unit,
    onSetCategory: (PushCategory, Boolean) -> Unit = { _, _ -> },
    onSetQuietHours: (QuietHours) -> Unit = {},
    onSetInterval: (PushInterval) -> Unit = {},
    onMatchEmail: () -> Unit = {},
    onLearnMore: () -> Unit = {}
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

            is Loadable.Loaded -> Column(modifier.verticalScroll(rememberScrollState())) {
                state.stale?.let { StaleNotice(it, onRetry) }
                EmailSettings(state.value, pending, onSet)
                HorizontalDivider()
                PushSettings(state.value, push, pending, onSetCategory, onSetInterval, onMatchEmail, onLearnMore)
                if (state.value.master) {
                    HorizontalDivider()
                    QuietHoursSettings(state.value, pending, onSetQuietHours)
                }
            }
        }
    }
}

@Composable
private fun EmailSettings(
    settings: NotificationSettings,
    pending: Set<String>,
    onSet: (NotificationSetting, Boolean) -> Unit
) {
    Paragraph(stringResource(R.string.notification_settings_intro))
    SettingRow(
        label = stringResource(R.string.notification_settings_master),
        supporting = stringResource(R.string.notification_settings_master_off).takeIf { !settings.master },
        checked = settings.master,
        enabled = NotificationSetting.Master.key !in pending,
        onChange = { onSet(NotificationSetting.Master, it) }
    )
    HorizontalDivider()
    // The master switch decides for all of them, so they are shown but cannot be changed while it is off.
    EmailSwitches.forEach { (setting, label) ->
        SettingRow(
            label = stringResource(label),
            supporting = null,
            checked = settings.valueOf(setting),
            enabled = settings.master && setting.key !in pending,
            onChange = { onSet(setting, it) }
        )
    }
}

@Composable
private fun PushSettings(
    settings: NotificationSettings,
    push: PushUiState,
    pending: Set<String>,
    onSetCategory: (PushCategory, Boolean) -> Unit,
    onSetInterval: (PushInterval) -> Unit,
    onMatchEmail: () -> Unit,
    onLearnMore: () -> Unit
) {
    Heading(stringResource(R.string.push_title))
    Paragraph(stringResource(R.string.push_intro))
    when {
        push.obstacle == PushObstacle.ServerHasNone -> Paragraph(stringResource(R.string.push_unavailable))
        push.permissionDenied -> Paragraph(stringResource(R.string.push_permission_denied))
        push.obstacle == PushObstacle.NoDistributor -> Paragraph(stringResource(R.string.push_no_distributor))
        else -> Unit
    }
    PushCategory.entries.forEach { category ->
        SettingRow(
            label = stringResource(category.channelName),
            supporting = null,
            checked = settings.other.push[category.key] == true,
            enabled = settings.master && category.key !in pending,
            onChange = { onSetCategory(category, it) }
        )
    }
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onMatchEmail,
            enabled = settings.master && NotificationSettingsViewModel.MATCH !in pending
        ) {
            Text(stringResource(R.string.push_match_email))
        }
        TextButton(onClick = onLearnMore) { Text(stringResource(R.string.push_learn_more)) }
    }
    Heading(stringResource(R.string.push_interval_title))
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Intervals.forEach { (interval, label) ->
            FilterChip(
                selected = push.interval == interval,
                onClick = { onSetInterval(interval) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

@Composable
private fun QuietHoursSettings(
    settings: NotificationSettings,
    pending: Set<String>,
    onSetQuietHours: (QuietHours) -> Unit
) {
    val quietHours = settings.other.quietHours ?: return
    val enabled = NotificationSettingsViewModel.QUIET_HOURS !in pending
    Heading(stringResource(R.string.quiet_hours_title))
    SettingRow(
        label = stringResource(R.string.quiet_hours_enabled),
        supporting = stringResource(R.string.quiet_hours_window, quietHours.start, quietHours.end),
        checked = quietHours.enabled,
        enabled = enabled,
        onChange = { onSetQuietHours(quietHours.copy(enabled = it)) }
    )
    SettingRow(
        label = stringResource(R.string.quiet_hours_urgent),
        supporting = null,
        checked = quietHours.allowUrgent,
        enabled = enabled && quietHours.enabled,
        onChange = { onSetQuietHours(quietHours.copy(allowUrgent = it)) }
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
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
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
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
private val EmailSwitches = listOf(
    NotificationSetting.Announcements to R.string.notification_settings_announcements,
    NotificationSetting.FollowingUpdates to R.string.notification_settings_following,
    NotificationSetting.ReceivedMessage to R.string.notification_settings_message,
    NotificationSetting.EventReminder to R.string.notification_settings_reminder,
    NotificationSetting.UpcomingEvents to R.string.notification_settings_upcoming,
    NotificationSetting.AttendedEventUpdate to R.string.notification_settings_event_update
)

private val Intervals = listOf(
    PushInterval.QuarterHour to R.string.push_interval_15,
    PushInterval.Hourly to R.string.push_interval_60,
    PushInterval.SixHourly to R.string.push_interval_360
)

private const val PRIVACY = "/privacy"
