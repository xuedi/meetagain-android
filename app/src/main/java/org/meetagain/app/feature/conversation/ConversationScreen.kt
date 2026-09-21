package org.meetagain.app.feature.conversation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Photo
import org.meetagain.app.core.ui.CommentComposer
import org.meetagain.app.core.ui.CommentRow
import org.meetagain.app.core.ui.Comments
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.MAX_PHOTO_BYTES
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.asUpload
import org.meetagain.app.core.ui.rememberCameraTarget

@Composable
fun ConversationRoute(container: AppContainer, id: Int, onBack: () -> Unit) {
    val viewModel = viewModel(key = "conversation-$id") { ConversationViewModel(container.memberRepository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraTarget = rememberCameraTarget()
    var pendingPhoto by remember { mutableStateOf<android.net.Uri?>(null) }

    val send: (android.net.Uri) -> Unit = { uri ->
        val upload = uri.asUpload(context)
        when {
            upload == null -> viewModel.photoTooBig()
            upload.length > MAX_PHOTO_BYTES -> viewModel.photoTooBig()
            else -> viewModel.addPhoto(upload)
        }
    }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(send) }
    val capture = rememberLauncherForActivityResult(TakePicture()) { taken ->
        if (taken) pendingPhoto?.let(send)
    }

    ProblemMessage(problem, snackbarHostState, viewModel::dismissProblem)
    ConversationScreen(
        state = state,
        photos = photos,
        draft = draft,
        busy = busy,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onDraft = viewModel::draft,
        onSend = viewModel::send,
        onLoadOlder = viewModel::loadOlder,
        onDeleteComment = viewModel::deleteComment,
        onUndoDeleteComment = viewModel::undoDeleteComment,
        onDeletePhoto = viewModel::deletePhoto,
        onUndoDeletePhoto = viewModel::undoDeletePhoto,
        onPickPhoto = { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val (uri, _) = cameraTarget()
            pendingPhoto = uri
            capture.launch(uri)
        }
    )
}

@Composable
private fun ProblemMessage(problem: ConversationProblem?, host: SnackbarHostState, onDismiss: () -> Unit) {
    val text = problem?.let { problemText(it) }
    LaunchedEffect(problem) {
        if (text != null) {
            host.showSnackbar(text)
            onDismiss()
        }
    }
}

@Composable
private fun problemText(problem: ConversationProblem): String = stringResource(
    when (problem) {
        ConversationProblem.NotAMember -> R.string.conversation_members_only
        ConversationProblem.TooLong -> R.string.conversation_too_long
        ConversationProblem.Empty -> R.string.conversation_empty_comment
        ConversationProblem.PhotoRejected -> R.string.conversation_photo_rejected
        ConversationProblem.Offline -> R.string.error_offline
        ConversationProblem.Failed -> R.string.conversation_failed
    }
)

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: Loadable<Comments>,
    photos: List<Photo>,
    draft: String,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onDeleteComment: (Int) -> Unit,
    onUndoDeleteComment: (Int) -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onUndoDeletePhoto: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val undo = stringResource(R.string.undo)
    val commentRemoved = stringResource(R.string.conversation_comment_removed)
    val photoRemoved = stringResource(R.string.conversation_photo_removed)
    val scope = rememberCoroutineScope()
    // The delete waits for this message to go before it reaches the server, so undo costs nothing.
    val showUndo: (String, () -> Unit) -> Unit = { text, onUndo ->
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            if (snackbarHostState.showSnackbar(text, actionLabel = undo) == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onPickPhoto, enabled = !busy) {
                        Icon(
                            painterResource(R.drawable.ic_photo_library),
                            stringResource(R.string.conversation_add_photo)
                        )
                    }
                    IconButton(onClick = onTakePhoto, enabled = !busy) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera),
                            stringResource(R.string.conversation_take_photo)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CommentComposer(draft, busy, onDraft, onSend)
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
                if (photos.isNotEmpty()) {
                    item(key = "photos") {
                        Photos(photos, photoRemoved, showUndo, onDeletePhoto, onUndoDeletePhoto)
                    }
                }
                items(state.value.visible, key = { "comment-${it.id}" }) { comment ->
                    CommentRow(
                        comment = comment,
                        onDelete = {
                            onDeleteComment(comment.id)
                            showUndo(commentRemoved) { onUndoDeleteComment(comment.id) }
                        }
                    )
                    HorizontalDivider()
                }
                item(key = "end") {
                    if (state.value.hasOlder) {
                        TextButton(
                            onClick = onLoadOlder,
                            enabled = !state.value.loadingOlder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.conversation_older))
                        }
                    } else {
                        ListEnd(
                            if (state.value.visible.isEmpty()) {
                                stringResource(R.string.conversation_empty)
                            } else {
                                stringResource(R.string.conversation_end)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Photos(
    photos: List<Photo>,
    removed: String,
    showUndo: (String, () -> Unit) -> Unit,
    onDelete: (Int) -> Unit,
    onUndo: (Int) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.event_photos),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .semantics { heading() }
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photos, key = { "photo-${it.id}" }) { photo ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = photo.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    if (photo.mine) {
                        IconButton(
                            onClick = {
                                onDelete(photo.id)
                                showUndo(removed) { onUndo(photo.id) }
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.conversation_delete))
                        }
                    }
                }
            }
        }
    }
}
