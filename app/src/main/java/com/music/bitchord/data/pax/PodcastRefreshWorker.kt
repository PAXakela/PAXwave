package com.music.bitchord.data.pax

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.data.settings.BatterySaver
import java.util.concurrent.TimeUnit

/**
 * Re-reads every subscribed feed a few times a day while the app is closed,
 * so new episodes are there — and, for shows set to it, already downloaded —
 * before the app is next opened. How often, and on which network and power
 * conditions, follows the battery settings in [BatterySaver].
 */
class PodcastRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (PodcastStore.podcasts.value.isEmpty()) return Result.success()
        PodcastStore.refreshAll()
        PodcastDownloads.refresh()
        return Result.success()
    }

    companion object {
        private const val NAME = "paxwave-podcast-refresh"

        /**
         * (Re)applies the schedule from the current battery settings. Called at
         * startup and whenever one of those settings changes; UPDATE keeps the
         * existing period's timing instead of restarting it from now.
         */
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            val hours = BatterySaver.podcastRefreshHours.value
            if (hours <= 0) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PodcastRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (BatterySaver.podcastRefreshWifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED,
                        )
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(BatterySaver.podcastRefreshWhileCharging.value)
                        .build(),
                )
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
