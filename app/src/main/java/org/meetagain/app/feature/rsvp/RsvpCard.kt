package org.meetagain.app.feature.rsvp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.time.Clock
import org.meetagain.app.R
import org.meetagain.app.core.data.Event

/**
 * The member's own answer to a meeting. Saying yes is offered only while the server would take it: not on a meeting
 * that was called off or has begun, and not in a group the member is not part of. Taking a yes back is always
 * offered, because the server always allows that.
 */
@Composable
fun RsvpCard(
    event: Event,
    onAnswer: (going: Boolean, guests: Int) -> Unit,
    modifier: Modifier = Modifier,
    canJoin: Boolean = true,
    clock: Clock = Clock.systemUTC()
) {
    val mine = event.mine ?: return
    val started = !event.start.isAfter(clock.instant())
    val mayJoin = canJoin && !event.canceled && !started
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.rsvp_question), style = MaterialTheme.typography.titleMedium)
            if (mayJoin || mine.going) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mine.going,
                        onClick = { onAnswer(true, mine.guests) },
                        enabled = mayJoin,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.rsvp_going))
                    }
                    SegmentedButton(
                        selected = !mine.going,
                        onClick = { onAnswer(false, 0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.rsvp_not_going))
                    }
                }
            }
            when {
                event.canceled -> Note(stringResource(R.string.rsvp_canceled))
                started -> Note(stringResource(R.string.rsvp_started))
                !canJoin -> Note(stringResource(R.string.rsvp_not_a_member))
            }
            if (mine.going && mayJoin) {
                Guests(mine.guests, onChange = { onAnswer(true, it) })
            }
        }
    }
}

@Composable
private fun Guests(guests: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.rsvp_guests), modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(guests - 1) }, enabled = guests > 0) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.rsvp_guest_fewer))
        }
        Text(
            text = pluralStringResource(R.plurals.rsvp_guest_count, guests, guests),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                stateDescription = guests.toString()
            }
        )
        IconButton(onClick = { onChange(guests + 1) }, enabled = guests < MAX_GUESTS) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.rsvp_guest_more))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Why the server did not take an answer, in the member's language. */
@Composable
fun rsvpRefusalMessage(reason: RsvpRefusal): String = stringResource(
    when (reason) {
        RsvpRefusal.NotAMember -> R.string.rsvp_not_a_member
        RsvpRefusal.Canceled -> R.string.rsvp_canceled
        RsvpRefusal.Started -> R.string.rsvp_started
        RsvpRefusal.Offline -> R.string.error_offline
        RsvpRefusal.Failed -> R.string.rsvp_failed
    }
)
