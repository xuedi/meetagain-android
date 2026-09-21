package org.meetagain.app.feature.applock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.auth.SessionState

/**
 * The only screen while the app is locked. It asks for the fingerprint or PIN as it opens, and offers the password as
 * the way out for a member who cannot pass the prompt.
 */
@Composable
fun UnlockRoute(container: AppContainer) {
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val session by container.auth.state.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val title = stringResource(R.string.unlock_prompt_title)
    val cancel = stringResource(R.string.cancel)

    val unlock: () -> Unit = unlock@{
        if (activity == null || busy) return@unlock
        busy = true
        scope.launch {
            // Null when the key is gone: the session has already ended, and the sign-in screen takes over.
            val cipher = container.auth.unlockCipher()
            if (cipher == null) {
                busy = false
                return@launch
            }
            activity.showLockPrompt(title, cancel, cipher) { result ->
                scope.launch {
                    failed = when (result) {
                        is PromptResult.Passed -> !container.auth.unlock(result.cipher)
                        PromptResult.Stopped -> false
                        PromptResult.Failed -> true
                    }
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(Unit) { unlock() }

    UnlockScreen(
        name = (session as? SessionState.Locked)?.name.orEmpty(),
        failed = failed,
        busy = busy,
        onUnlock = unlock,
        onSignInAgain = { scope.launch { container.auth.leaveLocked() } }
    )
}

@Composable
fun UnlockScreen(name: String, failed: Boolean, busy: Boolean, onUnlock: () -> Unit, onSignInAgain: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            if (name.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.unlock_signed_in_as, name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (failed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.unlock_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, enabled = !busy) { Text(stringResource(R.string.unlock_action)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignInAgain, enabled = !busy) {
                Text(stringResource(R.string.unlock_sign_in_again))
            }
        }
    }
}
