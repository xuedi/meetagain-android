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
data class NotificationItemDto(
    val key: String? = null,
    val label: String,
    val icon: String? = null,
    val webUrl: String? = null
)

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
    val attendedEventUpdate: Boolean? = null,
    val push: Map<String, Boolean>? = null,
    val quietHours: QuietHoursDto? = null
)

// Push devices

/**
 * `PushSubscriptionList` in the API description. [available] is false when the server has no VAPID key
 * configured, which means push is off on that platform rather than broken.
 */
@Serializable
data class PushSubscriptionListDto(
    val available: Boolean,
    val vapidPublicKey: String = "",
    val subscriptions: List<PushSubscriptionDto> = emptyList()
)

/** `PushSubscriptionEntry` in the API description. */
@Serializable
data class PushSubscriptionDto(
    val id: Int,
    val transport: String,
    val createdAt: String? = null,
    val lastSuccessAt: String? = null
)

/**
 * What the distributor gave the app, on its way to the server so it can encrypt to this device. [transport] has no
 * default here on purpose: the server would fill one in, and the app says which path it registered for rather than
 * letting that be decided elsewhere.
 */
@Serializable
data class PushRegistrationDto(val endpoint: String, val p256dh: String, val auth: String, val transport: String)

/** `SignalTokenResult` in the API description: a token for this device that can only read the signal. */
@Serializable
data class SignalTokenDto(val token: String, val scopes: List<String>, val expiresAt: String)

/** `SignalState` in the API description. Opaque: only whether it differs from the last one means anything. */
@Serializable
data class SignalStateDto(val state: String)

/** The only transport the app registers for; FCM is out of this version by decision. */
const val TRANSPORT_UNIFIEDPUSH = "unifiedpush"
