package org.meetagain.app.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** Opens an intent in the member's own apps; false when none can handle it (no browser, no map app). */
@Composable
fun rememberOpenIntent(): (Intent) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { intent ->
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

/** Opens a link in the member's browser; when there is none, nothing happens. */
@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val open = rememberOpenIntent()
    return remember(open) { { url -> open(Intent(Intent.ACTION_VIEW, url.toUri())) } }
}
