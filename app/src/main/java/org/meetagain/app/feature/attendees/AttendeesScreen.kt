package org.meetagain.app.feature.attendees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Attendee
import org.meetagain.app.core.data.Attendees
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.StoredContent

class AttendeesViewModel(repository: MemberRepository, id: Int) : ViewModel() {
    private val content =
        StoredContent(viewModelScope, repository.attendees(id)) { repository.refreshAttendees(id) }

    val state: StateFlow<Loadable<Attendees>> = content.state

    fun load() = content.reload()
}

@Composable
fun AttendeesRoute(container: AppContainer, id: Int, onBack: () -> Unit, onOpenMember: (Int) -> Unit) {
    val viewModel = viewModel(key = "attendees-$id") { AttendeesViewModel(container.memberRepository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AttendeesScreen(state, onBack = onBack, onRetry = viewModel::load, onOpenMember = onOpenMember)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendeesScreen(
    state: Loadable<Attendees>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMember: (Int) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attendees_title)) },
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
                items(state.value.people, key = { "person-${it.id}" }) { attendee ->
                    AttendeeRow(attendee) { onOpenMember(attendee.id) }
                }
                item(key = "end") { End(state.value) }
            }
        }
    }
}

/**
 * One stop for a screen reader: the name, the guests they bring, and whether it is the member themselves. A row
 * opens that member's page; the member's own row opens nothing, and says so.
 */
@Composable
private fun AttendeeRow(attendee: Attendee, onOpen: () -> Unit) {
    val resources = LocalResources.current
    val guests = if (attendee.guests > 0) {
        resources.getQuantityString(R.plurals.attendees_guests, attendee.guests, attendee.guests)
    } else {
        null
    }
    val you = stringResource(R.string.attendees_you)
    val sentence = listOfNotNull(attendee.name, guests, you.takeIf { attendee.mine })
        .joinToString(resources.getString(R.string.list_separator))
    ListItem(
        leadingContent = {
            AsyncImage(
                model = attendee.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_person),
                error = painterResource(R.drawable.ic_person),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = { Text(if (attendee.mine) "${attendee.name} ($you)" else attendee.name) },
        supportingContent = guests?.let { { Text(it) } },
        modifier = Modifier
            .let { if (attendee.mine) it else it.clickable(onClick = onOpen) }
            .semantics(mergeDescendants = true) { contentDescription = sentence }
    )
}

@Composable
private fun End(attendees: Attendees) {
    val text = if (attendees.externalCount > 0) {
        pluralStringResource(R.plurals.attendees_external, attendees.externalCount, attendees.externalCount)
    } else if (attendees.people.isEmpty()) {
        stringResource(R.string.attendees_empty)
    } else {
        pluralStringResource(R.plurals.attendees_total, attendees.total, attendees.total)
    }
    ListEnd(text)
}
