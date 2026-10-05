package com.windwidget

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindguruFetcherTest {

    private val location = WindTideLocation.PONTA_VERDE

    private fun reading(unixSeconds: Long = System.currentTimeMillis() / 1000) =
        """{"wind_avg":13.77,"wind_max":18.2,"wind_min":null,"wind_direction":96,"temperature":26.9,""" +
            """"mslp":1019.1,"rh":76,"datetime":"2026-10-05 10:23:31 -03","unixtime":$unixSeconds}"""

    @Test
    fun `parses a reading and sends the station page as Referer`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val requests = mutableListOf<okhttp3.Request>()
        val unix = System.currentTimeMillis() / 1000
        val data = WindguruFetcher(prefs, stubHttp(requests) { 200 to reading(unix) }).fetch(location)

        assertEquals(WindDataStatus.LIVE, data.dataStatus)
        assertEquals(13.77f, data.currentSpeed, 0.001f)
        assertEquals(18.2f, data.currentGust, 0.001f)
        assertEquals(96f, data.currentDirection, 0.001f)
        assertEquals(26.9f, data.temperatureC!!, 0.001f)
        assertEquals(unix * 1000, data.lastUpdatedMillis)
        assertEquals(location.name, data.locationName)

        val request = requests.single()
        assertEquals("https://www.windguru.cz/station/15572", request.header("Referer"))
        assertEquals("station_data_current", request.url.queryParameter("q"))
        assertEquals("15572", request.url.queryParameter("id_station"))
    }

    @Test
    fun `fresh cache is used without a request`() = runBlocking {
        val prefs = FakeSharedPreferences()
        WindguruFetcher(prefs, stubHttp { 200 to reading() }).fetch(location)

        val requests = mutableListOf<okhttp3.Request>()
        val data = WindguruFetcher(prefs, stubHttp(requests) { 500 to "" }).fetch(location)

        assertEquals(WindDataStatus.CACHED, data.dataStatus)
        assertEquals(0, requests.size)
    }

    @Test
    fun `error falls back to the last real reading as stale`() = runBlocking {
        val prefs = FakeSharedPreferences()
        WindguruFetcher(prefs, stubHttp { 200 to reading() }).fetch(location)

        val data = WindguruFetcher(prefs, stubHttp { 401 to """{"return":"error","message":"Unauthorized!"}""" })
            .fetch(location, forceRefresh = true)

        assertEquals(WindDataStatus.STALE, data.dataStatus)
        assertEquals(13.77f, data.currentSpeed, 0.001f)
    }

    @Test
    fun `unknown station without any reading is demo and never cached`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val data = WindguruFetcher(prefs, stubHttp { 400 to """{"return":"error","message":"Unknown station!"}""" })
            .fetch(location)

        assertEquals(WindDataStatus.DEMO, data.dataStatus)
        assertNull(prefs.getString("wind_15572_data", null))
    }

    @Test
    fun `an old reading from the station is stale even when the request works`() = runBlocking {
        val twoHoursAgo = System.currentTimeMillis() / 1000 - 2 * 3600
        val data = WindguruFetcher(FakeSharedPreferences(), stubHttp { 200 to reading(twoHoursAgo) }).fetch(location)

        assertEquals(WindDataStatus.STALE, data.dataStatus)
    }

    @Test
    fun `cache is per station`() = runBlocking {
        val prefs = FakeSharedPreferences()
        WindguruFetcher(prefs, stubHttp { 200 to reading() }).fetch(location)

        val other = WindTideLocation("x", "Outro", 999, "outra-praia")
        val data = WindguruFetcher(prefs, stubHttp { 500 to "" }).fetch(other)

        assertEquals(WindDataStatus.DEMO, data.dataStatus)
        assertEquals("Outro", data.locationName)
    }
}
