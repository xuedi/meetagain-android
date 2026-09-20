package org.meetagain.app.feature.rsvp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Rsvp
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/** Why the server did not take the answer. */
enum class RsvpRefusal { NotAMember, Canceled, Started, Offline, Failed }

/** What to say after an answer was sent, and what it would take to put it back. */
sealed interface RsvpMessage {
    /** [undoTo] is what the member answered before, or null when there is nothing to go back to. */
    data class Saved(val eventId: Int, val going: Boolean, val undoTo: Rsvp?) : RsvpMessage

    data class Refused(val reason: RsvpRefusal) : RsvpMessage
}

/**
 * Saying yes or no to a meeting, shared by the screens that offer it. The answer goes to the server at once and the
 * screen says so, with a way back while the message is up.
 */
class RsvpActions(private val scope: CoroutineScope, private val repository: MemberRepository) {
    private val current = MutableStateFlow<RsvpMessage?>(null)

    val message: StateFlow<RsvpMessage?> = current.asStateFlow()

    fun answer(event: Event, going: Boolean, guests: Int = event.mine?.guests ?: 0) {
        val before = event.mine
        send(event.id, going, guests) { RsvpMessage.Saved(event.id, going, before) }
    }

    /** Puts back what the member answered before, without another message. */
    fun undo(eventId: Int, to: Rsvp?) {
        current.value = null
        send(eventId, to?.going ?: false, to?.guests ?: 0) { null }
    }

    fun dismiss() {
        current.value = null
    }

    private fun send(eventId: Int, going: Boolean, guests: Int, message: () -> RsvpMessage?) {
        scope.launch {
            current.value = when (val result = repository.rsvp(eventId, going, guests)) {
                is ApiResult.Success -> message()
                is ApiResult.Failure -> RsvpMessage.Refused(refusalOf(result.error))
            }
        }
    }

    private fun refusalOf(error: ApiError): RsvpRefusal = when {
        error is ApiError.Offline || error is ApiError.Timeout -> RsvpRefusal.Offline
        error !is ApiError.Http -> RsvpRefusal.Failed
        error.code == NOT_A_MEMBER -> RsvpRefusal.NotAMember
        error.code == EVENT_CANCELED -> RsvpRefusal.Canceled
        error.code == EVENT_STARTED -> RsvpRefusal.Started
        else -> RsvpRefusal.Failed
    }

    private companion object {
        const val NOT_A_MEMBER = "not_a_member"
        const val EVENT_CANCELED = "event_canceled"
        const val EVENT_STARTED = "event_started"
    }
}

/** The most guests the server takes with an RSVP. */
const val MAX_GUESTS = 5
