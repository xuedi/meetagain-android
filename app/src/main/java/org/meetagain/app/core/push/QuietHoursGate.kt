package org.meetagain.app.core.push

import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import org.meetagain.app.core.data.QuietHours

/**
 * Whether the phone may speak now. The server already holds pings back during the member's quiet hours, so this is
 * for what the app raises on its own timer - the one path the server cannot gate.
 *
 * A window that wraps midnight is the normal case, so it is the case this is written around.
 */
object QuietHoursGate {
    fun allows(quietHours: QuietHours?, urgent: Boolean, clock: Clock = Clock.systemUTC()): Boolean {
        if (quietHours == null || !quietHours.enabled) return true
        if (!isQuiet(quietHours, clock)) return true
        return urgent && quietHours.allowUrgent
    }

    private fun isQuiet(quietHours: QuietHours, clock: Clock): Boolean {
        val start = time(quietHours.start) ?: return false
        val end = time(quietHours.end) ?: return false
        if (start == end) return false
        val zone = runCatching { ZoneId.of(quietHours.timeZone) }.getOrDefault(clock.zone)
        val now = LocalTime.now(clock.withZone(zone))
        return if (start.isBefore(end)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            // 22:00 to 07:00: quiet from the evening through to the morning.
            !now.isBefore(start) || now.isBefore(end)
        }
    }

    private fun time(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}
