package org.meetagain.app.feature.event

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.meetagain.app.AppContainer
import org.meetagain.app.R
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

@Composable
fun EventRoute(container: AppContainer, id: Int, onBack: () -> Unit) {
    val viewModel = viewModel(key = "event-$id") { EventViewModel(container.publicRepository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openIntent = rememberOpenIntent()
    val openUrl = rememberOpenUrl()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noCalendarApp = stringResource(R.string.calendar_no_app)
    EventScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAddToCalendar = { details ->
            if (!openIntent(calendarInsertIntent(details))) {
                scope.launch { snackbarHostState.showSnackbar(noCalendarApp) }
            }
        },
        onOpenMap = { location -> openIntent(Intent(Intent.ACTION_VIEW, mapUri(location))) },
        onOpenWebsite = openUrl,
        snackbarHostState = snackbarHostState
    )
}

/** A search in the member's own map app; the server sends an address, not coordinates. */
private fun mapUri(location: Location): Uri = "geo:0,0?q=${Uri.encode(location.query)}".toUri()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    state: Loadable<EventDetails>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddToCalendar: (EventDetails) -> Unit,
    onOpenMap: (Location) -> Unit,
    onOpenWebsite: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
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
                EventContent(state.value, onOpenMap, onOpenWebsite, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EventContent(
    details: EventDetails,
    onOpenMap: (Location) -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier
) {
    val event = details.event
    val time = rememberEventTime()
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
            details.description?.let { description ->
                val text = remember(description) { AnnotatedString.fromHtml(description.replace("\n", "<br>")) }
                Text(text, style = MaterialTheme.typography.bodyLarge)
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
