package org.meetagain.app.feature.townhall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.GroupLogo
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.PhotoViewer
import org.meetagain.app.core.ui.StaleNotice

/** The two halves of a group's Town Hall. */
enum class TownHallTab { Forum, Gallery }

/**
 * The Town Hall destination of the bar. With one group whose Town Hall is open, it is that group's Town Hall; with
 * several, it lists them first.
 */
@Composable
fun TownHallRoute(
    container: AppContainer,
    onOpenGroup: (Group) -> Unit,
    onOpenTopic: (String, Int) -> Unit,
    onOpenEvent: (Int) -> Unit,
    onOpenMe: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val groups by remember(container) { container.townHallRepository.groups() }.collectAsStateWithLifecycle(null)
    val only = groups?.singleOrNull()
    if (only != null) {
        GroupTownHallRoute(
            container,
            only.slug,
            only.name,
            onBack = null,
            onOpenMe = onOpenMe,
            onOpenTopic = { onOpenTopic(only.slug, it) },
            onOpenEvent = onOpenEvent,
            bottomBar = bottomBar
        )
    } else {
        TownHallGroupsScreen(groups, onOpenGroup, onOpenMe, bottomBar)
    }
}

/** The groups whose Town Hall is open to the member, when there is more than one; null while they are read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TownHallGroupsScreen(
    groups: List<Group>?,
    onOpenGroup: (Group) -> Unit,
    onOpenMe: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_town_hall)) },
                actions = { MeButton(onOpenMe) }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (groups == null) {
            LoadingState(modifier)
        } else {
            LazyColumn(modifier) {
                items(groups, key = { it.slug }) { group ->
                    ListItem(
                        leadingContent = { GroupLogo(group) },
                        headlineContent = { Text(group.name) },
                        modifier = Modifier.clickable { onOpenGroup(group) }
                    )
                }
                item(key = "end") { ListEnd(stringResource(R.string.town_hall_groups_end), Modifier.fillMaxWidth()) }
            }
        }
    }
}

/**
 * One group's Town Hall. As the bar's destination it has the person icon and no back arrow ([onBack] null); reached
 * from a group page or the list, it has a back arrow and no bar.
 */
@Composable
fun GroupTownHallRoute(
    container: AppContainer,
    slug: String,
    name: String,
    onBack: (() -> Unit)?,
    onOpenMe: (() -> Unit)?,
    onOpenTopic: (Int) -> Unit,
    onOpenEvent: (Int) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val forumModel = viewModel(key = "forum-$slug") { ForumViewModel(container.townHallRepository, slug) }
    val galleryModel = viewModel(key = "gallery-$slug") { GalleryViewModel(container.townHallRepository, slug) }
    val forum by forumModel.state.collectAsStateWithLifecycle()
    val dialog by forumModel.dialog.collectAsStateWithLifecycle()
    val gallery by galleryModel.state.collectAsStateWithLifecycle()
    val problem by galleryModel.problem.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TownHallTab.Forum) }
    var photoId by rememberSaveable { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val problemText = problem?.let { townHallProblemText(it) }
    LaunchedEffect(problem) {
        if (problemText != null) {
            snackbarHostState.showSnackbar(problemText)
            galleryModel.dismissProblem()
        }
    }
    GroupTownHallScreen(
        name = name,
        tab = tab,
        forum = forum,
        gallery = gallery,
        snackbarHostState = snackbarHostState,
        onTab = { tab = it },
        onBack = onBack,
        onOpenMe = onOpenMe,
        onNewTopic = forumModel::newTopic,
        onOpenTopic = onOpenTopic,
        onRetryForum = forumModel::load,
        onRetryGallery = galleryModel::load,
        onLoadMore = galleryModel::loadMore,
        onOpenPhoto = { photoId = it.id },
        bottomBar = bottomBar
    )
    dialog?.let {
        TitleDialogView(it, forumModel::editTitle, forumModel::confirmTitle, forumModel::dismissTitle)
    }
    val photo = (gallery as? Loadable.Loaded)?.value?.photos?.firstOrNull { it.id == photoId }
    if (photo != null) {
        GalleryPhotoViewer(photo, onOpenEvent = {
            photoId = null
            onOpenEvent(photo.eventId)
        }, onDismiss = { photoId = null })
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTownHallScreen(
    name: String,
    tab: TownHallTab,
    forum: Loadable<Forum>,
    gallery: Loadable<GalleryPage>,
    onTab: (TownHallTab) -> Unit,
    onBack: (() -> Unit)?,
    onOpenMe: (() -> Unit)?,
    onNewTopic: () -> Unit,
    onOpenTopic: (Int) -> Unit,
    onRetryForum: () -> Unit,
    onRetryGallery: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPhoto: (GalleryPhoto) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.nav_town_hall))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                            }
                        }
                    },
                    actions = {
                        if (tab == TownHallTab.Forum && forum is Loadable.Loaded) {
                            IconButton(onClick = onNewTopic) {
                                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.town_hall_new_topic))
                            }
                        }
                        onOpenMe?.let { MeButton(it) }
                    }
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == TownHallTab.Forum,
                        onClick = { onTab(TownHallTab.Forum) },
                        text = { Text(stringResource(R.string.town_hall_forum)) }
                    )
                    Tab(
                        selected = tab == TownHallTab.Gallery,
                        onClick = { onTab(TownHallTab.Gallery) },
                        text = { Text(stringResource(R.string.town_hall_gallery)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (tab) {
            TownHallTab.Forum -> ForumTab(forum, onOpenTopic, onRetryForum, modifier)
            TownHallTab.Gallery -> GalleryTab(gallery, onLoadMore, onOpenPhoto, onRetryGallery, modifier)
        }
    }
}

@Composable
private fun ForumTab(state: Loadable<Forum>, onOpenTopic: (Int) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    when (state) {
        Loadable.Loading -> LoadingState(modifier)

        is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

        is Loadable.Loaded -> LazyColumn(modifier) {
            state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
            items(state.value.topics, key = { "topic-${it.id}" }) { topic ->
                TopicRow(topic, indent = true) { onOpenTopic(topic.id) }
                HorizontalDivider()
            }
            item(key = "end") {
                val empty = state.value.topics.isEmpty()
                val end = if (empty) R.string.town_hall_forum_empty else R.string.town_hall_forum_end
                ListEnd(stringResource(end), Modifier.fillMaxWidth())
            }
        }
    }
}

/** A topic as a list shows it: its title, who started it and how many replies. Subtopics sit further in. */
@Composable
fun TopicRow(topic: Topic, indent: Boolean, onOpen: () -> Unit) {
    val replies = pluralStringResource(R.plurals.town_hall_replies, topic.replies, topic.replies)
    ListItem(
        headlineContent = { Text(topic.title) },
        supportingContent = { Text(listOf(topic.authorName, replies).joinToString(" · ")) },
        modifier = Modifier
            .clickable(onClick = onOpen)
            .padding(start = if (indent) (INDENT * (topic.depth - 1)).dp else 0.dp)
    )
}

@Composable
private fun GalleryTab(
    state: Loadable<GalleryPage>,
    onLoadMore: () -> Unit,
    onOpenPhoto: (GalleryPhoto) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        Loadable.Loading -> LoadingState(modifier)

        is Loadable.Failed -> ErrorState(state.error, onRetry, modifier)

        is Loadable.Loaded -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
        ) {
            state.stale?.let { item(key = "stale", span = { GridItemSpan(maxLineSpan) }) { StaleNotice(it, onRetry) } }
            items(state.value.photos, key = { "photo-${it.id}" }) { photo ->
                AsyncImage(
                    model = photo.thumbnailUrl,
                    contentDescription = stringResource(R.string.town_hall_photo_from, photo.eventTitle),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPhoto(photo) }
                )
            }
            item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                when {
                    state.value.hasMore -> TextButton(
                        onClick = onLoadMore,
                        enabled = !state.value.loadingMore,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.town_hall_gallery_more)) }

                    state.value.photos.isEmpty() -> ListEnd(stringResource(R.string.town_hall_gallery_empty))

                    else -> ListEnd(stringResource(R.string.town_hall_gallery_end))
                }
            }
        }
    }
}

