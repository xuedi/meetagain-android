package org.meetagain.app.core.push

import android.content.Context
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import org.meetagain.app.R
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.MemberRepository
import org.meetagain.app.core.data.Notification
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.data.PushCategory
import org.meetagain.app.core.format.EventTime

/**
 * What a ping means. The ping itself carries nothing, so the app fetches what it already fetches and compares it
 * with what it had: that comparison, not the message, is where the news comes from.
 *
 * The same work runs on the timer for a member with no push app installed, which is why [fromTimer] exists - the
 * server gates pings on quiet hours, but nothing gates the timer except this.
 */
class PushWork(
    private val context: Context,
    private val repository: MemberRepository,
    private val raised: RaisedNotifications,
    private val notifier: Notifier,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun run(fromTimer: Boolean) {
        if (!notifier.canPost()) return
        val settings = repository.notificationSettings().first()?.value ?: return
        if (!settings.master) return

        val before = Snapshot(events(), bell())
        repository.refreshMyEvents()
        repository.refreshNotifications()
        val after = Snapshot(events(), bell())

        val already = raised.read()
        val news = (
            changedEvents(before.events, after.events) + reminders(after.events) +
                newBellItems(before.bell, after.bell)
            )
            .filter { it.key !in already }
            .filter { settings.wants(it.category) }
            .filter { !fromTimer || QuietHoursGate.allows(settings.other.quietHours, it.urgent, clock) }

        news.forEach { notifier.post(it.raised) }
        raised.add(news.map { it.key })
    }

    private suspend fun events(): List<Event> = repository.myEvents().first()?.value?.events.orEmpty()

    private suspend fun bell(): List<Notification> = repository.notifications().first()?.value.orEmpty()

    /** A meeting called off, or moved to another time. Both are what the member most needs to hear. */
    private fun changedEvents(before: List<Event>, after: List<Event>): List<News> {
        val known = before.associateBy { it.id }
        return after.mapNotNull { event ->
            val was = known[event.id] ?: return@mapNotNull null
            when {
                event.canceled && !was.canceled -> News(
                    key = "event-${event.id}-canceled",
                    category = PushCategory.EventChanges,
                    text = context.getString(R.string.push_event_canceled, event.title),
                    webUrl = event.webUrl,
                    // A meeting called off is the one thing allowed to interrupt quiet hours.
                    urgent = true
                )

                event.start != was.start && !event.canceled -> News(
                    key = "event-${event.id}-moved-${event.start.epochSecond}",
                    category = PushCategory.EventChanges,
                    text = context.getString(R.string.push_event_moved, event.title, whenIt(event.start)),
                    webUrl = event.webUrl,
                    urgent = false
                )

                else -> null
            }
        }
    }

    /**
     * There is nothing to compare for a reminder: the server pings at the hour it would have sent the mail, and the
     * app names the meeting the member said yes to that starts soonest.
     */
    private fun reminders(events: List<Event>): List<News> {
        val until = clock.instant().plus(REMINDER_WINDOW)
        return events.filter { it.mine?.going == true && !it.canceled && it.start.isBefore(until) }
            .map { event ->
                News(
                    key = "event-${event.id}-reminder",
                    category = PushCategory.Reminders,
                    text = context.getString(R.string.push_reminder, event.title, whenIt(event.start)),
                    webUrl = event.webUrl,
                    urgent = false
                )
            }
    }

    /**
     * A bell entry the phone has not seen, said in the server's own words. The bell is not sorted into the push
     * categories, so the category here is a best guess - see `architecture/notifications.md`.
     */
    private fun newBellItems(before: List<Notification>, after: List<Notification>): List<News> {
        val known = before.map { it.key to it.text }.toSet()
        return after.filterNot { (it.key to it.text) in known }.map { item ->
            News(
                key = "bell-${item.key}-${item.text.hashCode()}",
                category = if (item.key == UNREAD_MESSAGES) PushCategory.Messages else PushCategory.Announcements,
                text = item.text,
                webUrl = item.webUrl,
                urgent = false
            )
        }
    }

    private fun whenIt(start: Instant): String {
        val zone = ZoneId.systemDefault()
        val time = EventTime(Locale.getDefault(), zone, is24Hour = true, today = LocalDate.now(clock.withZone(zone)))
        return "${time.relativeDay(start)} ${time.time(start)}"
    }

    private data class News(
        val key: String,
        val category: PushCategory,
        val text: String,
        val webUrl: String?,
        val urgent: Boolean
    ) {
        val raised: Raised get() = Raised(key, category, text, webUrl)
    }

    private data class Snapshot(val events: List<Event>, val bell: List<Notification>)

    private companion object {
        /** What the server's own reminder mail uses. */
        val REMINDER_WINDOW: Duration = Duration.ofHours(5)
        const val UNREAD_MESSAGES = "unread_messages"
    }
}

private fun NotificationSettings.wants(category: PushCategory) = other.push[category.key] == true
