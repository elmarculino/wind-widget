package com.windwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compact 2x2 Wind Widget Provider
 * Shows direction, speed, and gust only.
 */
class WindWidgetCompact : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.windwidget.ACTION_REFRESH_COMPACT"
        private const val DEFAULT_SIZE_DP = 120

        suspend fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            forceRefresh: Boolean = false
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_wind_compact)

            // Only turn the spinner on: a full update here would blank the widget and drop its tap handler
            val loading = RemoteViews(context.packageName, R.layout.widget_wind_compact).apply {
                setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            }
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loading)

            // Tap to refresh, set up front so the error path keeps it too
            val refreshIntent = Intent(context, WindWidgetCompact::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 3000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            try {
                val fetcher = EcowittDataFetcher(context, appWidgetId)
                val windData = fetcher.fetch(forceRefresh) ?: fetcher.generateDemoData()

                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val (widthPx, heightPx) = getWidgetSizeInPixels(context, options)

                val renderer = WindCompactRenderer(context)
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.render(windData, widthPx, heightPx)
                }

                views.setImageViewBitmap(R.id.windChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getWidgetSizeInPixels(context: Context, options: Bundle): Pair<Int, Int> {
            val density = context.resources.displayMetrics.density
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_SIZE_DP)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
            val widthDp = maxOf(minWidthDp, maxWidthDp, DEFAULT_SIZE_DP)

            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_SIZE_DP)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            val heightDp = maxOf(minHeightDp, maxHeightDp, DEFAULT_SIZE_DP)

            val scaleFactor = 2.0f
            return Pair(
                (widthDp * density * scaleFactor).toInt(),
                (heightDp * density * scaleFactor).toInt()
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WindUpdateScheduler.enqueueUpdate(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        WindUpdateScheduler.enqueueUpdate(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WindUpdateScheduler.enqueueUpdate(context, intArrayOf(appWidgetId), forceRefresh = true)
            }
        }
    }

    override fun onEnabled(context: Context) {
        WindUpdateScheduler.scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        WindUpdateScheduler.cancelUpdatesIfNoWidgets(context)
    }
}
