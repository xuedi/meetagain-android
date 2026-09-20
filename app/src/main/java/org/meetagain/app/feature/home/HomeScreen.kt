package org.meetagain.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Clock
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.format.EventTime
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.ListEnd
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.feature.event.eventItems
import org.meetagain.app.feature.event.kindLabel
import org.meetagain.app.feature.rsvp.RsvpCard
import org.meetagain.app.feature.rsvp.RsvpMessage
import org.meetagain.app.feature.rsvp.rsvpRefusalMessage

@Composable
fun HomeRoute(
    container: AppContainer,
    onOpenEvent: (Event) -> Unit,
    onOpenMe: () -> Unit,
    onOpenMyGroups: () -> Unit,
    onLookAround: () -> Unit,
    clock: Clock = Clock.systemUTC()
) {
    val viewModel = viewModel { HomeViewModel(container.memberRepository, clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val joined by viewModel.joined.collectAsStateWithLifecycle()
    val message by viewModel.rsvp.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RsvpSnackbar(message, snackbarHostState, viewModel)
    HomeScreen(
        state = state,
        joined = joined,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::load,
        onOpenEvent = onOpenEvent,
        onOpenMe = onOpenMe,
        onOpenMyGroups = onOpenMyGroups,
        onLookAround = onLookAround,
        onAnswer = { event, going, guests -> viewModel.rsvp.answer(event, going, guests) },
        clock = clock
    )
}

/** Says what happened, and offers the way back while the message is up. */
@Composable
private fun RsvpSnackbar(message: RsvpMessage?, host: SnackbarHostState, viewModel: HomeViewModel) {
    val undo = stringResource(R.string.undo)
    val text = rsvpMessageText(message)
    LaunchedEffect(message) {
        val saved = message as? RsvpMessage.Saved
        if (text == null) return@LaunchedEffect
        val result = host.showSnackbar(text, actionLabel = undo.takeIf { saved?.undoTo != null })
        if (result == SnackbarResult.ActionPerformed && saved != null) {
            viewModel.rsvp.undo(saved.eventId, saved.undoTo)
        } else {
            viewModel.rsvp.dismiss()
        }
    }
}

@Composable
fun rsvpMessageText(message: RsvpMessage?): String? = when (message) {
    null -> null

    is RsvpMessage.Saved ->
        if (message.going) stringResource(R.string.rsvp_saved_going) else stringResource(R.string.rsvp_saved_not_going)

    is RsvpMessage.Refused -> rsvpRefusalMessage(message.reason)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: Loadable<Home>,
    joined: Set<String>?,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    onOpenMe: () -> Unit,
    onOpenMyGroups: () -> Unit,
    onLookAround: () -> Unit,
    onAnswer: (Event, Boolean, Int) -> Unit,
    clock: Clock = Clock.systemUTC()
) {
    val time = rememberEventTime()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenMe) {
                        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.me_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (state) {
            Loadable.Loading -> LoadingState(Modifier.padding(padding))

            is Loadable.Failed -> ErrorState(state.error, onRetry, Modifier.padding(padding))

            is Loadable.Loaded -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                state.stale?.let { item(key = "stale") { StaleNotice(it, onRetry) } }
                val home = state.value
                if (home.next == null) {
                    item(key = "empty") {
                        Empty(onOpenMyGroups = onOpenMyGroups, onLookAround = onLookAround)
                    }
                } else {
                    item(key = "next") {
                        NextMeeting(
                            event = home.next,
                            time = time,
                            joined = joined,
                            onOpen = { onOpenEvent(home.next) },
                            onAnswer = { going, guests -> onAnswer(home.next, going, guests) },
                            clock = clock
                        )
                    }
                    if (home.later.isNotEmpty()) {
                        item(key = "later") { SectionHeading(stringResource(R.string.home_later)) }
                        eventItems(home.later, time, onOpenEvent)
                    }
                    item(key = "end") {
                        Column {
                            ListEnd(
                                if (home.beyondWindow) {
                                    stringResource(R.string.home_end_more)
                                } else {
                                    stringResource(R.string.home_end)
                                },
                                Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = onLookAround, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.home_look_around))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The one meeting that matters most: when and where it is, who is coming, and the member's own answer. */
@Composable
private fun NextMeeting(
    event: Event,
    time: EventTime,
    joined: Set<String>?,
    onOpen: () -> Unit,
    onAnswer: (Boolean, Int) -> Unit,
    clock: Clock
) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            Column(Modifier.clickable(onClick = onOpen)) {
                Text(time.date(event.start), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time.timeRange(event.start, event.end), style = MaterialTheme.typography.titleSmall)
                    event.kind?.let { Text(kindLabel(it), style = MaterialTheme.typography.titleSmall) }
                }
                Text(event.title, style = MaterialTheme.typography.headlineSmall)
                event.group?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (event.canceled) {
                    Text(stringResource(R.string.event_canceled), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = pluralStringResource(R.plurals.event_going, event.going, event.going),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RsvpCard(
                event = event,
                onAnswer = onAnswer,
                canJoin = joined == null || event.group == null || event.group.slug in joined,
                clock = clock
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun Empty(onOpenMyGroups: () -> Unit, onLookAround: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ListEnd(stringResource(R.string.home_empty), Modifier.fillMaxWidth())
        TextButton(onClick = onOpenMyGroups, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.me_my_groups))
        }
        TextButton(onClick = onLookAround, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_look_around))
        }
    }
}
