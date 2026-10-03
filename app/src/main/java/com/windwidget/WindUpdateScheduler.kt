package com.windwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

    fun cancelUpdatesIfNoWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val hasWidgets = listOf(
            WindWidget::class.java,
            WindWidgetHorizontal::class.java,
            WindWidgetClean::class.java,
            WindWidgetCompact::class.java,
            WindWidgetModern::class.java
        ).any { provider ->
            appWidgetManager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }

        if (!hasWidgets) {
            cancelUpdates(context)
        }
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
            // Update all widget types
            WindWidget.updateAllWidgets(applicationContext)
            WindWidgetHorizontal.updateAllWidgets(applicationContext)
            WindWidgetClean.updateAllWidgets(applicationContext)
            WindWidgetCompact.updateAllWidgets(applicationContext)
            WindWidgetModern.updateAllWidgets(applicationContext)
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
