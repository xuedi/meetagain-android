package org.meetagain.app.core.push

import android.content.Context
import java.time.Clock
import org.meetagain.app.R

/** What a ping or a timer run turns into. */
interface PushRun {
    suspend fun run(fromTimer: Boolean)
}

/**
 * The same, while the app lock is on: the member's token is sealed, so nothing about the news can be fetched. The
 * phone says only that something is new, always in one notification that a later one replaces.
 */
class LockedPushWork(
    private val context: Context,
    private val signal: NotificationSignal,
    private val store: SignalStore,
    private val notifier: Notifier,
    private val clock: Clock = Clock.systemUTC()
) : PushRun {
    override suspend fun run(fromTimer: Boolean) {
        if (!notifier.canPost()) return
        // Asked on a ping too, to move the mark: the timer must not announce what the ping already did.
        val check = signal.check()
        if (fromTimer) {
            if (check != SignalCheck.Changed) return
            val settings = store.settings()
            if (!settings.wanted || !QuietHoursGate.allows(settings.quietHours, urgent = false, clock)) return
        }
        // A ping is news by itself: the server sends one only after the member's categories and quiet hours.
        notifier.post(
            Raised(KEY, category = null, text = context.getString(R.string.push_something_new), webUrl = null)
        )
    }

    private companion object {
        const val KEY = "something-new"
    }
}
