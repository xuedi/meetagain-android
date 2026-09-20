package org.meetagain.app.core.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import org.meetagain.app.MeetAgainApp

/** How often a phone without a push app looks for news. Android will not run periodic work more often than 15 minutes. */
enum class PushInterval(val minutes: Long) {
    QuarterHour(15),
    Hourly(60),
    SixHourly(360)
}

/**
 * The fallback for a member with no distributor installed, and the safety net for one who has: the same check as a
 * ping, on a timer. No battery exemption is asked for, so Android may run it late - which is the honest cost of the
 * app working without Google.
 */
object PushTimer {
    fun start(context: Context, interval: PushInterval) {
        val request = PeriodicWorkRequestBuilder<PushWorker>(Duration.ofMinutes(interval.minutes))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private const val NAME = "push-check"
}

class PushWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MeetAgainApp).container
        return runCatching { container.pushWork().run(fromTimer = true) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
