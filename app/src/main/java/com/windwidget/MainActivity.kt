package com.windwidget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Main activity - manages saved locations and provides instructions for adding widgets.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var locationsAdapter: LocationsAdapter
    private lateinit var locationsRecyclerView: RecyclerView
    private lateinit var emptyLocationsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = LocationManager(this)

        setupUI()
        loadLocations()

        // Start periodic updates
        WindUpdateScheduler.scheduleUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        loadLocations()
    }

    private fun setupUI() {
        // Setup RecyclerView
        locationsRecyclerView = findViewById(R.id.locationsRecyclerView)
        emptyLocationsText = findViewById(R.id.emptyLocationsText)

        locationsAdapter = LocationsAdapter(
            onDeleteClick = { location ->
                showDeleteConfirmation(location)
            }
        )

        locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = locationsAdapter
        }

        // Setup form
        val locationNameInput = findViewById<EditText>(R.id.locationNameInput)
        val macAddressInput = findViewById<EditText>(R.id.macAddressInput)
        val appKeyInput = findViewById<EditText>(R.id.appKeyInput)
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val addButton = findViewById<Button>(R.id.addLocationButton)

        addButton.setOnClickListener {
            val name = locationNameInput.text.toString().trim()
            val mac = macAddressInput.text.toString().trim().uppercase()
            val appKey = appKeyInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()

            // Validate inputs
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mac.isEmpty()) {
                Toast.makeText(this, "Please enter a MAC address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (appKey.isEmpty()) {
                Toast.makeText(this, "Please enter an Application Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save the location
            val location = SavedLocation(
                name = name,
                macAddress = mac,
                applicationKey = appKey,
                apiKey = apiKey
            )

            locationManager.addLocation(location)

            // Clear form
            locationNameInput.text.clear()
            macAddressInput.text.clear()
            appKeyInput.text.clear()
            apiKeyInput.text.clear()

            Toast.makeText(this, "Location saved", Toast.LENGTH_SHORT).show()

            // Reload list
            loadLocations()
        }
    }

    private fun loadLocations() {
        val locations = locationManager.getLocations()
        locationsAdapter.submitList(locations)

        if (locations.isEmpty()) {
            emptyLocationsText.visibility = View.VISIBLE
            locationsRecyclerView.visibility = View.GONE
        } else {
            emptyLocationsText.visibility = View.GONE
            locationsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmation(location: SavedLocation) {
        AlertDialog.Builder(this)
            .setTitle("Delete Location")
            .setMessage("Are you sure you want to delete '${location.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                locationManager.deleteLocation(location.id)
                loadLocations()
                Toast.makeText(this, "Location deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

/**
 * RecyclerView adapter for displaying saved locations.
 */
class LocationsAdapter(
    private val onDeleteClick: (SavedLocation) -> Unit
) : RecyclerView.Adapter<LocationsAdapter.ViewHolder>() {

    private var locations: List<SavedLocation> = emptyList()

    fun submitList(newLocations: List<SavedLocation>) {
        locations = newLocations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(locations[position])
    }

    override fun getItemCount(): Int = locations.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.locationName)
        private val macText: TextView = itemView.findViewById(R.id.locationMac)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(location: SavedLocation) {
            nameText.text = location.name
            macText.text = location.macAddress

            deleteButton.setOnClickListener {
                onDeleteClick(location)
            }
        }
    }
}
