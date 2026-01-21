package com.windwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wind Chart Widget Provider
 *
 * Displays a modern wind forecast chart with:
 * - Wind speed line chart with gradient fill
 * - Wind direction arrows
 * - Gust indicators
 * - Bottom bar with current conditions
 */
class WindWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.windwidget.ACTION_REFRESH"

        // Widget dimensions in dp (will be converted to px in renderer)
        private const val WIDGET_WIDTH_DP = 320
        private const val WIDGET_HEIGHT_DP = 200

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
         * Update all widget instances
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WindWidget::class.java)
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
            val views = RemoteViews(context.packageName, R.layout.widget_wind)

            // Show loading state
            views.setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            views.setViewVisibility(R.id.errorOverlay, View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            try {
                // Fetch wind data from Ecowitt
                val fetcher = EcowittDataFetcher(context)
                val windData = fetcher.fetch() ?: fetcher.generateDemoData()

                // Calculate widget size in pixels
                val density = context.resources.displayMetrics.density
                val widthPx = (WIDGET_WIDTH_DP * density).toInt()
                val heightPx = (WIDGET_HEIGHT_DP * density).toInt()

                // Render chart bitmap
                val renderer = WindChartRenderer(context)
                val bitmap = renderer.render(windData, widthPx, heightPx)

                // Update widget with chart
                views.setImageViewBitmap(R.id.windChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
                views.setViewVisibility(R.id.errorOverlay, View.GONE)

                // Set click intent for refresh
                val refreshIntent = Intent(context, WindWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            } catch (e: Exception) {
                e.printStackTrace()
                // Show error state
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
                views.setViewVisibility(R.id.errorOverlay, View.VISIBLE)

                // Set click to retry
                val retryIntent = Intent(context, WindWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val retryPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.errorOverlay, retryPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
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
        // Start periodic updates when first widget is added
        WindUpdateScheduler.scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Cancel updates when last widget is removed
        WindUpdateScheduler.cancelUpdates(context)
    }
}
