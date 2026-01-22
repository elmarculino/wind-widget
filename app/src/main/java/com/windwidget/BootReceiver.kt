package com.windwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts widget updates after device reboot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule periodic updates
            WindUpdateScheduler.scheduleUpdates(context)

            // Trigger immediate update for all widget types
            WindWidget.updateAllWidgets(context)
            WindWidgetHorizontal.updateAllWidgets(context)
            WindWidgetClean.updateAllWidgets(context)
        }
    }
}
