package org.meetagain.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.meetagain.app.R

/**
 * One photo over the whole screen, on black whatever the theme, with what it shows underneath and one way on from
 * it. Back or the close button leaves it.
 */
@Composable
fun PhotoViewer(
    url: String,
    title: String,
    detail: String?,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.town_hall_close))
                }
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = onAction) { Text(actionLabel, color = Color.White) }
                }
            }
        }
    }
}
