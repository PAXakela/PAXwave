package com.music.bitchord.data.pax

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Re-reads every subscribed feed a few times a day while the app is closed,
 * so new episodes are there — and, for shows set to it, already downloaded —
 * before the app is next opened.
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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PodcastRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
