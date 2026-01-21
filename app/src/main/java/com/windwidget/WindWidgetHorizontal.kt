package com.windwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

        // Widget dimensions in dp (4x1 widget)
        private const val WIDGET_WIDTH_DP = 320
        private const val WIDGET_HEIGHT_DP = 80

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /**
         * Update a single widget instance
         */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            scope.launch {
                updateWidgetAsync(context, appWidgetManager, appWidgetId)
            }
        }

        /**
         * Update all horizontal widget instances
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WindWidgetHorizontal::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            widgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        }

        private suspend fun updateWidgetAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_wind_horizontal)

            // Show loading state
            views.setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            try {
                // Fetch wind data from Ecowitt
                val fetcher = EcowittDataFetcher(context)
                val windData = fetcher.fetch() ?: fetcher.generateDemoData()

                // Calculate widget size in pixels
                val density = context.resources.displayMetrics.density
                val widthPx = (WIDGET_WIDTH_DP * density).toInt()
                val heightPx = (WIDGET_HEIGHT_DP * density).toInt()

                // Render wind bar bitmap
                val renderer = WindBarRenderer(context)
                val bitmap = renderer.render(windData, widthPx, heightPx)

                // Update widget with chart
                views.setImageViewBitmap(R.id.windBarChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)

                // Set click intent for refresh
                val refreshIntent = Intent(context, WindWidgetHorizontal::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId + 1000,  // Offset to avoid collision with main widget
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
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
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        WindUpdateScheduler.scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Don't cancel updates - the main widget might still be active
    }
}
