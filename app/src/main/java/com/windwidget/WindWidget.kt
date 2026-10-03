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
 * Wind Chart Widget Provider (4x2)
 */
class WindWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.windwidget.ACTION_REFRESH"
        private const val DEFAULT_WIDTH_DP = 360
        private const val DEFAULT_HEIGHT_DP = 180

        suspend fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            forceRefresh: Boolean = false
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_wind)

            views.setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            views.setViewVisibility(R.id.errorOverlay, View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            try {
                val fetcher = EcowittDataFetcher(context, appWidgetId)
                val windData = fetcher.fetch(forceRefresh) ?: fetcher.generateDemoData()

                // Get actual widget size
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val (widthPx, heightPx) = getWidgetSizeInPixels(context, options)

                val renderer = WindChartRenderer(context)
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.render(windData, widthPx, heightPx)
                }

                views.setImageViewBitmap(R.id.windChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
                views.setViewVisibility(R.id.errorOverlay, View.GONE)

                val refreshIntent = Intent(context, WindWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, appWidgetId, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
                views.setViewVisibility(R.id.errorOverlay, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getWidgetSizeInPixels(context: Context, options: Bundle): Pair<Int, Int> {
            val density = context.resources.displayMetrics.density
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
            val widthDp = maxOf(minWidthDp, maxWidthDp, DEFAULT_WIDTH_DP)

            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
            val heightDp = maxOf(minHeightDp, maxHeightDp, DEFAULT_HEIGHT_DP)

            // Render at 2x for high-DPI displays
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
