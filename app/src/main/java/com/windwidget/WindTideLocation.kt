package com.windwidget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * A place for the wind + tide widget: a Windguru station for the wind and a tabuasdemare.com.br
 * page for the tide. Neither needs credentials, so these live in plain (not encrypted) prefs.
 */
data class WindTideLocation(
    val id: String,
    val name: String,
    val stationId: Int,
    val tideSlug: String
) {
    companion object {
        /** Default place, the same pair the Echo Show dashboard (echo-show-vento) uses. */
        val PONTA_VERDE = WindTideLocation(
            id = "ponta-verde",
            name = "Farol da Ponta Verde · Maceió",
            stationId = 15572,
            tideSlug = "praia-de-ponta-verde"
        )

        /** Accepts "15572" or a station link such as https://www.windguru.cz/station/15572. */
        fun parseStationId(input: String): Int? {
            val trimmed = input.trim().trimEnd('/')
            val digits = if (trimmed.all { it.isDigit() }) trimmed
            else Regex("""/station/(\d+)""").find(trimmed)?.groupValues?.get(1)
            return digits?.toIntOrNull()?.takeIf { it > 0 }
        }

        /** Accepts "praia-de-ponta-verde" or the page link https://tabuasdemare.com.br/praia-de-ponta-verde. */
        fun parseTideSlug(input: String): String? {
            val path = input.trim().substringBefore('?').substringBefore('#').trimEnd('/')
            val slug = path.substringAfterLast('/').lowercase()
            return slug.takeIf { it.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) }
        }
    }
}

/**
 * Saved wind + tide places plus each widget's choice. A widget keeps its own copy of the place, so
 * deleting a place from the list never breaks a widget that already shows it.
 */
class WindTideLocations(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(prefs(context))

    companion object {
        const val PREFS_NAME = "wind_tide_prefs"
        private const val KEY_LOCATIONS = "locations"

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun widgetKey(appWidgetId: Int) = "widget_${appWidgetId}_location"
    }

    private val gson = Gson()

    /** Saved places; Ponta Verde always comes first and can't be removed. */
    fun all(): List<WindTideLocation> {
        val saved = prefs.getString(KEY_LOCATIONS, null)?.let { json ->
            try {
                gson.fromJson<List<WindTideLocation>>(json, object : TypeToken<List<WindTideLocation>>() {}.type)
            } catch (e: Exception) {
                null
            }
        }.orEmpty()
        return listOf(WindTideLocation.PONTA_VERDE) + saved.filter { it.id != WindTideLocation.PONTA_VERDE.id }
    }

    fun add(name: String, stationId: Int, tideSlug: String): WindTideLocation {
        val location = WindTideLocation(UUID.randomUUID().toString(), name.trim(), stationId, tideSlug)
        save(custom() + location)
        return location
    }

    fun delete(id: String) {
        save(custom().filter { it.id != id })
    }

    fun forWidget(appWidgetId: Int): WindTideLocation =
        prefs.getString(widgetKey(appWidgetId), null)?.let { json ->
            try {
                gson.fromJson(json, WindTideLocation::class.java)
            } catch (e: Exception) {
                null
            }
        } ?: WindTideLocation.PONTA_VERDE

    fun setForWidget(appWidgetId: Int, location: WindTideLocation) {
        prefs.edit().putString(widgetKey(appWidgetId), gson.toJson(location)).apply()
    }

    fun removeWidget(appWidgetId: Int) {
        prefs.edit().remove(widgetKey(appWidgetId)).apply()
    }

    private fun custom() = all().drop(1)

    private fun save(locations: List<WindTideLocation>) {
        prefs.edit().putString(KEY_LOCATIONS, gson.toJson(locations)).apply()
    }
}
