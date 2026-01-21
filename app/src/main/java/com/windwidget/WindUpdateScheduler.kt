package com.windwidget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules periodic widget updates using WorkManager.
 *
 * Updates every 30 minutes (Android widget minimum interval)
 * with network connectivity constraint.
 */
object WindUpdateScheduler {

    private const val WORK_NAME = "wind_widget_update"
    private const val UPDATE_INTERVAL_MINUTES = 30L

    /**
     * Schedule periodic widget updates
     */
    fun scheduleUpdates(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WindUpdateWorker>(
            UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Cancel all scheduled updates
     */
    fun cancelUpdates(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Force an immediate update (one-time work)
     */
    fun forceUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WindUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

/**
 * Worker that updates all wind widgets
 */
class WindUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            WindWidget.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retry on failure
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