@Composable
fun GalleryPhotoViewer(photo: GalleryPhoto, onOpenEvent: () -> Unit, onDismiss: () -> Unit) {
    val time = rememberEventTime()
    PhotoViewer(
        url = photo.url,
        title = photo.eventTitle,
        detail = photo.uploadedAt?.let { time.relativeDay(it) },
        actionLabel = stringResource(R.string.town_hall_open_event),
        onAction = onOpenEvent,
        onDismiss = onDismiss
    )
}

/** Starting a topic or a subtopic, or renaming one: one field, and the server's reason when it refuses. */
@Composable
fun TitleDialogView(dialog: TitleDialog, onEdit: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (dialog.kind) {
                        TitleKind.NewTopic -> R.string.town_hall_new_topic
                        TitleKind.NewSubtopic -> R.string.town_hall_new_subtopic
                        TitleKind.Rename -> R.string.town_hall_rename
                    }
                )
            )
        },
        text = {
            OutlinedTextField(
                value = dialog.text,
                onValueChange = onEdit,
                label = { Text(stringResource(R.string.town_hall_topic_title)) },
                enabled = !dialog.busy,
                singleLine = true,
                isError = dialog.problem != null,
                supportingText = dialog.problem?.let { { Text(townHallProblemText(it)) } }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.busy) {
                Text(
                    stringResource(
                        if (dialog.kind == TitleKind.Rename) R.string.town_hall_save else R.string.town_hall_start
                    )
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun townHallProblemText(problem: TownHallProblem): String = stringResource(
    when (problem) {
        TownHallProblem.EmptyTitle -> R.string.town_hall_empty_title
        TownHallProblem.TitleTooLong -> R.string.town_hall_title_too_long
        TownHallProblem.TooDeep -> R.string.town_hall_too_deep
        TownHallProblem.EmptyReply -> R.string.conversation_empty_comment
        TownHallProblem.ReplyTooLong -> R.string.conversation_too_long
        TownHallProblem.NotAllowed -> R.string.town_hall_not_allowed
        TownHallProblem.Gone -> R.string.town_hall_gone
        TownHallProblem.Offline -> R.string.error_offline
        TownHallProblem.Failed -> R.string.conversation_failed
    }
)

@Composable
private fun MeButton(onOpenMe: () -> Unit) {
    IconButton(onClick = onOpenMe) {
        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.me_title))
    }
}

private const val INDENT = 24
private const val GRID_COLUMNS = 3
