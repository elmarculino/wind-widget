package com.windwidget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Represents a saved location with Ecowitt API credentials.
 */
data class SavedLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val macAddress: String,
    val applicationKey: String,
    val apiKey: String
)

/**
 * Manages saving and loading locations from SharedPreferences.
 */
class LocationManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "wind_widget_locations"
        private const val KEY_LOCATIONS = "saved_locations"
    }

    private val prefs = SecurePrefs.get(context, PREFS_NAME)
    private val gson = Gson()
    private val appContext = context.applicationContext

    init {
        migrateLegacyLocationsIfNeeded()
    }

    /**
     * Get all saved locations.
     */
    fun getLocations(): List<SavedLocation> {
        val json = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedLocation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save a new location.
     */
    fun addLocation(location: SavedLocation) {
        val locations = getLocations().toMutableList()
        locations.add(location)
        saveLocations(locations)
    }

    /**
     * Delete a location by ID.
     */
    fun deleteLocation(id: String) {
        val locations = getLocations().filter { it.id != id }
        saveLocations(locations)
    }

    /**
     * Get a location by ID.
     */
    fun getLocation(id: String): SavedLocation? {
        return getLocations().find { it.id == id }
    }

    /**
     * Update an existing location.
     */
    fun updateLocation(location: SavedLocation) {
        val locations = getLocations().toMutableList()
        val index = locations.indexOfFirst { it.id == location.id }
        if (index >= 0) {
            locations[index] = location
            saveLocations(locations)
        }
    }

    private fun saveLocations(locations: List<SavedLocation>) {
        prefs.edit()
            .putString(KEY_LOCATIONS, gson.toJson(locations))
            .apply()
    }

    private fun migrateLegacyLocationsIfNeeded() {
        if (prefs.getString(KEY_LOCATIONS, null) != null) return

        val legacyPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyJson = legacyPrefs.getString(KEY_LOCATIONS, null) ?: return

        prefs.edit()
            .putString(KEY_LOCATIONS, legacyJson)
            .apply()
    }
}
