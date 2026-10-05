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
 * Horizontal 4x1 Wind Bar Widget Provider
 *
 * Displays a compact wind bar with:
 * - Location name and current time
 * - Current wind speed and direction
 * - Color-coded wind speed bar (3-hour history)
 * - Time labels
 */
class WindWidgetHorizontal : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.windwidget.ACTION_REFRESH_HORIZONTAL"

        // Fallback dimensions in dp (if we can't get actual size)
        private const val DEFAULT_WIDTH_DP = 360
        private const val DEFAULT_HEIGHT_DP = 100

        suspend fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            forceRefresh: Boolean = false
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_wind_horizontal)

            // Only turn the spinner on: a full update here would blank the widget and drop its tap handler
            val loading = RemoteViews(context.packageName, R.layout.widget_wind_horizontal).apply {
                setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            }
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loading)

            // Tap to refresh, set up front so the error path keeps it too
            val refreshIntent = Intent(context, WindWidgetHorizontal::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            try {
                // Fetch wind data from Ecowitt
                val fetcher = EcowittDataFetcher(context, appWidgetId)
                val windData = fetcher.fetch(forceRefresh) ?: fetcher.generateDemoData()

                // Get actual widget size
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val (widthPx, heightPx) = getWidgetSizeInPixels(context, options)

                // Render wind bar bitmap
                val renderer = WindBarRenderer(context)
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.render(windData, widthPx, heightPx)
                }

                // Update widget with chart
                views.setImageViewBitmap(R.id.windBarChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Get widget size in pixels from AppWidgetManager options
         */
        private fun getWidgetSizeInPixels(context: Context, options: Bundle): Pair<Int, Int> {
            val density = context.resources.displayMetrics.density

            // Get width - use max width for landscape, min for portrait
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
            val widthDp = maxOf(minWidthDp, maxWidthDp, DEFAULT_WIDTH_DP)

            // Get height
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            val heightDp = maxOf(minHeightDp, maxHeightDp, DEFAULT_HEIGHT_DP)

            // Convert to pixels with higher resolution for crisp rendering
            val scaleFactor = 2.0f  // Render at 2x for sharpness
            val widthPx = (widthDp * density * scaleFactor).toInt()
            val heightPx = (heightDp * density * scaleFactor).toInt()

            return Pair(widthPx, heightPx)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WindUpdateScheduler.enqueueUpdate(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Re-render when widget is resized
        WindUpdateScheduler.enqueueUpdate(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WindUpdateScheduler.enqueueUpdate(context, intArrayOf(appWidgetId), forceRefresh = true)
                }
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
