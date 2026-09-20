package org.meetagain.app.core.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import org.meetagain.app.core.network.Upload

/** What a member picked, ready to be sent: its name, type and size, and a way to read it while it is sent. */
fun Uri.asUpload(context: Context): Upload? {
    val resolver = context.contentResolver
    val type = resolver.getType(this) ?: return null
    var name = lastPathSegment ?: "photo"
    var length = -1L
    resolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { row ->
        if (row.moveToFirst()) {
            row.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !row.isNull(it) }
                ?.let { name = row.getString(it) }
            row.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !row.isNull(it) }
                ?.let { length = row.getLong(it) }
        }
    }
    if (length < 0) length = resolver.openAssetFileDescriptor(this, "r")?.use { it.length } ?: return null
    return Upload(name, type, length) { resolver.openInputStream(this) ?: error("cannot read $this") }
}

/** The most the server takes for a photo of an event, and for a profile picture. */
const val MAX_PHOTO_BYTES = 16L * 1024 * 1024
const val MAX_AVATAR_BYTES = 10L * 1024 * 1024

/**
 * A file in the app's own cache for the camera app to write into, handed over through the app's FileProvider so no
 * storage permission is needed.
 */
@Composable
fun rememberCameraTarget(): () -> Pair<Uri, File> {
    val context = LocalContext.current
    return remember(context) {
        {
            val folder = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File.createTempFile("camera", ".jpg", folder)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.photos",
                file
            ) to file
        }
    }
}
