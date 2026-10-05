package com.windwidget

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos

/**
 * Tide from a tabuasdemare.com.br page, the same way the Echo Show dashboard (echo-show-vento) does
 * it: the page embeds the day's extremes plus the previous and next day's as JS constants, and the
 * height now is a cosine interpolation between the two extremes around the current time.
 *
 * The table is fetched once per day and kept for that day only; a table for another day is never
 * used (null means "sem dados").
 */
class TideFetcher(
    private val prefs: SharedPreferences,
    private val client: OkHttpClient = HttpClients.shared
) {

    companion object {
        val ZONE: ZoneId = ZoneId.of("America/Maceio")
        private const val USER_AGENT = "Mozilla/5.0 (compatible; WindWidget/1.0)"

        private fun dateKey(slug: String) = "tide_${slug}_date"
        private fun extremesKey(slug: String) = "tide_${slug}_extremes"
    }

    private val gson = Gson()

    suspend fun fetch(location: WindTideLocation, now: Long = System.currentTimeMillis()): TideData? {
        val today = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().toString()
        val extremes = cachedExtremes(location.tideSlug, today) ?: withContext(Dispatchers.IO) {
            try {
                val table = fetchTable(location.tideSlug)
                if (table.dateIso != today) throw IOException("tide table is for ${table.dateIso}, not $today")
                prefs.edit()
                    .putString(dateKey(location.tideSlug), table.dateIso)
                    .putString(extremesKey(location.tideSlug), gson.toJson(table.extremes))
                    .apply()
                table.extremes
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: return null
        return TideMath.tideAt(extremes, now, location.name)
    }

    /** One request for the page; throws on any error (also used to validate a new place). */
    internal fun fetchTable(slug: String): TideTable {
        val request = Request.Builder()
            .url("https://tabuasdemare.com.br/$slug")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("tabuasdemare ${response.code} for $slug")
            return TideParser.parse(response.body?.string().orEmpty())
        }
    }

    private fun cachedExtremes(slug: String, today: String): List<TideExtreme>? {
        if (prefs.getString(dateKey(slug), null) != today) return null
        val json = prefs.getString(extremesKey(slug), null) ?: return null
        return try {
            gson.fromJson<List<TideExtreme>>(json, object : TypeToken<List<TideExtreme>>() {}.type)
        } catch (e: Exception) {
            null
        }
    }
}

/** A day's extremes (with the neighbouring days'), as absolute instants. */
data class TideTable(val dateIso: String, val extremes: List<TideExtreme>)

internal object TideParser {

    /** Throws [IOException] when the page doesn't carry a tide table. */
    fun parse(html: String): TideTable {
        val dateIso = Regex("""const dateISO = '([^']*)';""").find(html)?.groupValues?.get(1)
            ?: throw IOException("tabuasdemare: no dateISO")
        val day = try {
            LocalDate.parse(dateIso)
        } catch (e: Exception) {
            throw IOException("tabuasdemare: bad dateISO $dateIso")
        }

        val extremes = listOf("prevEvents" to -1L, "events" to 0L, "nextEvents" to 1L).flatMap { (name, offset) ->
            events(html, name).map { (time, height, type) ->
                val instant = day.plusDays(offset).atTime(LocalTime.parse(time)).atZone(TideFetcher.ZONE)
                TideExtreme(type, height, instant.toInstant().toEpochMilli())
            }
        }.sortedBy { it.timeMillis }

        if (extremes.size < 2) throw IOException("tabuasdemare: not enough extremes")
        return TideTable(dateIso, extremes)
    }

    /** `const NAME = [{"time":"05:51","height_m":0.47,"type":"baixa"}, ...];` */
    private fun events(html: String, name: String): List<Triple<String, Float, TideType>> {
        val json = Regex("""const $name = (\[.*?\]);""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: return emptyList()
        return try {
            JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
                val event = element.asJsonObject
                val type = when (event.get("type")?.asString) {
                    "alta" -> TideType.HIGH
                    "baixa" -> TideType.LOW
                    else -> return@mapNotNull null
                }
                Triple(event.get("time").asString, event.get("height_m").asFloat, type)
            }
        } catch (e: Exception) {
            throw IOException("tabuasdemare: bad $name", e)
        }
    }
}

internal object TideMath {

    /** Height now between the extremes around [now]; null when [now] is outside the table. */
    fun tideAt(extremes: List<TideExtreme>, now: Long, locationName: String): TideData? {
        val sorted = extremes.sortedBy { it.timeMillis }
        val i = (0 until sorted.size - 1).firstOrNull { sorted[it].timeMillis <= now && now < sorted[it + 1].timeMillis }
            ?: return null
        val last = sorted[i]
        val next = sorted[i + 1]
        return TideData(
            locationName = locationName,
            heightNowM = cosInterp(now, last.timeMillis, next.timeMillis, last.heightM, next.heightM),
            rising = last.type == TideType.LOW,
            lastExtreme = last,
            upcoming = sorted.drop(i + 1).take(2)
        )
    }

    /** Cosine interpolation between two extremes (as in tabuasdemare's own app.js). */
    fun cosInterp(t: Long, t0: Long, t1: Long, y0: Float, y1: Float): Float {
        if (t1 == t0) return y0
        val mu = (t - t0).toDouble() / (t1 - t0)
        val mu2 = (1 - cos(mu * PI)) / 2
        return (y0 * (1 - mu2) + y1 * mu2).toFloat()
    }
}
