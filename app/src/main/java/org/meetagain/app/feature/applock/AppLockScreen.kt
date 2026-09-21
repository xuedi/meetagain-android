package org.meetagain.app.feature.applock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.crypto.Cipher
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.auth.AuthRepository

/**
 * Turning the app lock on or off. Both directions ask for the fingerprint or PIN: turning it on seals the token
 * under the locked key, turning it off opens it again, and neither is something whoever holds an open phone may do.
 */
@Composable
fun AppLockRoute(container: AppContainer, onBack: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lockOn by container.auth.lockOn.collectAsStateWithLifecycle()
    val hasSignal by container.signalStore.hasToken.collectAsStateWithLifecycle(true)
    // Checked again on every return, because the member may have gone to set up a screen lock.
    var availability by remember { mutableStateOf(lockAvailability(context)) }
    LifecycleResumeEffect(Unit) {
        availability = lockAvailability(context)
        onPauseOrDispose { }
    }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val titleOn = stringResource(R.string.app_lock_prompt_on)
    val titleOff = stringResource(R.string.app_lock_prompt_off)
    val cancel = stringResource(R.string.cancel)

    val change: (Boolean) -> Unit = change@{ on ->
        if (activity == null || busy) return@change
        busy = true
        failed = false
        scope.launch {
            val cipher = if (on) container.auth.lockCipher() else container.auth.unlockCipherForTurningOff()
            if (cipher == null) {
                failed = true
                busy = false
                return@launch
            }
            activity.showLockPrompt(if (on) titleOn else titleOff, cancel, cipher) { result ->
                scope.launch {
                    failed = when (result) {
                        is PromptResult.Passed -> !container.auth.switchLock(on, result.cipher)
                        PromptResult.Stopped -> false
                        PromptResult.Failed -> true
                    }
                    busy = false
                }
            }
        }
    }

    AppLockScreen(
        lockOn = lockOn,
        availability = availability,
        pinAllowed = pinAllowed,
        timerAvailable = hasSignal,
        busy = busy,
        failed = failed,
        onChange = change,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    lockOn: Boolean,
    availability: LockAvailability,
    pinAllowed: Boolean,
    timerAvailable: Boolean,
    busy: Boolean,
    failed: Boolean,
    onChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_lock_title)) },
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
            val explained = if (pinAllowed) R.string.app_lock_explained_pin else R.string.app_lock_explained_fingerprint
            // A lock that is on can always be turned off, even when the phone could no longer turn it on.
            val canChange = !busy && (lockOn || availability == LockAvailability.Available)
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_lock_switch)) },
                supportingContent = { Text(stringResource(explained)) },
                trailingContent = { Switch(checked = lockOn, onCheckedChange = onChange, enabled = canChange) }
            )
            if (failed) Paragraph(stringResource(R.string.app_lock_failed), MaterialTheme.colorScheme.error)
            if (!lockOn) {
                reasonFor(availability)?.let { Paragraph(stringResource(it)) }
            }
            Paragraph(stringResource(R.string.app_lock_notifications))
            if (lockOn && !timerAvailable) Paragraph(stringResource(R.string.app_lock_no_timer))
            Paragraph(stringResource(R.string.app_lock_optional))
        }
    }
}

private suspend fun AuthRepository.switchLock(on: Boolean, cipher: Cipher): Boolean =
    if (on) turnLockOn(cipher) else turnLockOff(cipher)

private fun reasonFor(availability: LockAvailability): Int? = when (availability) {
    LockAvailability.Available -> null
    LockAvailability.NoScreenLock -> R.string.app_lock_no_screen_lock
    LockAvailability.NoFingerprint -> R.string.app_lock_no_fingerprint
    LockAvailability.Unsupported -> R.string.app_lock_unsupported
}

@Composable
private fun Paragraph(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
