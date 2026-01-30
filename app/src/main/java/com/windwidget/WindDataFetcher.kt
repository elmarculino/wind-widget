package com.windwidget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Fetches wind data from Ecowitt API:
 * - History endpoint: last 3 hours for the chart
 * - Real-time endpoint: current speed/direction for the bottom bar
 */
class EcowittDataFetcher(
    private val context: Context,
    private val appWidgetId: Int? = null
) {

    companion object {
        private const val PREFS_NAME = "wind_widget_prefs"
        private const val KEY_CACHED_DATA = "cached_wind_data"
        private const val KEY_CACHE_TIME = "cache_time"
        private const val KEY_MIGRATION_COMPLETED_V1 = "migration_completed_v1"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

        // Ecowitt API settings keys
        const val KEY_APPLICATION_KEY = "ecowitt_app_key"
        const val KEY_API_KEY = "ecowitt_api_key"
        const val KEY_MAC_ADDRESS = "ecowitt_mac"
        const val KEY_LOCATION_NAME = "location_name"

        // Wind speed unit: 8 = knots
        private const val WIND_SPEED_UNIT_KNOTS = 8

        // Default location name
        private const val DEFAULT_LOCATION = "São Miguel dos Milagres - Alagoas, MiCasa"
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs: SharedPreferences by lazy {
        SecurePrefs.get(context, PREFS_NAME)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    init {
        migrateLegacyPrefsIfNeeded()
    }

    /**
     * Fetch wind data, using cache if available and fresh.
     */
    suspend fun fetch(forceRefresh: Boolean = false): WindData? {
        // Check cache first
        if (!forceRefresh) {
            getCachedData(status = WindDataStatus.CACHED)?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            try {
                fetchFromEcowitt()?.also { data ->
                    cacheData(data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Return cached data even if stale on error
                getCachedData(ignoreExpiry = true, status = WindDataStatus.STALE) ?: generateDemoData()
            }
        }
    }

    private suspend fun fetchFromEcowitt(): WindData? = withContext(Dispatchers.IO) {
        val credentials = getCredentials()
        val appKey = credentials?.applicationKey
        val apiKey = credentials?.apiKey
        val mac = credentials?.macAddress
        val locationName = credentials?.locationName ?: DEFAULT_LOCATION

        if (appKey.isNullOrEmpty() || apiKey.isNullOrEmpty() || mac.isNullOrEmpty()) {
            // No credentials configured, return demo data
            return@withContext generateDemoData()
        }

        // Fetch both history and real-time data in parallel
        val historyDeferred = async { fetchHistory(appKey, apiKey, mac) }
        val realtimeDeferred = async { fetchRealtime(appKey, apiKey, mac) }

        val historyData = historyDeferred.await()
        val realtimeData = realtimeDeferred.await()

        // If we have no history, fall back to demo
        if (historyData == null) {
            return@withContext generateDemoData()
        }

        // Combine history with real-time current values
        WindData(
            locationName = locationName,
            times = historyData.times,
            speeds = historyData.speeds,
            directions = historyData.directions,
            gusts = historyData.gusts,
            currentSpeed = realtimeData?.speed ?: historyData.speeds.lastOrNull() ?: 0f,
            currentDirection = realtimeData?.direction ?: historyData.directions.lastOrNull() ?: 0f,
            currentGust = realtimeData?.gust ?: historyData.gusts.lastOrNull() ?: 0f,
            dataStatus = WindDataStatus.LIVE,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    /**
     * Fetch last 3 hours of history data for the chart
     */
    private fun fetchHistory(appKey: String, apiKey: String, mac: String): HistoryResult? {
        val endDate = Date()
        val startDate = Date(endDate.time - 3 * 60 * 60 * 1000) // 3 hours ago

        val url = "https://api.ecowitt.net/api/v3/device/history?" +
                "mac=${URLEncoder.encode(mac, "UTF-8")}" +
                "&start_date=${URLEncoder.encode(dateFormat.format(startDate), "UTF-8")}" +
                "&end_date=${URLEncoder.encode(dateFormat.format(endDate), "UTF-8")}" +
                "&cycle_type=5min" +
                "&call_back=wind" +
                "&wind_speed_unitid=$WIND_SPEED_UNIT_KNOTS"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Application-Key", appKey)
            .header("X-API-Key", apiKey)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = response.body?.string() ?: return null
            parseHistoryResponse(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch real-time current wind data
     */
    private fun fetchRealtime(appKey: String, apiKey: String, mac: String): RealtimeResult? {
        val url = "https://api.ecowitt.net/api/v3/device/real_time?" +
                "mac=${URLEncoder.encode(mac, "UTF-8")}" +
                "&call_back=wind" +
                "&wind_speed_unitid=$WIND_SPEED_UNIT_KNOTS"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Application-Key", appKey)
            .header("X-API-Key", apiKey)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = response.body?.string() ?: return null
            parseRealtimeResponse(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun parseHistoryResponse(json: String): HistoryResult? {
        return try {
            val response = Gson().fromJson(json, EcowittHistoryResponse::class.java)

            if (response.code != 0 || response.data?.wind == null) {
                return null
            }

            val windData = response.data.wind
            val speedList = windData.windSpeed?.list ?: return null
            val gustList = windData.windGust?.list ?: emptyMap()
            val directionList = windData.windDirection?.list ?: emptyMap()

            // Sort timestamps chronologically
            val timestamps = speedList.keys.mapNotNull { it.toLongOrNull() }.sorted()

            // Take all points (up to 36 for 3 hours at 5-min intervals)
            val recentTimestamps = timestamps.takeLast(36)

            if (recentTimestamps.isEmpty()) return null

            val times = recentTimestamps.map { ts ->
                formatTimestamp(ts)
            }

            val speeds = recentTimestamps.map { ts ->
                speedList[ts.toString()]?.toFloatOrNull() ?: 0f
            }

            val directions = recentTimestamps.map { ts ->
                directionList[ts.toString()]?.toFloatOrNull() ?: 0f
            }

            val gusts = recentTimestamps.map { ts ->
                gustList[ts.toString()]?.toFloatOrNull() ?: 0f
            }

            HistoryResult(times, speeds, directions, gusts)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun parseRealtimeResponse(json: String): RealtimeResult? {
        return try {
            val response = Gson().fromJson(json, EcowittRealtimeResponse::class.java)

            if (response.code != 0 || response.data?.wind == null) {
                return null
            }

            val wind = response.data.wind

            // Values come directly as the current reading
            val speed = wind.windSpeed?.value?.toFloatOrNull() ?: 0f
            val gust = wind.windGust?.value?.toFloatOrNull() ?: 0f
            val direction = wind.windDirection?.value?.toFloatOrNull() ?: 0f

            RealtimeResult(speed, direction, gust)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(date)
    }

    private fun getCachedData(
        ignoreExpiry: Boolean = false,
        status: WindDataStatus = WindDataStatus.CACHED
    ): WindData? {
        val cacheTime = prefs.getLong(widgetKey(KEY_CACHE_TIME), 0)
        val now = System.currentTimeMillis()

        if (!ignoreExpiry && now - cacheTime > CACHE_DURATION_MS) {
            return null
        }

        val json = prefs.getString(widgetKey(KEY_CACHED_DATA), null) ?: return null
        return try {
            val cached = Gson().fromJson(json, WindData::class.java)
            cached.copy(dataStatus = status)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheData(data: WindData) {
        prefs.edit()
            .putString(widgetKey(KEY_CACHED_DATA), Gson().toJson(data.copy(dataStatus = WindDataStatus.LIVE)))
            .putLong(widgetKey(KEY_CACHE_TIME), System.currentTimeMillis())
            .apply()
    }

    /**
     * Save Ecowitt API credentials
     */
    fun saveCredentials(applicationKey: String, apiKey: String, macAddress: String, locationName: String) {
        prefs.edit()
            .putString(widgetKey(KEY_APPLICATION_KEY), applicationKey)
            .putString(widgetKey(KEY_API_KEY), apiKey)
            .putString(widgetKey(KEY_MAC_ADDRESS), macAddress)
            .putString(widgetKey(KEY_LOCATION_NAME), locationName)
            .apply()
    }

    /**
     * Check if credentials are configured
     */
    fun hasCredentials(): Boolean {
        return !prefs.getString(widgetKey(KEY_APPLICATION_KEY), null).isNullOrEmpty() &&
                !prefs.getString(widgetKey(KEY_API_KEY), null).isNullOrEmpty() &&
                !prefs.getString(widgetKey(KEY_MAC_ADDRESS), null).isNullOrEmpty()
    }

    fun loadCredentials(): Credentials? {
        return getCredentials()
    }

    /**
     * Generate demo data for testing/preview
     */
    fun generateDemoData(): WindData {
        val calendar = Calendar.getInstance()
        val times = mutableListOf<String>()
        val speeds = mutableListOf<Float>()
        val directions = mutableListOf<Float>()
        val gusts = mutableListOf<Float>()

        // Generate 36 points (3 hours at 5-min intervals) of realistic wind data
        calendar.add(Calendar.HOUR, -3)

        for (i in 0 until 36) {
            val timeStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(calendar.time)
            times.add(timeStr)

            // Simulate realistic wind patterns (11-14 knots base, varying)
            val baseSpeed = 11.5f + (Math.sin(i * 0.3) * 1.5f).toFloat()
            speeds.add(baseSpeed + (Math.random() * 1.5f).toFloat())

            // Direction around ENE (80-90 degrees) with small variations
            directions.add(85f + (Math.random() * 10 - 5).toFloat())

            // Gusts 30-50% higher than speed
            gusts.add(baseSpeed * (1.3f + (Math.random() * 0.2f).toFloat()))

            calendar.add(Calendar.MINUTE, 5)
        }

        // Current real-time values (latest reading)
        val currentSpeed = 11.5f
        val currentDirection = 74f
        val currentGust = 15f

        return WindData(
            locationName = "São Miguel dos Milagres - Alagoas, MiCasa",
            times = times,
            speeds = speeds,
            directions = directions,
            gusts = gusts,
            currentSpeed = currentSpeed,
            currentDirection = currentDirection,
            currentGust = currentGust,
            dataStatus = WindDataStatus.DEMO,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    private fun widgetKey(base: String): String {
        return if (appWidgetId != null) {
            "widget_${appWidgetId}_$base"
        } else {
            base
        }
    }

    private fun getCredentials(): Credentials? {
        val appKey = prefs.getString(widgetKey(KEY_APPLICATION_KEY), null)
        val apiKey = prefs.getString(widgetKey(KEY_API_KEY), null)
        val mac = prefs.getString(widgetKey(KEY_MAC_ADDRESS), null)
        val locationName = prefs.getString(widgetKey(KEY_LOCATION_NAME), DEFAULT_LOCATION)

        if (appKey.isNullOrEmpty() || apiKey.isNullOrEmpty() || mac.isNullOrEmpty()) {
            return null
        }

        return Credentials(
            applicationKey = appKey,
            apiKey = apiKey,
            macAddress = mac,
            locationName = locationName ?: DEFAULT_LOCATION
        )
    }

    data class Credentials(
        val applicationKey: String,
        val apiKey: String,
        val macAddress: String,
        val locationName: String
    )

    private fun migrateLegacyPrefsIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATION_COMPLETED_V1, false)) {
            return
        }

        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val hasWidgetCreds = !prefs.getString(widgetKey(KEY_APPLICATION_KEY), null).isNullOrEmpty() ||
                !prefs.getString(widgetKey(KEY_API_KEY), null).isNullOrEmpty() ||
                !prefs.getString(widgetKey(KEY_MAC_ADDRESS), null).isNullOrEmpty()

        if (appWidgetId != null && !hasWidgetCreds) {
            val legacyAppKey = legacyPrefs.getString(KEY_APPLICATION_KEY, null)
            val legacyApiKey = legacyPrefs.getString(KEY_API_KEY, null)
            val legacyMac = legacyPrefs.getString(KEY_MAC_ADDRESS, null)
            val legacyLocation = legacyPrefs.getString(KEY_LOCATION_NAME, null)

            if (!legacyAppKey.isNullOrEmpty() && !legacyApiKey.isNullOrEmpty() && !legacyMac.isNullOrEmpty()) {
                prefs.edit()
                    .putString(widgetKey(KEY_APPLICATION_KEY), legacyAppKey)
                    .putString(widgetKey(KEY_API_KEY), legacyApiKey)
                    .putString(widgetKey(KEY_MAC_ADDRESS), legacyMac)
                    .putString(widgetKey(KEY_LOCATION_NAME), legacyLocation)
                    .apply()
            }
        }

        val widgetCacheKey = widgetKey(KEY_CACHED_DATA)
        val widgetCacheTimeKey = widgetKey(KEY_CACHE_TIME)
        if (prefs.getString(widgetCacheKey, null) == null) {
            val legacyCache = legacyPrefs.getString(KEY_CACHED_DATA, null)
            if (legacyCache != null) {
                prefs.edit()
                    .putString(widgetCacheKey, legacyCache)
                    .putLong(widgetCacheTimeKey, legacyPrefs.getLong(KEY_CACHE_TIME, 0))
                    .apply()
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED_V1, true).apply()
    }

    // Internal data classes for parsing
    internal data class HistoryResult(
        val times: List<String>,
        val speeds: List<Float>,
        val directions: List<Float>,
        val gusts: List<Float>
    )

    internal data class RealtimeResult(
        val speed: Float,
        val direction: Float,
        val gust: Float
    )
}

// ============ Ecowitt API Response Models ============

// History response
data class EcowittHistoryResponse(
    val code: Int,
    val msg: String?,
    val time: String?,
    val data: EcowittHistoryData?
)

data class EcowittHistoryData(
    val wind: EcowittHistoryWindData?
)

data class EcowittHistoryWindData(
    @SerializedName("wind_speed")
    val windSpeed: EcowittMetricList?,
    @SerializedName("wind_gust")
    val windGust: EcowittMetricList?,
    @SerializedName("wind_direction")
    val windDirection: EcowittMetricList?
)

data class EcowittMetricList(
    val unit: String?,
    val list: Map<String, String>?
)

// Real-time response
data class EcowittRealtimeResponse(
    val code: Int,
    val msg: String?,
    val time: String?,
    val data: EcowittRealtimeData?
)

data class EcowittRealtimeData(
    val wind: EcowittRealtimeWindData?
)

data class EcowittRealtimeWindData(
    @SerializedName("wind_speed")
    val windSpeed: EcowittMetricValue?,
    @SerializedName("wind_gust")
    val windGust: EcowittMetricValue?,
    @SerializedName("wind_direction")
    val windDirection: EcowittMetricValue?
)

data class EcowittMetricValue(
    val unit: String?,
    val value: String?
)
