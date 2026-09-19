package org.meetagain.app.core.data

import java.time.Instant

/** The kinds of meeting worth a label; a regular meeting has none. */
enum class EventKind { Outdoor, Dinner }

data class Event(
    val id: Int,
    val title: String,
    val teaser: String?,
    val start: Instant,
    val end: Instant?,
    val kind: EventKind?,
    val going: Int,
    val imageUrl: String?,
    val webUrl: String
)

data class EventDetails(
    val event: Event,
    /** Text with newlines and optional inline HTML, as the website stores it. */
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
