package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

/** `EventList` in the API description. */
@Serializable
data class EventListDto(val items: List<EventSummaryDto>, val total: Int, val limit: Int, val offset: Int)

/** `EventSummary` in the API description. Times are ISO 8601 with the server's offset. */
@Serializable
data class EventSummaryDto(
    val id: Int,
    val title: String,
    val teaser: String? = null,
    val start: String,
    val stop: String? = null,
    val type: Int? = null,
    val rsvpCount: Int,
    val attendeeCount: Int,
    val canceled: Boolean = false,
    val seriesId: Int? = null,
    val group: EventGroupDto? = null,
    val myRsvp: Boolean? = null,
    val myGuests: Int? = null,
    val previewImageUrl: String? = null,
    val detailUrl: String,
    val webUrl: String
)

/** `EventDetail` in the API description. [description] is plain text with newlines. */
@Serializable
data class EventDetailDto(
    val id: Int,
    val title: String,
    val teaser: String? = null,
    val start: String,
    val stop: String? = null,
    val type: Int? = null,
    val rsvpCount: Int,
    val attendeeCount: Int,
    val canceled: Boolean = false,
    val seriesId: Int? = null,
    val group: EventGroupDto? = null,
    val myRsvp: Boolean? = null,
    val myGuests: Int? = null,
    val previewImageUrl: String? = null,
    val detailUrl: String,
    val webUrl: String,
    val description: String? = null,
    val location: EventLocationDto? = null,
    val images: List<String>
)

/** `EventLocation` in the API description. */
@Serializable
data class EventLocationDto(
    val name: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postcode: String? = null
)

/** `EventGroup` in the API description: the group an event belongs to. */
@Serializable
data class EventGroupDto(
    val slug: String,
    val name: String,
    val visibility: String? = null,
    val domain: String? = null,
    val logoUrl: String? = null
)
