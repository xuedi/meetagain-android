package org.meetagain.app.core.data

import java.time.Duration
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The group's subscribable calendar feed on its own domain, or null when the group has none. The feed only holds events
 * in the language it is asked for, so the language is one the group publishes in: the app's own if it can, else the
 * group's first.
 */
fun calendarFeedUrl(group: GroupDetails, appLanguage: String): String? {
    val website = group.websiteUrl?.toHttpUrlOrNull() ?: return null
    val language = appLanguage.takeIf { it in group.languages } ?: group.languages.firstOrNull() ?: "en"
    return website.newBuilder()
        .encodedPath("/")
        .addPathSegment(language)
        .addPathSegment("events.ics")
        .query(null)
        .fragment(null)
        .build()
        .toString()
}

/** Without an end the event lasts two hours, as on the website and in its calendar feed. */
val Event.calendarEnd: Instant get() = end ?: start.plus(Duration.ofHours(2))

/** The event's text for a calendar entry: plain text, then the link to its page, where changes show up. */
fun calendarDescription(details: EventDetails): String =
    listOfNotNull(details.description?.let(::plainText)?.takeIf { it.isNotEmpty() }, details.event.webUrl)
        .joinToString("\n\n")

/** Text with inline HTML as plain text: tags dropped, line breaks kept, entities decoded. */
fun plainText(html: String): String = html
    .replace("\r\n", "\n")
    .replace(LINE_BREAK, "\n")
    .replace(BLOCK_END, "\n")
    .replace(TAG, "")
    .let(::decodeEntities)
    .lines()
    .joinToString("\n") { it.trimEnd() }
    .replace(BLANK_LINES, "\n\n")
    .trim()

private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val name = match.groupValues[1]
    when {
        name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
        else -> NAMED_ENTITIES[name]
    } ?: match.value
}

private fun codePoint(value: Int): String? = value.takeIf { Character.isValidCodePoint(it) }?.let(Character::toString)

private val LINE_BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val BLOCK_END = Regex("</(p|div|li|h[1-6])\\s*>", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]*>")
private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val BLANK_LINES = Regex("\n{3,}")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " "
)
