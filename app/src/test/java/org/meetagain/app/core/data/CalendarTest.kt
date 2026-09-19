package org.meetagain.app.core.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.meetagain.app.testing.Samples

class CalendarTest {
    private val group = Samples.groupDetails

    @Test
    fun `the feed is in the app's language when the group publishes in it`() {
        assertEquals("https://dragon-descendants.de/zh/events.ics", calendarFeedUrl(group, "zh"))
    }

    @Test
    fun `the feed falls back to the group's first language`() {
        assertEquals("https://dragon-descendants.de/de/events.ics", calendarFeedUrl(group, "fr"))
    }

    @Test
    fun `a group without languages gets the English feed`() {
        assertEquals(
            "https://dragon-descendants.de/en/events.ics",
            calendarFeedUrl(group.copy(languages = emptyList()), "fr")
        )
    }

    @Test
    fun `a group without a domain has no feed`() {
        assertNull(calendarFeedUrl(group.copy(websiteUrl = null), "en"))
    }

    @Test
    fun `an event without an end lasts two hours`() {
        val event = Samples.exchange.copy(end = null)
        assertEquals(Instant.parse("2026-09-22T19:00:00Z"), event.calendarEnd)
        assertEquals(Samples.exchange.end, Samples.exchange.calendarEnd)
    }

    @Test
    fun `html becomes plain text with its line breaks`() {
        assertEquals(
            "Every Tuesday.\n\nIt is free & fun,\nbring \"friends\" <3",
            plainText("Every Tuesday.<br><br>It is <b>free</b> &amp; fun,<br/>bring &quot;friends&quot; &lt;3")
        )
    }

    @Test
    fun `paragraphs end in a line break and runs of blank lines collapse`() {
        assertEquals("One\n\nTwo\nThree", plainText("<p>One</p>\n\n\n<p>Two</p>Three"))
        assertEquals("café é", plainText("caf&#233; &#xE9;"))
    }

    @Test
    fun `the calendar description is the text and the event's page`() {
        assertEquals(
            "Every Tuesday from 7:00 PM at Travolta Bar.\n\nWhat to expect\n\n- Friendly and informal atmosphere\n" +
                "- All levels welcome\n\nIt is free, you only pay for your drinks.\n\n" +
                "https://meetagain.org/en/event/117",
            calendarDescription(Samples.eventDetails)
        )
        assertEquals(
            "https://meetagain.org/en/event/117",
            calendarDescription(Samples.eventDetails.copy(description = null))
        )
    }
}
