package org.meetagain.app.core.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.meetagain.app.MainActivity
import org.meetagain.app.R
import org.meetagain.app.core.data.PushCategory

/** One thing worth telling the member, already worded in their language. */
data class Raised(val key: String, val category: PushCategory, val text: String, val webUrl: String?)

/**
 * Puts what changed on the phone. Nothing here talks to the network: by the time a [Raised] arrives, the app has
 * already fetched and worked out what it means.
 */
interface Notifier {
    /** False when Android has not been given permission, in which case nothing is shown and nothing is recorded. */
    fun canPost(): Boolean

    fun post(raised: Raised)
}

class PushNotifier(private val context: Context) : Notifier {
    /** Before Android 13 there is no permission to hold, and notifications are allowed unless Android says not. */
    override fun canPost(): Boolean = allowed()

    override fun post(raised: Raised) {
        // The check is inline rather than behind canPost() so it is the statement Lint reads.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, raised.category.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(raised.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(raised.text))
            .setAutoCancel(true)
            .setContentIntent(open(raised.webUrl))
            .build()
        NotificationManagerCompat.from(context).notify(raised.key.hashCode(), notification)
    }

    /**
     * Tapping opens the app, at the screen the address belongs to when there is one - the same routing a link
     * tapped anywhere else on the phone goes through.
     */
    private fun allowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(webUrl: String?): PendingIntent {
        val intent = if (webUrl == null) {
            Intent(context, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, webUrl.toUri()).setClass(context, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            webUrl.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Spelled out rather than taken from Manifest, whose constant only exists from Android 13 on. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
