package org.meetagain.app.core.data

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

data class Membership(
    val group: Group,
    /** `owner`, `organizer` or `member`, as the server names it. */
    val role: String?,
    val status: MembershipStatus,
    val blocked: Boolean,
    val joinedAt: Instant?
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
