package org.meetagain.app.core.data

import java.time.Duration
import java.time.Instant

/** The kinds of meeting worth a label; a regular meeting has none. */
enum class EventKind { Outdoor, Dinner }

/** What the member answered on an event: whether they are coming and how many people they bring. */
data class Rsvp(val going: Boolean, val guests: Int = 0)

data class Event(
    val id: Int,
    val title: String,
    val teaser: String?,
    val start: Instant,
    val end: Instant?,
    val kind: EventKind?,
    /** The people who said yes, without their guests. */
    val going: Int,
    /** Everyone expected: those people, their guests and the ones the organiser counts without an account. */
    val attending: Int = going,
    val canceled: Boolean = false,
    /** Set when the meeting repeats, and the same for every date of it. */
    val seriesId: Int? = null,
    val group: Group? = null,
    /** The member's own answer, or null when nobody is signed in. */
    val mine: Rsvp? = null,
    val imageUrl: String?,
    val webUrl: String
)

data class EventDetails(
    val event: Event,
    /** Text with newlines, as the website shows it. */
    val description: String?,
    val location: Location?,
    val photoUrls: List<String>
)

data class Location(val name: String?, val street: String?, val postcode: String?, val city: String?) {
    /** The address as one line for a map search: name, street, postcode and city, whichever are known. */
    val query: String = listOfNotNull(name, street, listOfNotNull(postcode, city).joinToString(" ").ifBlank { null })
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

/** The upcoming events of a list; [complete] is false when the server has more than the app asked for. */
data class Upcoming(val events: List<Event>, val complete: Boolean)

data class Group(val slug: String, val name: String, val logoUrl: String?)

data class GroupDetails(
    val group: Group,
    val description: String?,
    val memberCount: Int,
    /** The group's own website, from its domain. */
    val websiteUrl: String?,
    /** The languages the group publishes in, as language codes. */
    val languages: List<String>
)

/** One person coming to a meeting, and the guests they bring. */
data class Attendee(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val guests: Int,
    /** Whether this is the member looking at the list. */
    val mine: Boolean
)

/** Who is coming to a meeting: the people with an account, and the ones the organiser counts. */
data class Attendees(val people: List<Attendee>, val externalCount: Int, val total: Int)

data class Comment(
    val id: Int,
    val authorName: String,
    /** Null once the author's account is gone. */
    val authorId: Int? = null,
    val authorAvatarUrl: String?,
    val writtenAt: Instant?,
    val text: String,
    val mine: Boolean,
    val canDelete: Boolean
)

/** A page of the conversation, newest first; [olderBefore] is what to ask for to get the page before it. */
data class Conversation(val comments: List<Comment>, val total: Int, val olderBefore: Int?)

data class Photo(val id: Int, val url: String, val thumbnailUrl: String, val mine: Boolean)

/** Where a member stands with a group. */
enum class MembershipStatus { Pending, Approved, Rejected }

/**
 * A part of a group that not every group has. The server says per membership whether it is open to the member, and
 * the app offers a feature only there: in the bar, and on that group's page.
 */
enum class GroupFeature { TownHall }

data class Membership(
    val group: Group,
    /** `owner`, `organizer` or `member`, as the server names it. */
    val role: String?,
    val status: MembershipStatus,
    val blocked: Boolean,
    val joinedAt: Instant?,
    val features: Set<GroupFeature> = emptySet()
)

data class Invitation(val id: Int, val group: Group, val role: String?, val invitedBy: String?, val expiresAt: Instant?)

/** The member's own profile, as they can change it in the app. */
data class Profile(
    val id: Int,
    val name: String,
    val email: String,
    val bio: String?,
    val language: String,
    /** Whether other members can see the profile. */
    val public: Boolean,
    val avatarUrl: String?
)

/**
 * One entry of the bell. [key] says what kind of thing it is, so the app can open its own screen for it; [text] is
 * the sentence the server already wrote in the member's language, shown as it is. [webUrl] is where the website
 * shows it, used when the app has no screen of its own.
 */
data class Notification(val key: String, val text: String, val webUrl: String?)

/**
 * What the member hears about, the same switches as the website's profile settings. [master] off means they hear
 * nothing whatever the rest say.
 *
 * [other] carries the settings this app has no screen for yet - the push categories and quiet hours - so reading
 * and writing the settings never drops them.
 */
data class NotificationSettings(
    val master: Boolean,
    val announcements: Boolean,
    val followingUpdates: Boolean,
    val receivedMessage: Boolean,
    val eventReminder: Boolean,
    val upcomingEvents: Boolean,
    val attendedEventUpdate: Boolean,
    val other: OtherSettings
)

/** The parts of the notification settings that belong to push delivery, kept as the server sent them. */
data class OtherSettings(val push: Map<String, Boolean>, val quietHours: QuietHours?)

data class QuietHours(
    val enabled: Boolean,
    val start: String,
    val end: String,
    val timeZone: String,
    val allowUrgent: Boolean
)

/** Which switch a toggle on the settings screen means, and the key the server knows it by. */
enum class NotificationSetting(val key: String) {
    Master("enabled"),
    Announcements("announcements"),
    FollowingUpdates("followingUpdates"),
    ReceivedMessage("receivedMessage"),
    EventReminder("eventReminder"),
    UpcomingEvents("upcomingEvents"),
    AttendedEventUpdate("attendedEventUpdate")
}

/** What a member can ask to be told about at once. The server decides what to ping about from these. */
enum class PushCategory(val key: String) {
    EventChanges("event-changes"),
    Reminders("reminders"),
    Messages("messages"),
    Announcements("announcements")
}

/**
 * The push devices registered for this member, and whether the server can do push at all.
 * [available] false means the platform has no key configured - push is off there, not broken.
 */
data class PushDevices(val available: Boolean, val vapidPublicKey: String, val devices: List<PushDevice>)

data class PushDevice(val id: Int, val transport: String, val registeredAt: Instant?)

/** What the distributor handed the app, on its way to the server. */
data class PushRegistration(val endpoint: String, val p256dh: String, val auth: String)

// The community: messages and members

/** One person as a member list, a conversation or a thread names them. */
data class MemberSummary(val id: Int, val name: String, val avatarUrl: String?)

/**
 * One row of the inbox: who, how much was said, how much is unread and when the last one came. There is no
 * preview, because the server sends none - and so no message text ever sits in a list.
 */
data class InboxEntry(val partner: MemberSummary, val messages: Int, val unread: Int, val lastMessageAt: Instant?)

/** A page of the inbox, newest first; [total] counts every conversation the member has. */
data class Inbox(val entries: List<InboxEntry>, val total: Int)

/**
 * One message of a thread. [systemNote] marks a support question carried into the thread, which is shown as a note
 * rather than as something the partner typed.
 */
data class Message(
    val id: Int,
    val text: String,
    val sentAt: Instant?,
    val mine: Boolean,
    /** Whether the receiver has read it; on a partner's message, false is what the thread marks read for. */
    val read: Boolean,
    /** Whether the member may still take it back: the server's answer, and the window counted here as well. */
    val editable: Boolean,
    val editedAt: Instant?,
    val systemNote: Boolean
)

/**
 * A page of one thread, oldest first. [offset] is where the page starts in the whole thread, which is what the page
 * before it is asked for with; [blocked] means a block in either direction, so the thread reads but nothing sends.
 */
data class MessageThread(
    val partner: MemberSummary,
    val messages: List<Message>,
    val total: Int,
    val offset: Int,
    val blocked: Boolean
) {
    /** Whether there is anything before this page. */
    val hasEarlier: Boolean get() = offset > 0
}

/** A member's page, with the same fields the website shows and no more. */
data class MemberProfile(
    val id: Int,
    val name: String,
    val bio: String?,
    val avatarUrl: String?,
    /** Whether they appear in the list shown to visitors who are not signed in. */
    val public: Boolean,
    val memberSince: Instant?,
    val following: Boolean,
    val followsMe: Boolean,
    val blockedByMe: Boolean,
    val canMessage: Boolean
)

/** A page of members: a group's list, or the ones the member has blocked. */
data class Members(val people: List<MemberSummary>, val total: Int)

/** How long a message can be taken back, counted from when it was sent, as the server counts it. */
val MESSAGE_EDIT_WINDOW: Duration = Duration.ofMinutes(10)
