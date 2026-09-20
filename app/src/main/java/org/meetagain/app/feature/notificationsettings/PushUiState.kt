package org.meetagain.app.feature.notificationsettings

import org.meetagain.app.core.push.PushInterval
import org.meetagain.app.core.push.PushObstacle

/**
 * What the settings screen knows about push on this phone: whether it can work at all, and why not when it cannot.
 * [permissionDenied] is Android's answer, which only the system settings can change.
 */
data class PushUiState(
    val obstacle: PushObstacle? = null,
    val interval: PushInterval = PushInterval.Hourly,
    val permissionDenied: Boolean = false,
    val askPermission: Boolean = false
)
