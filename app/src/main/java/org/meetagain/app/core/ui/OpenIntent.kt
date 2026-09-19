package org.meetagain.app.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** Opens an intent in the member's own apps; when none can handle it (no browser, no map app) nothing happens. */
@Composable
fun rememberOpenIntent(): (Intent) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // Nothing to open it with; the screen stays as it is.
            }
        }
    }
}

@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val open = rememberOpenIntent()
    return remember(open) { { url -> open(Intent(Intent.ACTION_VIEW, url.toUri())) } }
}
