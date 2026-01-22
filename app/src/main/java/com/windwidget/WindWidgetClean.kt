package com.windwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Clean Material Design style 4x2 Wind Widget Provider
 */
class WindWidgetClean : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.windwidget.ACTION_REFRESH_CLEAN"
        private const val DEFAULT_WIDTH_DP = 360
        private const val DEFAULT_HEIGHT_DP = 120

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            scope.launch {
                updateWidgetAsync(context, appWidgetManager, appWidgetId)
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WindWidgetClean::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            widgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        }

        private suspend fun updateWidgetAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_wind_clean)

            views.setViewVisibility(R.id.loadingIndicator, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            try {
                val fetcher = EcowittDataFetcher(context)
                val windData = fetcher.fetch() ?: fetcher.generateDemoData()

                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val (widthPx, heightPx) = getWidgetSizeInPixels(context, options)

                val renderer = WindCleanRenderer(context)
                val bitmap = renderer.render(windData, widthPx, heightPx)

                views.setImageViewBitmap(R.id.windChart, bitmap)
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)

                val refreshIntent = Intent(context, WindWidgetClean::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, appWidgetId + 2000, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            } catch (e: Exception) {
                e.printStackTrace()
                views.setViewVisibility(R.id.loadingIndicator, View.GONE)
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

            val scaleFactor = 2.0f
            return Pair(
                (widthDp * density * scaleFactor).toInt(),
                (heightDp * density * scaleFactor).toInt()
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            }
        }
    }

    override fun onEnabled(context: Context) {
        WindUpdateScheduler.scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        // Don't cancel updates - other widgets might still be active
    }
}
