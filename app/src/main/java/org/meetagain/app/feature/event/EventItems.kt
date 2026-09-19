package org.meetagain.app.feature.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.meetagain.app.R
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.EventKind
import org.meetagain.app.core.format.EventTime

/** Events in time order under one heading per day. */
fun LazyListScope.eventItems(events: List<Event>, time: EventTime, onOpen: (Event) -> Unit) {
    events.groupBy { time.day(it.start) }.forEach { (day, onDay) ->
        item(key = "day-$day") { DayHeader(time.date(onDay.first().start)) }
        items(onDay, key = { "event-${it.id}" }) { event -> EventRow(event, time, onOpen = { onOpen(event) }) }
    }
}

@Composable
private fun DayHeader(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/** One stop for a screen reader, with the full sentence: day, time, title, kind and how many are going. */
@Composable
fun EventRow(event: Event, time: EventTime, onOpen: () -> Unit) {
    val resources = LocalResources.current
    val going = pluralStringResource(R.plurals.event_going, event.going, event.going)
    val kind = event.kind?.let { kindLabel(it) }
    val sentence = listOfNotNull(
        time.date(event.start),
        time.timeRange(event.start, event.end),
        event.title,
        kind,
        going
    )
        .joinToString(resources.getString(R.string.list_separator))
    ListItem(
        overlineContent = { Text(listOfNotNull(time.timeRange(event.start, event.end), kind).joinToString(" · ")) },
        headlineContent = { Text(event.title) },
        supportingContent = {
            Column {
                event.teaser?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(going, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = sentence
                role = Role.Button
                onClick {
                    onOpen()
                    true
                }
            }
    )
}

@Composable
fun kindLabel(kind: EventKind): String = when (kind) {
    EventKind.Outdoor -> stringResource(R.string.event_kind_outdoor)
    EventKind.Dinner -> stringResource(R.string.event_kind_dinner)
}
