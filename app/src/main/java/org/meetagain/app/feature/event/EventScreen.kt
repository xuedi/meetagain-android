package org.meetagain.app.feature.event

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.time.Clock
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
import org.meetagain.app.core.auth.SessionState
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.Location
import org.meetagain.app.core.format.rememberEventTime
import org.meetagain.app.core.ui.ErrorState
import org.meetagain.app.core.ui.Loadable
import org.meetagain.app.core.ui.LoadingState
import org.meetagain.app.core.ui.StaleNotice
import org.meetagain.app.core.ui.calendarInsertIntent
import org.meetagain.app.core.ui.rememberOpenIntent
import org.meetagain.app.core.ui.rememberOpenUrl
import org.meetagain.app.feature.home.rsvpMessageText
import org.meetagain.app.feature.rsvp.RsvpCard
import org.meetagain.app.feature.rsvp.RsvpMessage

@Composable
fun EventRoute(
    container: AppContainer,
    id: Int,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAttendees: () -> Unit,
    onOpenConversation: () -> Unit,
    clock: Clock = Clock.systemUTC()
) {
    val session by container.auth.state.collectAsStateWithLifecycle()
    val signedIn = session is SessionState.SignedIn
    val viewModel = viewModel(key = "event-$id") {
        EventViewModel(container.publicRepository, container.memberRepository, signedIn, id)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val occurrences by viewModel.occurrences.collectAsStateWithLifecycle()
    val joined by viewModel.joined.collectAsStateWithLifecycle()
    val message by viewModel.rsvp.message.collectAsStateWithLifecycle()
    val openIntent = rememberOpenIntent()
    val openUrl = rememberOpenUrl()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noCalendarApp = stringResource(R.string.calendar_no_app)
    val undo = stringResource(R.string.undo)
    val text = rsvpMessageText(message)
    LaunchedEffect(message) {
        val saved = message as? RsvpMessage.Saved
        if (text == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(text, actionLabel = undo.takeIf { saved?.undoTo != null })
        if (result == SnackbarResult.ActionPerformed && saved != null) {
            viewModel.rsvp.undo(saved.eventId, saved.undoTo)
        } else {
            viewModel.rsvp.dismiss()
        }
    }
    EventScreen(
        state = state,
        occurrences = occurrences,
        joined = joined,
        signedIn = viewModel.canAnswer,
        onBack = onBack,
        onRetry = viewModel::load,
        onAddToCalendar = { details ->
            if (!openIntent(calendarInsertIntent(details))) {
                scope.launch { snackbarHostState.showSnackbar(noCalendarApp) }
            }
        },
        onOpenMap = { location -> openIntent(Intent(Intent.ACTION_VIEW, mapUri(location))) },
        onOpenWebsite = openUrl,
        onOpenGroup = onOpenGroup,
        onOpenAttendees = onOpenAttendees,
        onOpenConversation = onOpenConversation,
        onAnswer = { event, going, guests -> viewModel.rsvp.answer(event, going, guests) },
        snackbarHostState = snackbarHostState,
        clock = clock
    )
}

/** A search in the member's own map app; the server sends an address, not coordinates. */
private fun mapUri(location: Location): Uri = "geo:0,0?q=${Uri.encode(location.query)}".toUri()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    state: Loadable<EventDetails>,
    occurrences: List<Event> = emptyList(),
    joined: Set<String>? = null,
    signedIn: Boolean = false,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddToCalendar: (EventDetails) -> Unit,
    onOpenMap: (Location) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenGroup: (String) -> Unit = {},
    onOpenAttendees: () -> Unit = {},
    onOpenConversation: () -> Unit = {},
    onAnswer: (Event, Boolean, Int) -> Unit = { _, _, _ -> },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    clock: Clock = Clock.systemUTC()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state is Loadable.Loaded) {
                        IconButton(onClick = { onAddToCalendar(state.value) }) {
                            Icon(painterResource(R.drawable.ic_calendar_add_on), stringResource(R.string.calendar_add))
                        }
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

            is Loadable.Loaded -> Column(modifier) {
                state.stale?.let { StaleNotice(it, onRetry = onRetry) }
                EventContent(
                    details = state.value,
                    occurrences = occurrences,
                    joined = joined,
                    signedIn = signedIn,
                    onOpenMap = onOpenMap,
                    onOpenWebsite = onOpenWebsite,
                    onOpenGroup = onOpenGroup,
                    onOpenAttendees = onOpenAttendees,
                    onOpenConversation = onOpenConversation,
                    onAnswer = onAnswer,
                    clock = clock,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EventContent(
    details: EventDetails,
    occurrences: List<Event>,
    joined: Set<String>?,
    signedIn: Boolean,
    onOpenMap: (Location) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenAttendees: () -> Unit,
    onOpenConversation: () -> Unit,
    onAnswer: (Event, Boolean, Int) -> Unit,
    clock: Clock,
    modifier: Modifier
) {
    val event = details.event
    val time = rememberEventTime()
    val canJoin = joined == null || event.group == null || event.group.slug in joined
    Column(modifier.verticalScroll(rememberScrollState())) {
        event.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (event.canceled) {
                Text(
                    text = stringResource(R.string.event_canceled),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            event.kind?.let {
                Text(
                    kindLabel(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            event.group?.let { group ->
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenGroup(group.slug) }
                )
            }
            Column {
                Text(time.date(event.start), style = MaterialTheme.typography.titleMedium)
                Text(time.timeRange(event.start, event.end), style = MaterialTheme.typography.bodyLarge)
            }
            details.location?.let { location ->
                Column {
                    Text(location.query, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onOpenMap(location) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.event_open_map))
                    }
                }
            }
            Text(
                text = pluralStringResource(R.plurals.event_going, event.going, event.going),
                style = MaterialTheme.typography.bodyLarge
            )
            details.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (signedIn) {
                RsvpCard(event = event, onAnswer = { going, guests ->
                    onAnswer(event, going, guests)
                }, canJoin = canJoin, clock = clock)
                if (occurrences.isNotEmpty()) {
                    MoreDates(occurrences, canJoin, onAnswer, clock)
                }
                TextButton(onClick = onOpenAttendees, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.attendees_title))
                }
                TextButton(onClick = onOpenConversation, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.conversation_title))
                }
            }
        }
        if (details.photoUrls.isNotEmpty()) {
            Text(
                text = stringResource(R.string.event_photos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() }
            )
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(details.photoUrls) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(120.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { onOpenWebsite(event.webUrl) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) { Text(stringResource(R.string.event_open_website)) }
    }
}

/** The other dates of a meeting that repeats: one switch each, so the member answers them without leaving. */
@Composable
private fun MoreDates(
    occurrences: List<Event>,
    canJoin: Boolean,
    onAnswer: (Event, Boolean, Int) -> Unit,
    clock: Clock
) {
    val time = rememberEventTime()
    Column {
        Text(
            text = stringResource(R.string.event_more_dates),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        occurrences.forEach { event ->
            val going = event.mine?.going == true
            val mayChange = canJoin && !event.canceled && event.start.isAfter(clock.instant())
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(time.date(event.start))
                    Text(
                        text = time.timeRange(event.start, event.end),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = going,
                    onCheckedChange = { onAnswer(event, it, 0) },
                    enabled = mayChange || going
                )
            }
        }
    }
}
