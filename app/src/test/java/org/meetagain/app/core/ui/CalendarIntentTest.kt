package org.meetagain.app.core.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.CalendarContract
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meetagain.app.testing.Samples
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalendarIntentTest {
    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the insert intent carries the event`() {
        val intent = calendarInsertIntent(Samples.eventDetails)
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals(
            Samples.exchange.start.toEpochMilli(),
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)
        )
        assertEquals(
            Samples.exchange.end!!.toEpochMilli(),
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0)
        )
        assertEquals("German English Language Exchange", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(
            "Travolta, Wiener Strasse 14b, 10999 Berlin",
            intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION)
        )
        val description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION)!!
        assertTrue(description.startsWith("Every Tuesday"))
        assertTrue(description.endsWith("https://meetagain.org/en/event/117"))
    }

    @Test
    fun `an event without an end is inserted for two hours and without a place has no location`() {
        val details = Samples.eventDetails.copy(event = Samples.exchange.copy(end = null), location = null)
        val intent = calendarInsertIntent(details)
        assertEquals(
            Samples.exchange.start.plusSeconds(2 * 60 * 60).toEpochMilli(),
            intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0)
        )
        assertFalse(intent.hasExtra(CalendarContract.Events.EVENT_LOCATION))
    }

    @Test
    fun `the subscription is a webcal link to the feed`() {
        val intent = subscribeIntent("https://dragon-descendants.de/en/events.ics")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("webcal://dragon-descendants.de/en/events.ics", intent.data.toString())
    }

    @Test
    fun `opening reports whether an app took the intent`() {
        shadowOf(application).checkActivities(true)
        lateinit var open: (Intent) -> Boolean
        compose.setContent { open = rememberOpenIntent() }
        val subscribe = subscribeIntent("https://dragon-descendants.de/en/events.ics")

        assertFalse(open(subscribe))

        val subscriber = ComponentName("org.example.subscriber", "org.example.subscriber.Subscribe")
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(subscriber)
            addIntentFilterForActivity(
                subscriber,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("webcal")
                }
            )
        }
        assertTrue(open(subscribe))
    }
}
