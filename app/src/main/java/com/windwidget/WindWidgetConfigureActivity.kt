package com.windwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Configuration activity shown when adding a new widget instance.
 * Allows users to select a saved location or configure Ecowitt API credentials manually.
 */
class WindWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var fetcher: EcowittDataFetcher
    private lateinit var locationManager: LocationManager

    private lateinit var appKeyInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var macAddressInput: EditText
    private lateinit var locationInput: EditText
    private lateinit var locationSpinner: Spinner
    private lateinit var savedLocationsLabel: TextView
    private lateinit var divider: View
    private lateinit var manualEntryLabel: TextView

    private var savedLocations: List<SavedLocation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case user backs out
        setResult(Activity.RESULT_CANCELED)

        setContentView(R.layout.activity_configure)

        // Get the widget ID from the intent
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        fetcher = EcowittDataFetcher(this, appWidgetId)
        locationManager = LocationManager(this)

        setupUI()
    }

    private fun setupUI() {
        appKeyInput = findViewById(R.id.appKeyInput)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        macAddressInput = findViewById(R.id.macAddressInput)
        locationInput = findViewById(R.id.locationInput)
        locationSpinner = findViewById(R.id.locationSpinner)
        savedLocationsLabel = findViewById(R.id.savedLocationsLabel)
        divider = findViewById(R.id.divider)
        manualEntryLabel = findViewById(R.id.manualEntryLabel)
        val confirmButton = findViewById<Button>(R.id.confirmButton)

        // Load saved locations
        savedLocations = locationManager.getLocations()

        if (savedLocations.isNotEmpty()) {
            setupLocationSpinner()
        } else {
            // Load existing credentials if any
            loadExistingCredentials()
        }

        confirmButton.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupLocationSpinner() {
        savedLocationsLabel.visibility = View.VISIBLE
        locationSpinner.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE
        manualEntryLabel.visibility = View.VISIBLE

        // Create spinner items: "Select a location..." + saved locations + "Enter manually"
        val spinnerItems = mutableListOf("Select a location...")
        spinnerItems.addAll(savedLocations.map { it.name })
        spinnerItems.add("Enter manually")

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        locationSpinner.adapter = adapter

        locationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when {
                    position == 0 -> {
                        // "Select a location..." - load existing credentials
                        loadExistingCredentials()
                    }
                    position <= savedLocations.size -> {
                        // A saved location was selected
                        val location = savedLocations[position - 1]
                        fillFieldsFromLocation(location)
                    }
                    else -> {
                        // "Enter manually" - clear fields
                        clearFields()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                loadExistingCredentials()
            }
        }
    }

    private fun loadExistingCredentials() {
        val credentials = fetcher.loadCredentials()
        appKeyInput.setText(credentials?.applicationKey ?: "")
        apiKeyInput.setText(credentials?.apiKey ?: "")
        macAddressInput.setText(credentials?.macAddress ?: "")
        locationInput.setText(credentials?.locationName ?: "")
    }

    private fun fillFieldsFromLocation(location: SavedLocation) {
        appKeyInput.setText(location.applicationKey)
        apiKeyInput.setText(location.apiKey)
        macAddressInput.setText(location.macAddress)
        locationInput.setText(location.name)
    }

    private fun clearFields() {
        appKeyInput.text.clear()
        apiKeyInput.text.clear()
        macAddressInput.text.clear()
        locationInput.text.clear()
    }

    private fun saveAndFinish() {
        val appKey = appKeyInput.text.toString().trim()
        val apiKey = apiKeyInput.text.toString().trim()
        val macAddress = macAddressInput.text.toString().trim()
        val locationName = locationInput.text.toString().ifEmpty {
            "Wind Station"
        }

        // Validate inputs
        if (appKey.isEmpty() || apiKey.isEmpty() || macAddress.isEmpty()) {
            Toast.makeText(this, "Please fill in all Ecowitt API fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Save credentials
        fetcher.saveCredentials(appKey, apiKey, macAddress, locationName)

        // Fetch fresh data for the new credentials (bypass any cached reading)
        WindUpdateScheduler.enqueueUpdate(this, intArrayOf(appWidgetId), forceRefresh = true)

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
