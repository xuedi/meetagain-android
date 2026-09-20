package org.meetagain.app.navigation

import androidx.navigation3.runtime.NavKey
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Which screen a meetagain.org address belongs to, for a link the member tapped elsewhere on the phone and for a
 * bell item that has a screen of its own.
 *
 * Only the platform's own host is mapped. A group's own domain is left to the browser: it cannot be a verified App
 * Links host without being named inside the app, and which domains exist is not knowable when the app is built.
 * Anything with no screen here answers null, and the caller opens the website instead of guessing.
 */
fun destinationOf(url: String?, host: String): NavKey? {
    val parsed = url?.toHttpUrlOrNull() ?: return null
    if (!parsed.host.equals(host, ignoreCase = true)) return null
    return destinationOfPath(parsed.withoutLocale())
}

/** The path segments without the language the website puts in front of every page. */
private fun HttpUrl.withoutLocale(): List<String> {
    val segments = pathSegments.filter { it.isNotEmpty() }
    return if (segments.firstOrNull()?.isLanguage() == true) segments.drop(1) else segments
}

private fun String.isLanguage() = length == 2 && all { it in 'a'..'z' }

private fun destinationOfPath(segments: List<String>): NavKey? = when {
    segments.isEmpty() -> Home
    segments[0] == EVENT -> segments.getOrNull(1)?.toIntOrNull()?.let(::EventDetail)
    segments[0] == EVENTS || segments[0] == GROUPS -> Explore
    segments[0] == PROFILE -> profileDestination(segments.drop(1))
    else -> null
}

/**
 * The website's profile pages, as far as the app has them. The ones it does not have - the review queue, the
 * access tokens - answer null so they open where they actually work.
 */
private fun profileDestination(rest: List<String>): NavKey? = when (rest.firstOrNull()) {
    null -> MyProfile
    MY_GROUPS_PATH -> MyGroups
    NOTIFICATIONS_PATH -> Notifications
    CONFIG_PATH -> NotificationSettings
    BLOCKED_PATH -> Blocked
    MESSAGES_PATH -> messagesDestination(rest.drop(1))
    else -> null
}

/**
 * `/profile/messages` is the inbox and `/profile/messages/{id}` that member's thread, which is what makes the
 * bell's unread-messages item and the message push open the app rather than the browser. Anything else under it -
 * a partner that is not a number - is not a screen here.
 */
private fun messagesDestination(rest: List<String>): NavKey? =
    if (rest.isEmpty()) Messages else rest.singleOrNull()?.toIntOrNull()?.let(::Thread)

private const val EVENT = "event"
private const val EVENTS = "events"
private const val GROUPS = "groups"
private const val PROFILE = "profile"
private const val MY_GROUPS_PATH = "my-groups"
private const val NOTIFICATIONS_PATH = "notifications"
private const val CONFIG_PATH = "config"
private const val BLOCKED_PATH = "blocked"
private const val MESSAGES_PATH = "messages"
