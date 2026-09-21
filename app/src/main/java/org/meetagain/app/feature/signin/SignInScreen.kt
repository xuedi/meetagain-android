package org.meetagain.app.feature.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.i18n.websiteUrl
import org.meetagain.app.core.network.SessionRefusal
import org.meetagain.app.core.ui.errorMessage
import org.meetagain.app.core.ui.rememberOpenUrl

@Composable
fun SignInRoute(container: AppContainer, onLookAround: () -> Unit, onOpenAbout: () -> Unit) {
    val viewModel = viewModel { SignInViewModel(container.auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openUrl = rememberOpenUrl()
    SignInScreen(
        state = state,
        onEmail = viewModel::email,
        onPassword = viewModel::password,
        onSubmit = viewModel::submit,
        onOpenWebsite = { openUrl(websiteUrl(container.appInfo.baseUrl, it)) },
        onLookAround = onLookAround,
        onOpenAbout = onOpenAbout
    )
}

@Composable
fun SignInScreen(
    state: SignInState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    onLookAround: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSubmit()
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.start_tagline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            state.problem?.let { Problem(it, onOpenWebsite) }
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text(stringResource(R.string.signin_email)) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.EmailAddress }
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text(stringResource(R.string.signin_password)) },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password }
            )
            Button(onClick = submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.signin_submit))
                }
            }
            Text(
                text = stringResource(R.string.signin_password_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { onOpenWebsite(FORGOTTEN_PASSWORD) }) {
                Text(stringResource(R.string.signin_forgot))
            }
            TextButton(onClick = { onOpenWebsite(REGISTER) }) { Text(stringResource(R.string.signin_create)) }
            TextButton(onClick = onLookAround) { Text(stringResource(R.string.start_look_around)) }
            TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.about_title)) }
        }
    }
}

/** The sentence for what went wrong, and the way out where the website is the only one. */
@Composable
private fun Problem(problem: SignInProblem, onOpenWebsite: (String) -> Unit) {
    val text = when (problem) {
        SignInProblem.WrongCredentials -> stringResource(R.string.signin_error_credentials)

        SignInProblem.Blocked -> stringResource(R.string.signin_error_blocked)

        SignInProblem.EmailNotVerified -> stringResource(R.string.signin_error_not_verified)

        SignInProblem.PendingApproval -> stringResource(R.string.signin_error_pending)

        SignInProblem.SignInOnTheWebsite -> stringResource(R.string.signin_error_restricted)

        SignInProblem.DeviceName -> stringResource(R.string.signin_error_device)

        is SignInProblem.TooManyAttempts ->
            pluralStringResource(R.plurals.signin_error_attempts, problem.minutes, problem.minutes)

        is SignInProblem.Connection -> errorMessage(problem.error)

        is SignInProblem.SessionEnded -> when (problem.refusal) {
            SessionRefusal.TokenRefused -> stringResource(R.string.signin_session_ended)
            SessionRefusal.SectionMissing -> stringResource(R.string.signin_session_new_section)
            SessionRefusal.LockReset -> stringResource(R.string.signin_session_lock_reset)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
        val link = when (problem) {
            SignInProblem.EmailNotVerified, SignInProblem.PendingApproval, SignInProblem.Blocked -> PROFILE
            SignInProblem.SignInOnTheWebsite -> LOGIN
            else -> null
        }
        if (link != null) {
            TextButton(onClick = { onOpenWebsite(link) }) { Text(stringResource(R.string.signin_open_website)) }
        }
    }
}

private const val LOGIN = "/login"
private const val REGISTER = "/register"
private const val FORGOTTEN_PASSWORD = "/reset"
private const val PROFILE = "/profile"
