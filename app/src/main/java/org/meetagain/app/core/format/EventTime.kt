package org.meetagain.app.core.format

import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Event times as the member reads them: in the device's time zone, [locale] and clock (12 or 24 hours). The server
 * sends instants with its own offset; nothing here shows that offset.
 */
class EventTime(private val locale: Locale, private val zone: ZoneId, is24Hour: Boolean, private val today: LocalDate) {
    private val icuZone = TimeZone.getTimeZone(zone.id)
    private val timeSkeleton = if (is24Hour) "Hm" else "hm"

    /** The calendar day an event starts on, for grouping a list by day. */
    fun day(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** "Tuesday 22 September", with the year only when it is not this year. */
    fun date(instant: Instant): String {
        val skeleton = if (day(instant).year == today.year) "EEEEMMMMd" else "yMMMMEEEEd"
        return format(skeleton, instant)
    }

    fun time(instant: Instant): String = format(timeSkeleton, instant)

    /** "today", "yesterday" or the [date], worded for the middle of a sentence. */
    fun relativeDay(instant: Instant): String {
        val formatter = RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale),
            null,
            RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE
        )
        return when (ChronoUnit.DAYS.between(day(instant), today)) {
            0L -> formatter.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            1L -> formatter.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            else -> date(instant)
        }
    }

    /** "19:00 - 22:30" on one day; with both dates when the event runs past midnight. */
    fun timeRange(start: Instant, end: Instant?): String {
        if (end == null || !end.isAfter(start)) return time(start)
        val skeleton = if (day(start) == day(end)) timeSkeleton else "MMMEd$timeSkeleton"
        val format = DateIntervalFormat.getInstance(skeleton, locale)
        format.setTimeZone(icuZone)
        return format.format(DateInterval(start.toEpochMilli(), end.toEpochMilli()))
    }

    private fun format(skeleton: String, instant: Instant): String {
        val format = DateFormat.getInstanceForSkeleton(skeleton, locale)
        format.timeZone = icuZone
        return format.format(java.util.Date(instant.toEpochMilli()))
    }
}

/** The zone and day event times are read in; screenshots pin them so the images do not change with the date. */
data class TimeContext(val zone: ZoneId, val today: LocalDate)

val LocalTimeContext = staticCompositionLocalOf<TimeContext?> { null }

@Composable
fun rememberEventTime(): EventTime {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val context = LocalTimeContext.current ?: ZoneId.systemDefault().let { TimeContext(it, LocalDate.now(it)) }
    return remember(locale, is24Hour, context) { EventTime(locale, context.zone, is24Hour, context.today) }
}
