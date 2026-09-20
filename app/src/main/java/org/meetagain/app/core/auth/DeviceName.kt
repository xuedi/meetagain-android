package org.meetagain.app.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * The name the member sees next to this installation at `/profile/access-tokens`, for instance "Pixel 8 (4f2a)".
 *
 * Signing in revokes the member's earlier token with the same name, so it has to be the same on every sign-in from
 * this installation and different from their other phones: the model alone collides between two phones of one model.
 * The suffix comes from the installation id, which is stable for this app on this device, reinstalls included.
 */
@SuppressLint("HardwareIds")
fun deviceName(context: Context): String {
    val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    val suffix = id.filter { it.isLetterOrDigit() }.takeLast(4).lowercase(Locale.ROOT)
    val model = Build.MODEL.trim().ifEmpty { Build.DEVICE.trim() }.ifEmpty { "Android" }
    val name = if (suffix.isEmpty()) model else "$model ($suffix)"
    return name.take(MAX_LENGTH)
}

/** The server rejects a longer name with `invalid_device_name`. */
private const val MAX_LENGTH = 80
