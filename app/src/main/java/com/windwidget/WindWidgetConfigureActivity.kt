package com.windwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Configuration activity shown when adding a new widget instance.
 * Allows users to configure Ecowitt API credentials.
 */
class WindWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var fetcher: EcowittDataFetcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case user backs out
        setResult(Activity.RESULT_CANCELED)

        setContentView(R.layout.activity_configure)

        fetcher = EcowittDataFetcher(this)

        // Get the widget ID from the intent
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        val appKeyInput = findViewById<EditText>(R.id.appKeyInput)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val macAddressInput = findViewById<EditText>(R.id.macAddressInput)
        val locationInput = findViewById<EditText>(R.id.locationInput)
        val confirmButton = findViewById<Button>(R.id.confirmButton)

        // Load existing credentials if any
        val prefs = getSharedPreferences("wind_widget_prefs", MODE_PRIVATE)
        appKeyInput.setText(prefs.getString(EcowittDataFetcher.KEY_APPLICATION_KEY, ""))
        apiKeyInput.setText(prefs.getString(EcowittDataFetcher.KEY_API_KEY, ""))
        macAddressInput.setText(prefs.getString(EcowittDataFetcher.KEY_MAC_ADDRESS, ""))
        locationInput.setText(prefs.getString(EcowittDataFetcher.KEY_LOCATION_NAME, "São Miguel dos Milagres - Alagoas, MiCasa"))

        confirmButton.setOnClickListener {
            val appKey = appKeyInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()
            val macAddress = macAddressInput.text.toString().trim()
            val locationName = locationInput.text.toString().ifEmpty {
                "São Miguel dos Milagres - Alagoas, MiCasa"
            }

            // Validate inputs
            if (appKey.isEmpty() || apiKey.isEmpty() || macAddress.isEmpty()) {
                Toast.makeText(this, "Please fill in all Ecowitt API fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save credentials
            fetcher.saveCredentials(appKey, apiKey, macAddress, locationName)

            // Update the widget
            val appWidgetManager = AppWidgetManager.getInstance(this)
            WindWidget.updateWidget(this, appWidgetManager, appWidgetId)

            // Schedule periodic updates
            WindUpdateScheduler.scheduleUpdates(this)

            // Return success
            val resultIntent = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
