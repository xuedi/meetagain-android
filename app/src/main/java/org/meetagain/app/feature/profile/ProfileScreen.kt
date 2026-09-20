package org.meetagain.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.i18n.websiteUrl
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.MAX_AVATAR_BYTES
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.asUpload
import org.meetagain.app.core.ui.rememberCameraTarget
import org.meetagain.app.core.ui.rememberOpenUrl

@Composable
fun ProfileRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel = viewModel { ProfileViewModel(container.memberRepository, container.auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraTarget = rememberCameraTarget()
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val send: (android.net.Uri) -> Unit = { uri ->
        val upload = uri.asUpload(context)
        if (upload == null || upload.length > MAX_AVATAR_BYTES) {
            viewModel.avatarRejected()
        } else {
            viewModel.uploadAvatar(upload)
        }
    }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(send) }
    val capture = rememberLauncherForActivityResult(TakePicture()) { taken -> if (taken) pending?.let(send) }
    val text = message?.let { profileMessageText(it) }
    LaunchedEffect(message) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.dismissMessage()
        }
    }
    ProfileScreen(
        state = state,
        edit = edit,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onName = viewModel::name,
        onBio = viewModel::bio,
        onLanguage = viewModel::language,
        onPublic = viewModel::public,
        onSave = viewModel::save,
        onPickAvatar = { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onTakeAvatar = {
            val (uri, _) = cameraTarget()
            pending = uri
            capture.launch(uri)
        },
        onOpenWebsite = { openUrl(websiteUrl(container.appInfo.baseUrl, it)) }
    )
}

@Composable
private fun profileMessageText(message: ProfileMessage): String = stringResource(
    when (message) {
        ProfileMessage.Saved -> R.string.profile_saved
        ProfileMessage.NameRequired -> R.string.profile_name_required
        ProfileMessage.NameTooLong -> R.string.profile_name_too_long
        ProfileMessage.LanguageNotAllowed -> R.string.profile_language_not_allowed
        ProfileMessage.AvatarRejected -> R.string.profile_avatar_rejected
        ProfileMessage.Offline -> R.string.error_offline
        ProfileMessage.Failed -> R.string.profile_failed
    }
)

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: Loadable<ProfileEdit>,
    edit: ProfileEdit?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onName: (String) -> Unit,
    onBio: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onPublic: (Boolean) -> Unit,
    onSave: () -> Unit,
    onPickAvatar: () -> Unit,
    onTakeAvatar: () -> Unit,
    onOpenWebsite: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.me_profile)) },
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
                val shown = edit ?: state.value
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AsyncImage(
                            model = shown.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.ic_person),
                            error = painterResource(R.drawable.ic_person),
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )
                        IconButton(onClick = onPickAvatar) {
                            Icon(
                                painterResource(R.drawable.ic_photo_library),
                                stringResource(R.string.profile_pick_avatar)
                            )
                        }
                        IconButton(onClick = onTakeAvatar) {
                            Icon(
                                painterResource(R.drawable.ic_photo_camera),
                                stringResource(R.string.profile_take_avatar)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = shown.name,
                        onValueChange = onName,
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shown.bio,
                        onValueChange = onBio,
                        label = { Text(stringResource(R.string.profile_bio)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Languages(shown.language, onLanguage)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_public))
                            Text(
                                text = stringResource(R.string.profile_public_explained),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = shown.public, onCheckedChange = onPublic)
                    }
                    Button(onClick = onSave, enabled = edit != null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_save))
                    }
                    Text(
                        text = stringResource(R.string.profile_on_the_website),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onOpenWebsite(PASSWORD) }) {
                        Text(stringResource(R.string.profile_change_password))
                    }
                    TextButton(onClick = { onOpenWebsite(TOKENS) }) {
                        Text(stringResource(R.string.profile_devices))
                    }
                    TextButton(onClick = { onOpenWebsite(PROFILE) }) {
                        Text(stringResource(R.string.me_delete_account))
                    }
                }
            }
        }
    }
}

/** The languages MeetAgain speaks, named in themselves so anyone can find their own. */
@Composable
private fun Languages(current: String, onLanguage: (String) -> Unit) {
    Column {
        Text(stringResource(R.string.about_language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LANGUAGES.forEach { (code, name) ->
                TextButton(onClick = { onLanguage(code) }, enabled = code != current) {
                    Text(stringResource(name))
                }
            }
        }
    }
}

private val LANGUAGES = listOf(
    "en" to R.string.language_en,
    "de" to R.string.language_de,
    "zh" to R.string.language_zh,
    "fr" to R.string.language_fr,
    "es" to R.string.language_es
)

private const val PROFILE = "/profile"
private const val PASSWORD = "/profile"
private const val TOKENS = "/profile/access-tokens"
