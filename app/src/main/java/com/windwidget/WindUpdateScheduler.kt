package com.windwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules widget updates using WorkManager.
 *
 * All updates (periodic, tap-to-refresh, resize, boot, configure) run inside
 * [WindUpdateWorker] so the fetch and render finish while WorkManager keeps
 * the process alive, instead of in a fire-and-forget coroutine.
 */
object WindUpdateScheduler {

    private const val WORK_NAME = "wind_widget_update"
    private const val UPDATE_INTERVAL_MINUTES = 30L

    internal const val KEY_WIDGET_IDS = "widget_ids"
    internal const val KEY_FORCE_REFRESH = "force_refresh"

    private val PROVIDERS = listOf(
        WindWidget::class.java,
        WindWidgetHorizontal::class.java,
        WindWidgetClean::class.java,
        WindWidgetCompact::class.java,
        WindWidgetModern::class.java,
        WindWidgetTide::class.java
    )

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
        if (allWidgetIds(context).isEmpty()) {
            cancelUpdates(context)
        }
    }

    /**
     * Update the given widgets now (or all widgets when [appWidgetIds] is null).
     *
     * No network constraint: when offline the fetcher falls back to the stale
     * cache, so the widget still re-renders (e.g. after a resize).
     */
    fun enqueueUpdate(
        context: Context,
        appWidgetIds: IntArray? = null,
        forceRefresh: Boolean = false
    ) {
        val input = Data.Builder().putBoolean(KEY_FORCE_REFRESH, forceRefresh)
        if (appWidgetIds != null) {
            input.putIntArray(KEY_WIDGET_IDS, appWidgetIds)
        }

        val workRequest = OneTimeWorkRequestBuilder<WindUpdateWorker>()
            .setInputData(input.build())
            .build()

        val uniqueName = "${WORK_NAME}_" + (appWidgetIds?.sorted()?.joinToString("_") ?: "all")
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Force an immediate refresh of every widget
     */
    fun forceUpdate(context: Context) {
        enqueueUpdate(context, forceRefresh = true)
    }

    internal fun allWidgetIds(context: Context): IntArray = widgetIds(context, PROVIDERS)

    /** Widgets that read Ecowitt credentials (all but wind + tide). */
    internal fun ecowittWidgetIds(context: Context): IntArray =
        widgetIds(context, PROVIDERS - WindWidgetTide::class.java)

    private fun widgetIds(context: Context, providers: List<Class<*>>): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return providers.flatMap { provider ->
            appWidgetManager.getAppWidgetIds(ComponentName(context, provider)).toList()
        }.toIntArray()
    }

    /**
     * Fetch and render one widget, dispatching to the provider that owns it.
     * Suspends until the widget has been updated.
     */
    internal suspend fun updateWidget(context: Context, appWidgetId: Int, forceRefresh: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        when (appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider?.className) {
            WindWidget::class.java.name -> WindWidget.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            WindWidgetHorizontal::class.java.name -> WindWidgetHorizontal.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            WindWidgetClean::class.java.name -> WindWidgetClean.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            WindWidgetCompact::class.java.name -> WindWidgetCompact.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            WindWidgetModern::class.java.name -> WindWidgetModern.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            WindWidgetTide::class.java.name -> WindWidgetTide.updateWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            // Widget was removed before the work ran
            else -> Unit
        }
    }
}

/**
 * Worker that updates wind widgets and only returns once they are rendered.
 */
class WindUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceRefresh = inputData.getBoolean(WindUpdateScheduler.KEY_FORCE_REFRESH, false)
        val widgetIds = inputData.getIntArray(WindUpdateScheduler.KEY_WIDGET_IDS)
            ?: WindUpdateScheduler.allWidgetIds(applicationContext)

        // Each provider handles its own fetch/render errors (stale cache or
        // error overlay), so a single widget failing never fails the batch.
        widgetIds.forEach { appWidgetId ->
            WindUpdateScheduler.updateWidget(applicationContext, appWidgetId, forceRefresh)
        }
        return Result.success()
    }
}
