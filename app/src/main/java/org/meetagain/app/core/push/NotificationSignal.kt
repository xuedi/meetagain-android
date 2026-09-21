package org.meetagain.app.core.push

import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import org.meetagain.app.core.auth.isInsufficientScope
import org.meetagain.app.core.auth.isInvalidToken
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.network.ApiClient
import org.meetagain.app.core.network.ApiResult

/**
 * Whether anything changed for the member, asked with a token that can ask nothing else. It exists only while the app
 * lock is on, for the timer that stands in for push on a phone without a distributor.
 *
 * [session] carries the member's own token and is only used to ask for the signal token; [bare] carries no token of
 * its own, so the one call it makes goes out with the signal token and nothing else.
 */
class NotificationSignal(
    private val session: ApiClient,
    private val bare: ApiClient,
    private val store: SignalStore,
    private val clock: Clock = Clock.systemUTC()
) {
    /**
     * When the lock is turned on, and at every unlock: a token when there is none, or when the one there runs out
     * within a month. A server without the signal leaves the phone without one, and the timer then stays quiet.
     */
    suspend fun ensureToken() {
        val current = store.token()
        if (current != null && current.expiresAt.isAfter(clock.instant().plus(RENEW_BEFORE))) return
        val issued = (session.issueSignalToken() as? ApiResult.Success)?.value ?: return
        val expires = runCatching { OffsetDateTime.parse(issued.expiresAt).toInstant() }.getOrNull() ?: return
        store.writeToken(SignalToken(issued.token, expires))
        // The first answer only sets the mark, so turning the lock on is not announced as news.
        if (store.lastState() == null) check()
    }

    suspend fun check(): SignalCheck {
        val token = store.token() ?: return SignalCheck.NoToken
        return when (val result = bare.signal(token.value)) {
            is ApiResult.Success -> {
                val last = store.lastState()
                store.writeLastState(result.value.state)
                if (last == null || last == result.value.state) SignalCheck.Same else SignalCheck.Changed
            }

            is ApiResult.Failure ->
                // A refused signal token never touches the session: it goes, and the next unlock asks for another.
                if (result.error.isInvalidToken || result.error.isInsufficientScope) {
                    store.dropToken()
                    SignalCheck.Refused
                } else {
                    SignalCheck.Failed
                }
        }
    }

    /** What the timer goes by while the member's own settings are out of reach. */
    suspend fun remember(settings: NotificationSettings?) {
        store.writeSettings(
            PushSnapshot(
                wanted = settings == null || (settings.master && settings.other.push.values.any { it }),
                quietHours = settings?.other?.quietHours
            )
        )
    }

    /** The token revokes itself where the server can be reached; the phone forgets it either way. */
    suspend fun discard() {
        store.token()?.let { bare.logout(it.value) }
        store.clear()
    }

    private companion object {
        val RENEW_BEFORE: Duration = Duration.ofDays(30)
    }
}

enum class SignalCheck {
    Changed,
    Same,
    NoToken,
    Refused,
    Failed
}
