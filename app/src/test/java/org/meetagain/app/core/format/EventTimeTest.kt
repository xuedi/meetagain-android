package org.meetagain.app.core.format

import android.app.Application
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EventTimeTest {
    private val start = Instant.parse("2026-09-22T17:00:00Z")
    private val end = Instant.parse("2026-09-22T20:30:00Z")
    private val today = LocalDate.of(2026, 9, 19)
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun time(locale: Locale = Locale.UK, zone: ZoneId = berlin, is24Hour: Boolean = true) =
        EventTime(locale, zone, is24Hour, today)

    /** ICU separates ranges with an en dash and thin spaces, and 12-hour times with a narrow space. */
    private fun String.plain() = replace(' ', ' ').replace(' ', ' ').replace('–', '-')

    @Test
    fun `times follow the device zone`() {
        assertEquals("19:00", time().time(start))
        assertEquals("01:00", time(zone = ZoneId.of("Asia/Shanghai")).time(start))
        assertEquals(LocalDate.of(2026, 9, 23), time(zone = ZoneId.of("Asia/Shanghai")).day(start))
    }

    @Test
    fun `the clock follows the device setting`() {
        assertEquals("7:00 pm", time(is24Hour = false).time(start).plain())
    }

    @Test
    fun `dates follow the locale and name the year only when it is not this year`() {
        assertEquals("Tuesday 22 September", time().date(start))
        assertEquals("Dienstag, 22. September", time(Locale.GERMAN).date(start))
        assertEquals("9月22日星期二", time(Locale.SIMPLIFIED_CHINESE).date(start))
        assertEquals("Friday 22 January 2027", time().date(Instant.parse("2027-01-22T17:00:00Z")))
    }

    @Test
    fun `a range on one day shows the times only`() {
        assertEquals("19:00-22:30", time().timeRange(start, end).plain())
        assertEquals("19:00", time().timeRange(start, null))
    }

    @Test
    fun `a range past midnight shows both days`() {
        val late = Instant.parse("2026-09-22T23:30:00Z")
        assertEquals("Tue, 22 Sept, 19:00 - Wed, 23 Sept, 01:30", time().timeRange(start, late).plain())
    }

    @Test
    fun `the last update reads today, yesterday or a date`() {
        assertEquals("today", time().relativeDay(Instant.parse("2026-09-19T12:05:00Z")))
        assertEquals("yesterday", time().relativeDay(Instant.parse("2026-09-18T21:00:00Z")))
        assertEquals("heute", time(Locale.GERMAN).relativeDay(Instant.parse("2026-09-19T12:05:00Z")))
        assertEquals("Wednesday 16 September", time().relativeDay(Instant.parse("2026-09-16T12:00:00Z")))
    }
}
