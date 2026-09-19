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
    val previewImageUrl: String? = null,
    val detailUrl: String,
    val webUrl: String
)

/** `EventDetail` in the API description. [description] is text with newlines and optional inline HTML. */
@Serializable
data class EventDetailDto(
    val id: Int,
    val title: String,
    val teaser: String? = null,
    val start: String,
    val stop: String? = null,
    val type: Int? = null,
    val rsvpCount: Int,
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
