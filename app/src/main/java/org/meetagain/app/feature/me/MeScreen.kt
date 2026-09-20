package org.meetagain.app.feature.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.auth.member
import org.meetagain.app.core.i18n.websiteUrl
import org.meetagain.app.core.ui.rememberOpenUrl

/** Everything about the member themselves, and the way out of the app. */
@Composable
fun MeRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenMyGroups: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit
) {
    val openUrl = rememberOpenUrl()
    val session by container.auth.state.collectAsStateWithLifecycle()
    MeScreen(
        name = session.member?.name.orEmpty(),
        onBack = onBack,
        onOpenMyGroups = onOpenMyGroups,
        onOpenProfile = onOpenProfile,
        onOpenAbout = onOpenAbout,
        onSignOut = onSignOut,
        onDeleteAccount = { openUrl(websiteUrl(container.appInfo.baseUrl, PROFILE)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    name: String,
    onBack: () -> Unit,
    onOpenMyGroups: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.me_title)) },
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
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
            Entry(stringResource(R.string.me_my_groups), onOpenMyGroups)
            Entry(stringResource(R.string.me_profile), onOpenProfile)
            Entry(stringResource(R.string.about_title), onOpenAbout)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Entry(stringResource(R.string.me_sign_out), onSignOut)
            Entry(stringResource(R.string.me_delete_account), onDeleteAccount)
        }
    }
}

@Composable
private fun Entry(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

private const val PROFILE = "/profile"
