package org.meetagain.app.core.push

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meetagain.app.core.data.QuietHours

/** When the phone may speak on its own. The server gates pings; this gates the timer. */
class QuietHoursGateTest {
    private fun at(time: String, zone: String = BERLIN) = Clock.fixed(Instant.parse(time), ZoneId.of(zone))

    private fun window(
        enabled: Boolean = true,
        start: String = "22:00",
        end: String = "07:00",
        zone: String = BERLIN,
        urgent: Boolean = false
    ) = QuietHours(enabled, start, end, zone, urgent)

    @Test
    fun `outside the window anything may be said`() {
        assertTrue(QuietHoursGate.allows(window(), urgent = false, clock = at("2026-09-20T10:00:00Z")))
    }

    /** 22:00 to 07:00 is the normal case, and it wraps midnight. */
    @Test
    fun `inside a window that wraps midnight nothing is said`() {
        assertFalse(QuietHoursGate.allows(window(), urgent = false, clock = at("2026-09-20T21:30:00Z")))
        assertFalse(QuietHoursGate.allows(window(), urgent = false, clock = at("2026-09-20T03:00:00Z")))
    }

    @Test
    fun `a cancelled meeting gets through only when the member allowed it`() {
        val night = at("2026-09-20T23:00:00Z")
        assertFalse(QuietHoursGate.allows(window(urgent = false), urgent = true, clock = night))
        assertTrue(QuietHoursGate.allows(window(urgent = true), urgent = true, clock = night))
    }

    @Test
    fun `a window switched off holds nothing back`() {
        assertTrue(QuietHoursGate.allows(window(enabled = false), urgent = false, clock = at("2026-09-20T23:00:00Z")))
    }

    @Test
    fun `no window at all holds nothing back`() {
        assertTrue(QuietHoursGate.allows(null, urgent = false, clock = at("2026-09-20T23:00:00Z")))
    }

    /** A window inside one day, rather than across midnight, is the other shape the member can set. */
    @Test
    fun `a daytime window works the same way`() {
        val gate = window(start = "09:00", end = "17:00")
        assertFalse(QuietHoursGate.allows(gate, urgent = false, clock = at("2026-09-20T10:00:00Z")))
        assertTrue(QuietHoursGate.allows(gate, urgent = false, clock = at("2026-09-20T18:00:00Z")))
    }

    /** The window is wall-clock time where the member is, not where the server is. */
    @Test
    fun `the window follows the stored time zone`() {
        // 02:00 in Berlin, so inside 22:00-07:00; the same instant is 08:00 in Shanghai, so outside it.
        val instant = at("2026-09-21T00:00:00Z")
        assertFalse(QuietHoursGate.allows(window(), urgent = false, clock = instant))
        assertTrue(QuietHoursGate.allows(window(zone = SHANGHAI), urgent = false, clock = instant))
    }

    @Test
    fun `a window that cannot be read holds nothing back`() {
        assertTrue(
            QuietHoursGate.allows(window(start = "nonsense"), urgent = false, clock = at("2026-09-20T23:00:00Z"))
        )
    }

    private companion object {
        const val BERLIN = "Europe/Berlin"
        const val SHANGHAI = "Asia/Shanghai"
    }
}
