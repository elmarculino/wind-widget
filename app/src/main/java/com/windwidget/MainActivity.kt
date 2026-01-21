package com.windwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Main activity - displays a preview of the widget and settings.
 * This is optional but provides a way for users to preview the widget
 * before adding it to their home screen.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start periodic updates
        WindUpdateScheduler.scheduleUpdates(this)
    }
}
