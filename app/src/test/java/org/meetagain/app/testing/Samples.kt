package org.meetagain.app.testing

import java.time.Instant
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.EventKind
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.Location
import org.meetagain.app.core.data.Upcoming

/** Content for screenshots and UI tests, in the shape the server sends it, around 22 September 2026. */
object Samples {
    val exchange = Event(
        id = 117,
        title = "German English Language Exchange",
        teaser = "Join us every Tuesday evening at Travolta Bar in Kreuzberg for a casual and fun German-English " +
            "language exchange.",
        start = Instant.parse("2026-09-22T17:00:00Z"),
        end = Instant.parse("2026-09-22T20:30:00Z"),
        kind = null,
        going = 12,
        imageUrl = "https://meetagain.org/images/thumbnails/exchange_600x400.webp",
        webUrl = "https://meetagain.org/en/event/117"
    )

    val picnic = Event(
        id = 126,
        title = "Picnic at Tempelhofer Feld",
        teaser = null,
        start = Instant.parse("2026-09-26T12:00:00Z"),
        end = Instant.parse("2026-09-26T16:00:00Z"),
        kind = EventKind.Outdoor,
        going = 1,
        imageUrl = null,
        webUrl = "https://meetagain.org/en/event/126"
    )

    val dinner = Event(
        id = 91,
        title = "Hotpot evening",
        teaser = "Bring your appetite.",
        start = Instant.parse("2026-09-26T17:30:00Z"),
        end = Instant.parse("2026-09-26T23:30:00Z"),
        kind = EventKind.Dinner,
        going = 8,
        imageUrl = null,
        webUrl = "https://meetagain.org/en/event/91"
    )

    val upcoming = Upcoming(listOf(exchange, picnic, dinner), complete = true)

    val eventDetails = EventDetails(
        event = exchange,
        description = "Every Tuesday from 7:00 PM at Travolta Bar.\n\nWhat to expect\n\n- Friendly and informal " +
            "atmosphere\n- All levels welcome\n\nIt is <b>free</b>, you only pay for your drinks.",
        location = Location("Travolta", "Wiener Strasse 14b", "10999", "Berlin"),
        photoUrls = listOf("https://meetagain.org/images/a_800x600.webp", "https://meetagain.org/images/b_800x600.webp")
    )

    val dragons = Group("my-community", "Dragon Descendants", "https://meetagain.org/images/thumbnails/logo_h120.webp")

    val groups = listOf(
        Group("another-country-bookshop", "Another Country Bookshop", null),
        Group("berlin-activities", "Berlin Activities", null),
        dragons,
        Group("german-english-language-exchange-in-berlin", "German English Language Exchange", null)
    )

    val groupDetails = GroupDetails(
        group = dragons,
        description = "Since 2015, this Berlin group brings together locals and internationals for relaxed " +
            "Chinese-German language exchange.",
        memberCount = 45,
        websiteUrl = "https://dragon-descendants.de/",
        languages = listOf("de", "en", "zh")
    )
}
