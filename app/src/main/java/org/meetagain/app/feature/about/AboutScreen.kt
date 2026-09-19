package org.meetagain.app.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.i18n.AppLocale
import org.meetagain.app.core.network.ApiError

@Composable
fun AboutRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel = viewModel { AboutViewModel(container.api, container.appInfo) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val languageSettingsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val open = { intent: Intent ->
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No app can open it (no browser installed); the row stays as it is.
        }
    }
    AboutScreen(
        state = state,
        languageName = locale.getDisplayLanguage(locale)
            .replaceFirstChar { it.titlecase(locale) }
            .takeIf { languageSettingsAvailable },
        onBack = onBack,
        onRetry = viewModel::checkServer,
        onOpenLanguageSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                open(
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        },
        onOpenLegalPage = { page ->
            val url = "${state.appInfo.baseUrl}/${AppLocale.current(locale)}/${page.path}"
            open(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    )
}

enum class LegalPage(val path: String) {
    Privacy("privacy"),
    Imprint("imprint")
}

/**
 * [languageName] is the app's current language, or null where Android has no per-app language setting (below 13)
 * and the app follows the device language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: AboutUiState,
    languageName: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
                .verticalScroll(rememberScrollState())
        ) {
            val version = if (state.appInfo.testBuild) {
                stringResource(R.string.about_version_test_build, state.appInfo.version)
            } else {
                state.appInfo.version
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version)) },
                supportingContent = { Text(version) }
            )
            ServerItem(state.appInfo.serverName, state.serverCheck, onRetry)
            if (languageName != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_language)) },
                    supportingContent = { Text(languageName) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLanguageSettings)
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_language)) },
                    supportingContent = { Text(stringResource(R.string.about_language_follows_device)) }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_privacy)) },
                modifier = Modifier.clickable(role = Role.Button) { onOpenLegalPage(LegalPage.Privacy) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_imprint)) },
                modifier = Modifier.clickable(role = Role.Button) { onOpenLegalPage(LegalPage.Imprint) }
            )
        }
    }
}

@Composable
private fun ServerItem(serverName: String, check: ServerCheck, onRetry: () -> Unit) {
    val status = when (check) {
        ServerCheck.Checking -> stringResource(R.string.about_server_checking)

        ServerCheck.Reachable -> stringResource(R.string.about_server_reachable)

        is ServerCheck.Failed -> when (val error = check.error) {
            ApiError.Offline -> stringResource(R.string.about_server_offline)
            ApiError.Timeout -> stringResource(R.string.about_server_timeout)
            ApiError.Malformed -> stringResource(R.string.about_server_malformed)
            is ApiError.Http -> stringResource(R.string.about_server_http_error, error.status)
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_server)) },
        supportingContent = {
            Column {
                Text(serverName)
                Text(
                    text = status,
                    color = if (check is ServerCheck.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        },
        trailingContent = {
            when (check) {
                ServerCheck.Checking -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                ServerCheck.Reachable -> Unit
                is ServerCheck.Failed -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    )
}
