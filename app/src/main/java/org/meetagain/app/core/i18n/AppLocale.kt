package org.meetagain.app.core.i18n

import java.util.Locale

/** The five languages MeetAgain speaks. The app's language is the first of them that matches the current locale. */
object AppLocale {
    val supported = listOf("en", "de", "zh", "fr", "es")

    /** A language code the server understands; English when the app runs in any other language. */
    fun current(locale: Locale = Locale.getDefault()): String = locale.language.takeIf { it in supported } ?: "en"
}
