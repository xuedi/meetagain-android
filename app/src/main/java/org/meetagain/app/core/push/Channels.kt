package org.meetagain.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import org.meetagain.app.R
import org.meetagain.app.core.data.PushCategory

/**
 * One Android channel per push category, so the list in Android's own settings is the list the app shows. The
 * member can then silence one kind without the app inventing a second set of switches for it.
 *
 * Importance follows how urgent the category can be: a meeting called off may interrupt, the rest arrive quietly.
 */
object Channels {
    fun create(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        PushCategory.entries.forEach { category ->
            manager.createNotificationChannel(
                NotificationChannel(category.channelId, context.getString(category.channelName), category.importance)
            )
        }
    }
}

val PushCategory.channelId: String get() = "category-$key"

private val PushCategory.importance: Int
    get() = when (this) {
        PushCategory.EventChanges -> NotificationManager.IMPORTANCE_HIGH
        PushCategory.Reminders -> NotificationManager.IMPORTANCE_DEFAULT
        PushCategory.Messages -> NotificationManager.IMPORTANCE_DEFAULT
        PushCategory.Announcements -> NotificationManager.IMPORTANCE_LOW
    }

val PushCategory.channelName: Int
    get() = when (this) {
        PushCategory.EventChanges -> R.string.push_category_event_changes
        PushCategory.Reminders -> R.string.push_category_reminders
        PushCategory.Messages -> R.string.push_category_messages
        PushCategory.Announcements -> R.string.push_category_announcements
    }
