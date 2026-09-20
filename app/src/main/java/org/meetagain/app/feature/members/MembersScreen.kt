package org.meetagain.app.feature.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.MemberSummary
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice

@Composable
fun GroupMembersRoute(container: AppContainer, slug: String, onBack: () -> Unit, onOpenMember: (Int) -> Unit) {
    val repository = container.memberRepository
    val viewModel = viewModel(key = "group-members-$slug") {
        MembersViewModel(
            stored = repository.groupMembers(slug),
            refresh = { repository.refreshGroupMembers(slug) },
            more = { offset -> repository.moreGroupMembers(slug, offset) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MembersScreen(
        state = state,
        title = stringResource(R.string.group_members_title),
        empty = stringResource(R.string.group_members_empty),
        end = stringResource(R.string.group_members_end),
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenMember = onOpenMember,
        onLoadMore = viewModel::loadMore
    )
}

/** The only honest way back to someone after blocking them, which is why it is a screen and not a dead end. */
@Composable
fun BlockedRoute(container: AppContainer, onBack: () -> Unit, onOpenMember: (Int) -> Unit) {
    val repository = container.memberRepository
    val viewModel = viewModel(key = "blocked") {
        MembersViewModel(stored = repository.blocked(), refresh = { repository.refreshBlocked() })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MembersScreen(
        state = state,
        title = stringResource(R.string.me_blocked),
        empty = stringResource(R.string.blocked_empty),
        end = stringResource(R.string.blocked_end),
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenMember = onOpenMember,
        onLoadMore = viewModel::loadMore
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    state: Loadable<MemberList>,
    title: String,
    empty: String,
    end: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMember: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                }
            )
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
                items(state.value.people, key = { "member-${it.id}" }) { member ->
                    MemberRow(member) { onOpenMember(member.id) }
                }
                item(key = "end") {
                    if (state.value.hasMore) {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !state.value.loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.messages_more))
                        }
                    } else {
                        ListEnd(
                            if (state.value.people.isEmpty()) empty else end,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: MemberSummary, onOpen: () -> Unit) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_person),
                error = painterResource(R.drawable.ic_person),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = { Text(member.name) },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}
