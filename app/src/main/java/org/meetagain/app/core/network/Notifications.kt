package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

/**
 * `NotificationList` in the API description: the website's navbar bell, as data.
 *
 * The server stores none of it - every item is computed from live data when asked - so there is no read state, no
 * history and no cursor, and an item disappears when the condition behind it stops being true.
 */
@Serializable
data class NotificationListDto(val items: List<NotificationItemDto>, val total: Int)

/**
 * `NotificationItem` in the API description. [key] is stable and machine-readable; [label] is a sentence the server
 * has already translated into the request's language, which the app shows and never parses.
 */
@Serializable
data class NotificationItemDto(val key: String, val label: String, val icon: String? = null, val webUrl: String? = null)

/**
 * `MeNotificationSettings` in the API description. [push] and [quietHours] belong to push delivery; this app reads
 * them so a round trip does not lose them and sends them back only once it has a screen for them.
 */
@Serializable
data class NotificationSettingsDto(
    val enabled: Boolean,
    val announcements: Boolean,
    val followingUpdates: Boolean,
    val receivedMessage: Boolean,
    val eventReminder: Boolean,
    val upcomingEvents: Boolean,
    val attendedEventUpdate: Boolean,
    val push: Map<String, Boolean> = emptyMap(),
    val quietHours: QuietHoursDto? = null
)

/** `QuietHours` in the API description. Carried through untouched until push delivery has a screen for it. */
@Serializable
data class QuietHoursDto(
    val enabled: Boolean,
    val start: String,
    val end: String,
    val timeZone: String,
    val allowUrgent: Boolean
)

/** Only the keys the member changed are sent; an absent one keeps its stored value. */
@Serializable
data class NotificationSettingsChangeDto(
    val enabled: Boolean? = null,
    val announcements: Boolean? = null,
    val followingUpdates: Boolean? = null,
    val receivedMessage: Boolean? = null,
    val eventReminder: Boolean? = null,
    val upcomingEvents: Boolean? = null,
    val attendedEventUpdate: Boolean? = null
)
