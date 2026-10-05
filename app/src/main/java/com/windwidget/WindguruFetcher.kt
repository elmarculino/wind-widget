package com.windwidget

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Current wind from a Windguru station (the same endpoint the Echo Show dashboard uses). No key is
 * needed, but Windguru answers 401 "Unauthorized!" unless the request carries the station page as
 * Referer. Speeds come in knots.
 */
class WindguruFetcher(
    private val prefs: SharedPreferences,
    private val client: OkHttpClient = HttpClients.shared
) {

    companion object {
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L
        /** A station that hasn't reported for this long is shown as offline (amber time). */
        internal const val READING_STALE_MS = 60 * 60 * 1000L
        private const val USER_AGENT = "Mozilla/5.0 (compatible; WindWidget/1.0)"

        private fun dataKey(stationId: Int) = "wind_${stationId}_data"
        private fun timeKey(stationId: Int) = "wind_${stationId}_time"
    }

    private val gson = Gson()

    /**
     * Fresh cache, else a live reading, else the last real reading marked STALE, else demo data
     * (DEMO) when the station has never answered. Demo data is never cached.
     */
    suspend fun fetch(location: WindTideLocation, forceRefresh: Boolean = false): WindData {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cached(location, now, ignoreExpiry = false)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            try {
                fetchCurrent(location.stationId, location.name).also { data ->
                    prefs.edit()
                        .putString(dataKey(location.stationId), gson.toJson(data))
                        .putLong(timeKey(location.stationId), now)
                        .apply()
                }.withReadingAge(now)
            } catch (e: Exception) {
                e.printStackTrace()
                cached(location, now, ignoreExpiry = true)?.copy(dataStatus = WindDataStatus.STALE)
                    ?: demo(location.name)
            }
        }
    }

    /** One request to the station; throws on any error (also used to validate a new place). */
    internal fun fetchCurrent(stationId: Int, locationName: String): WindData {
        val url = "https://www.windguru.cz/int/iapi.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", "station_data_current")
            .addQueryParameter("id_station", stationId.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://www.windguru.cz/station/$stationId")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = try {
                JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            } catch (e: Exception) {
                null
            }
            // Errors come as {"return":"error","message":"Unknown station!"}, with 400/401
            if (json == null || json.str("return") == "error" || !response.isSuccessful) {
                throw IOException("Windguru ${response.code}: ${json?.str("message") ?: "bad response"}")
            }
            return parseCurrent(json, locationName)
        }
    }

    internal fun parseCurrent(json: JsonObject, locationName: String): WindData {
        val avg = json.float("wind_avg") ?: throw IOException("Windguru: no wind_avg")
        return WindData(
            locationName = locationName,
            times = emptyList(),
            speeds = emptyList(),
            directions = emptyList(),
            gusts = emptyList(),
            currentSpeed = avg,
            currentDirection = json.float("wind_direction") ?: 0f,
            currentGust = json.float("wind_max") ?: avg,
            temperatureC = json.float("temperature"),
            dataStatus = WindDataStatus.LIVE,
            // The station's own reading time, so the header shows how old the reading is
            lastUpdatedMillis = json.get("unixtime")?.takeIf { !it.isJsonNull }?.asLong?.times(1000)
                ?: System.currentTimeMillis()
        )
    }

    private fun cached(location: WindTideLocation, now: Long, ignoreExpiry: Boolean): WindData? {
        val cacheTime = prefs.getLong(timeKey(location.stationId), 0)
        if (!ignoreExpiry && now - cacheTime > CACHE_DURATION_MS) return null
        val json = prefs.getString(dataKey(location.stationId), null) ?: return null
        return try {
            gson.fromJson(json, WindData::class.java)
                .copy(locationName = location.name, dataStatus = WindDataStatus.CACHED)
                .withReadingAge(now)
        } catch (e: Exception) {
            null
        }
    }

    /** A station can keep answering with an old reading; that must not look live. */
    private fun WindData.withReadingAge(now: Long): WindData =
        if (now - lastUpdatedMillis > READING_STALE_MS) copy(dataStatus = WindDataStatus.STALE) else this

    private fun demo(locationName: String) = WindData(
        locationName = locationName,
        times = emptyList(),
        speeds = emptyList(),
        directions = emptyList(),
        gusts = emptyList(),
        currentSpeed = 12.5f,
        currentDirection = 100f,
        currentGust = 16.0f,
        dataStatus = WindDataStatus.DEMO
    )

    private fun JsonObject.element(name: String): JsonElement? = get(name)?.takeIf { !it.isJsonNull }
    private fun JsonObject.str(name: String): String? = element(name)?.asString
    private fun JsonObject.float(name: String): Float? = element(name)?.asFloat
}
