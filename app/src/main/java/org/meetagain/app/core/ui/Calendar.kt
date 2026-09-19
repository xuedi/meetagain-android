package org.meetagain.app.core.ui

import android.content.Intent
import android.provider.CalendarContract
import androidx.core.net.toUri
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.calendarDescription
import org.meetagain.app.core.data.calendarEnd

/** A new event in the member's calendar app, filled in for them to check and save. Needs no permission. */
fun calendarInsertIntent(details: EventDetails): Intent {
    val event = details.event
    return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.calendarEnd.toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, calendarDescription(details))
        .apply { details.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it.query) } }
}

/** Hands a feed to whichever app subscribes to calendars; `webcal:` is the scheme those apps register for. */
fun subscribeIntent(feedUrl: String): Intent =
    Intent(Intent.ACTION_VIEW, feedUrl.replaceFirst(Regex("^https?:"), "webcal:").toUri())
