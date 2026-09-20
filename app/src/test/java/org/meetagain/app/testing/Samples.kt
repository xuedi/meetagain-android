package org.meetagain.app.testing

import java.time.Instant
import org.meetagain.app.core.data.Attendee
import org.meetagain.app.core.data.Attendees
import org.meetagain.app.core.data.Comment
import org.meetagain.app.core.data.Event
import org.meetagain.app.core.data.EventDetails
import org.meetagain.app.core.data.EventKind
import org.meetagain.app.core.data.Group
import org.meetagain.app.core.data.GroupDetails
import org.meetagain.app.core.data.Invitation
import org.meetagain.app.core.data.Location
import org.meetagain.app.core.data.Membership
import org.meetagain.app.core.data.MembershipStatus
import org.meetagain.app.core.data.Notification
import org.meetagain.app.core.data.NotificationSettings
import org.meetagain.app.core.data.OtherSettings
import org.meetagain.app.core.data.Photo
import org.meetagain.app.core.data.QuietHours
import org.meetagain.app.core.data.Rsvp
import org.meetagain.app.core.data.Upcoming
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.ui.Stale
import org.meetagain.app.feature.conversation.Comments
import org.meetagain.app.feature.home.Home
import org.meetagain.app.feature.mygroups.MyGroups
import org.meetagain.app.feature.profile.ProfileEdit

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
            "atmosphere\n- All levels welcome\n\nIt is free, you only pay for your drinks.",
        location = Location("Travolta", "Wiener Strasse 14b", "10999", "Berlin"),
        photoUrls = listOf("https://meetagain.org/images/a_800x600.webp", "https://meetagain.org/images/b_800x600.webp")
    )

    /** Stored this morning, before the connection went. */
    val offlineSince = Stale(Instant.parse("2026-09-19T07:05:00Z"), ApiError.Offline)

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

    // Signed in, as Crystal Liu

    val myExchange = exchange.copy(
        attending = 14,
        seriesId = 4,
        group = dragons,
        mine = Rsvp(going = true, guests = 1)
    )

    val myPicnic = picnic.copy(group = dragons, mine = Rsvp(going = false))

    val myDinner = dinner.copy(group = dragons, canceled = true, mine = Rsvp(going = true))

    val home = Home(next = myExchange, later = listOf(myPicnic, myDinner), beyondWindow = false)

    val attendees = Attendees(
        people = listOf(
            Attendee(4, "Crystal Liu", null, guests = 1, mine = true),
            Attendee(6, "Adem Lane", null, guests = 0, mine = false),
            Attendee(12, "Ali Mahdi", null, guests = 2, mine = false)
        ),
        externalCount = 3,
        total = 9
    )

    val conversation = Comments(
        visible = listOf(
            Comment(
                id = 10,
                authorName = "Ali Mahdi",
                authorAvatarUrl = null,
                writtenAt = Instant.parse("2026-09-18T18:00:00Z"),
                text = "Count me in, I will bring my own board.",
                mine = false,
                canDelete = false
            ),
            Comment(
                id = 9,
                authorName = "Crystal Liu",
                authorAvatarUrl = null,
                writtenAt = Instant.parse("2026-09-17T09:30:00Z"),
                text = "See you all on Tuesday.",
                mine = true,
                canDelete = true
            )
        ),
        total = 2,
        hasOlder = false
    )

    val photos = listOf(
        Photo(41, "https://meetagain.org/images/a_1024x768.webp", "https://meetagain.org/images/a_350x263.webp", true),
        Photo(42, "https://meetagain.org/images/b_1024x768.webp", "https://meetagain.org/images/b_350x263.webp", false)
    )

    val myGroups = MyGroups(
        memberships = listOf(
            Membership(dragons, "member", MembershipStatus.Approved, blocked = false, joinedAt = null),
            Membership(groups[0], "owner", MembershipStatus.Approved, blocked = false, joinedAt = null),
            Membership(groups[1], null, MembershipStatus.Pending, blocked = false, joinedAt = null)
        ),
        invitations = listOf(Invitation(7, groups[3], "member", "Adem Lane", null))
    )

    val profile = ProfileEdit(
        name = "Crystal Liu",
        bio = "Adventurer at heart, I explore the great outdoors and capture moments through photography.",
        language = "zh",
        public = true,
        avatarUrl = null
    )

    /** The bell as the server words it, including one item the app has no screen of its own for. */
    val notifications = listOf(
        Notification(
            key = "group_invitation",
            text = "1 group invitation",
            webUrl = "https://meetagain.org/en/profile/my-groups/"
        ),
        Notification(
            key = "unread_messages",
            text = "3 unread messages",
            webUrl = "https://meetagain.org/en/profile/messages/12"
        ),
        Notification(
            key = "review_pending",
            text = "2 items waiting for review",
            webUrl = "https://meetagain.org/en/profile/review"
        )
    )

    val notificationSettings = NotificationSettings(
        master = true,
        announcements = true,
        followingUpdates = false,
        receivedMessage = true,
        eventReminder = true,
        upcomingEvents = false,
        attendedEventUpdate = true,
        other = OtherSettings(
            push = mapOf("event-changes" to false, "reminders" to false),
            quietHours = QuietHours(
                enabled = true,
                start = "22:00",
                end = "07:00",
                timeZone = "Europe/Berlin",
                allowUrgent = false
            )
        )
    )
}
