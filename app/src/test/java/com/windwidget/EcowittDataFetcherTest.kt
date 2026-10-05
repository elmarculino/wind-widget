package com.windwidget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class EcowittDataFetcherTest {

    private companion object {
        // Answers both endpoints: history reads the "list" maps, real_time reads "value"
        const val HISTORY_OK = """{"code":0,"msg":"success","data":{"wind":{
            "wind_speed":{"unit":"knot","value":"11.0","list":{"1690000000":"10.5"}},
            "wind_gust":{"unit":"knot","value":"15.0","list":{"1690000000":"15.0"}},
            "wind_direction":{"unit":"deg","value":"80","list":{"1690000000":"80"}}}}}"""
    }

    private lateinit var context: Context
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var legacyPrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = FakeSharedPreferences()
        legacyPrefs = FakeSharedPreferences()

        mockkObject(SecurePrefs)
        every { SecurePrefs.get(any(), any()) } returns prefs
        every { context.getSharedPreferences(any(), any()) } returns legacyPrefs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `parses history response`() {
        val fetcher = EcowittDataFetcher(context)
        val json = """
            {
              "code": 0,
              "msg": "success",
              "time": "2024-01-01 00:00:00",
              "data": {
                "wind": {
                  "wind_speed": {"unit": "knot", "list": {"1690000000": "10.5", "1690000300": "12.0"}},
                  "wind_gust": {"unit": "knot", "list": {"1690000000": "15.0", "1690000300": "16.0"}},
                  "wind_direction": {"unit": "deg", "list": {"1690000000": "80", "1690000300": "90"}}
                }
              }
            }
        """.trimIndent()

        val result = fetcher.parseHistoryResponse(json)

        assertNotNull(result)
        result!!
        assertEquals(2, result.times.size)
        assertEquals(listOf(10.5f, 12.0f), result.speeds)
        assertEquals(listOf(80f, 90f), result.directions)
        assertEquals(listOf(15.0f, 16.0f), result.gusts)
    }

    @Test
    fun `parses realtime response`() {
        val fetcher = EcowittDataFetcher(context)
        val json = """
            {
              "code": 0,
              "msg": "success",
              "time": "2024-01-01 00:00:00",
              "data": {
                "wind": {
                  "wind_speed": {"unit": "knot", "value": "11.2"},
                  "wind_gust": {"unit": "knot", "value": "15.7"},
                  "wind_direction": {"unit": "deg", "value": "72"}
                }
              }
            }
        """.trimIndent()

        val result = fetcher.parseRealtimeResponse(json)

        assertNotNull(result)
        result!!
        assertEquals(11.2f, result.speed)
        assertEquals(15.7f, result.gust)
        assertEquals(72f, result.direction)
    }

    @Test
    fun `cache logic returns fresh cache and falls back when stale`() = runBlocking {
        val fetcher = EcowittDataFetcher(context)
        val now = System.currentTimeMillis()
        val cached = WindData(
            locationName = "Test",
            times = listOf("2024-01-01T00:00"),
            speeds = listOf(10f),
            directions = listOf(80f),
            gusts = listOf(15f),
            currentSpeed = 10f,
            currentDirection = 80f,
            currentGust = 15f,
            dataStatus = WindDataStatus.LIVE,
            lastUpdatedMillis = now
        )

        prefs.edit()
            .putString("cached_wind_data", Gson().toJson(cached))
            .putLong("cache_time", now)
            .apply()

        val fresh = fetcher.fetch()
        assertNotNull(fresh)
        assertEquals(WindDataStatus.CACHED, fresh!!.dataStatus)

        prefs.edit().putLong("cache_time", now - 10 * 60 * 1000L).apply()
        val stale = fetcher.fetch()
        assertNotNull(stale)
        assertEquals(WindDataStatus.DEMO, stale!!.dataStatus)
    }

    @Test
    fun `demo data generation`() {
        val fetcher = EcowittDataFetcher(context)
        val demo = fetcher.generateDemoData()
        assertEquals(WindDataStatus.DEMO, demo.dataStatus)
        assertEquals(36, demo.times.size)
        assertTrue(demo.speeds.all { it > 0f })
    }

    @Test
    fun `credential storage and retrieval`() {
        val fetcher = EcowittDataFetcher(context)
        fetcher.saveCredentials("appKey", "apiKey", "mac123", "My Place")

        val creds = fetcher.loadCredentials()
        assertNotNull(creds)
        creds!!
        assertEquals("appKey", creds.applicationKey)
        assertEquals("apiKey", creds.apiKey)
        assertEquals("mac123", creds.macAddress)
        assertEquals("My Place", creds.locationName)
    }

    @Test
    fun `api failure keeps last real reading as stale and never caches demo data`() = runBlocking {
        val fetcher = EcowittDataFetcher(context, client = stubClient(code = 500, body = ""))
        fetcher.saveCredentials("appKey", "apiKey", "mac123", "My Place")
        val real = realReading()
        prefs.edit()
            .putString("cached_wind_data", Gson().toJson(real))
            .putLong("cache_time", System.currentTimeMillis() - 10 * 60 * 1000L)
            .apply()

        val result = fetcher.fetch()

        assertEquals(WindDataStatus.STALE, result!!.dataStatus)
        assertEquals(real.speeds, result.speeds)
        assertEquals(Gson().toJson(real), prefs.getString("cached_wind_data", null))
    }

    @Test
    fun `api error code without cache returns uncached demo data`() = runBlocking {
        val fetcher = EcowittDataFetcher(
            context,
            client = stubClient(code = 200, body = """{"code":40010,"msg":"Illegal Application_Key Parameter","data":[]}""")
        )
        fetcher.saveCredentials("appKey", "apiKey", "mac123", "My Place")

        val result = fetcher.fetch(forceRefresh = true)

        assertEquals(WindDataStatus.DEMO, result!!.dataStatus)
        assertNull(prefs.getString("cached_wind_data", null))
    }

    @Test
    fun `credentials are sent as query parameters, not headers`() = runBlocking {
        // Ecowitt rejects X-Application-Key / X-API-Key headers with 40010 (seen on the phone, 2026-10-03)
        val requests = mutableListOf<okhttp3.Request>()
        val fetcher = EcowittDataFetcher(context, client = stubClient(code = 200, body = HISTORY_OK, seen = requests))
        fetcher.saveCredentials("app key&1", "apiKey", "AA:BB:CC", "My Place")

        val result = fetcher.fetch(forceRefresh = true)

        assertEquals(WindDataStatus.LIVE, result!!.dataStatus)
        assertEquals(setOf("history", "real_time"), requests.map { it.url.pathSegments.last() }.toSet())
        for (request in requests) {
            assertEquals("app key&1", request.url.queryParameter("application_key"))
            assertEquals("apiKey", request.url.queryParameter("api_key"))
            assertEquals("AA:BB:CC", request.url.queryParameter("mac"))
            assertNull(request.header("X-Application-Key"))
            assertNull(request.header("X-API-Key"))
        }
    }

    @Test
    fun `error responses with an empty data array parse to null`() {
        val fetcher = EcowittDataFetcher(context)
        val error = """{"code":40010,"msg":"Invalid application Key","time":"1791051360","data":[]}"""

        assertNull(fetcher.parseHistoryResponse(error))
        assertNull(fetcher.parseRealtimeResponse(error))
    }

    @Test
    fun `missing credentials returns demo data without caching it`() = runBlocking {
        val fetcher = EcowittDataFetcher(context)

        val result = fetcher.fetch()

        assertEquals(WindDataStatus.DEMO, result!!.dataStatus)
        assertFalse(prefs.contains("cached_wind_data"))
    }

    @Test
    fun `saving credentials invalidates cached reading`() {
        val fetcher = EcowittDataFetcher(context)
        prefs.edit()
            .putString("cached_wind_data", Gson().toJson(realReading()))
            .putLong("cache_time", System.currentTimeMillis())
            .apply()

        fetcher.saveCredentials("appKey", "apiKey", "otherMac", "Other Place")

        assertFalse(prefs.contains("cached_wind_data"))
        assertFalse(prefs.contains("cache_time"))
    }

    @Test
    fun `migration gives legacy credentials to every Ecowitt widget and deletes the plain text`() {
        legacyPrefs.edit()
            .putString("ecowitt_app_key", "appKey")
            .putString("ecowitt_api_key", "apiKey")
            .putString("ecowitt_mac", "mac123")
            .putString("location_name", "Old Place")
            .putString("cached_wind_data", Gson().toJson(realReading()))
            .putLong("cache_time", 1234L)
            .putBoolean("migration_completed_v1", true)
            .apply()
        // Widget 8 already has its own settings; they must survive
        prefs.edit().putString("widget_8_ecowitt_app_key", "ownKey").apply()

        EcowittDataFetcher(context, appWidgetId = 7, ecowittWidgetIds = { intArrayOf(7, 8, 9) })

        for (id in listOf(7, 9)) {
            assertEquals("appKey", prefs.getString("widget_${id}_ecowitt_app_key", null))
            assertEquals("apiKey", prefs.getString("widget_${id}_ecowitt_api_key", null))
            assertEquals("mac123", prefs.getString("widget_${id}_ecowitt_mac", null))
            assertEquals("Old Place", prefs.getString("widget_${id}_location_name", null))
            assertEquals(1234L, prefs.getLong("widget_${id}_cache_time", 0))
        }
        assertEquals("ownKey", prefs.getString("widget_8_ecowitt_app_key", null))
        assertNull(prefs.getString("widget_8_ecowitt_api_key", null))
        assertTrue(legacyPrefs.all.isEmpty())
    }

    @Test
    fun `migration moves plain-text widget keys left by the keystore fallback`() {
        legacyPrefs.edit()
            .putString("widget_5_ecowitt_app_key", "appKey")
            .putString("widget_5_ecowitt_api_key", "apiKey")
            .putString("widget_5_ecowitt_mac", "mac123")
            .putLong("widget_5_cache_time", 99L)
            .apply()

        val fetcher = EcowittDataFetcher(context, appWidgetId = 5, ecowittWidgetIds = { error("not needed") })

        assertEquals("mac123", fetcher.loadCredentials()!!.macAddress)
        assertEquals(99L, prefs.getLong("widget_5_cache_time", 0))
        assertTrue(legacyPrefs.all.isEmpty())
    }

    @Test
    fun `migration keeps legacy credentials when the widget list is unavailable`() {
        legacyPrefs.edit()
            .putString("ecowitt_app_key", "appKey")
            .putString("ecowitt_api_key", "apiKey")
            .putString("ecowitt_mac", "mac123")
            .apply()

        EcowittDataFetcher(context, appWidgetId = 7, ecowittWidgetIds = { throw IllegalStateException() })

        assertEquals("appKey", legacyPrefs.getString("ecowitt_app_key", null))
        assertNull(prefs.getString("widget_7_ecowitt_app_key", null))
    }

    @Test
    fun `migration leaves unrelated plain keys alone`() {
        legacyPrefs.edit().putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "keyset").apply()

        EcowittDataFetcher(context, appWidgetId = 7, ecowittWidgetIds = { error("not needed") })

        assertEquals("keyset", legacyPrefs.getString("__androidx_security_crypto_encrypted_prefs_key_keyset__", null))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `error responses are closed`() = runBlocking {
        val closed = mutableListOf<Boolean>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = object : okhttp3.ResponseBody() {
                    private val source = okio.Buffer().writeUtf8("oops")
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = source.size
                    override fun source(): okio.BufferedSource = source
                    override fun close() {
                        synchronized(closed) { closed.add(true) }
                        super.close()
                    }
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("stub")
                    .body(body)
                    .build()
            }
            .build()
        val fetcher = EcowittDataFetcher(context, client = client)
        fetcher.saveCredentials("appKey", "apiKey", "mac123", "My Place")

        fetcher.fetch()

        // History and real-time both answered 500 without reading the body
        assertEquals(2, closed.size)
    }

    private fun realReading() = WindData(
        locationName = "Real",
        times = listOf("2024-01-01T00:00", "2024-01-01T00:05"),
        speeds = listOf(18f, 19f),
        directions = listOf(90f, 95f),
        gusts = listOf(22f, 24f),
        dataStatus = WindDataStatus.LIVE,
        lastUpdatedMillis = System.currentTimeMillis() - 10 * 60 * 1000L
    )

    private fun stubClient(
        code: Int,
        body: String,
        seen: MutableList<okhttp3.Request>? = null
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            synchronized(this) { seen?.add(chain.request()) }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body(body.toResponseBody())
                .build()
        }
        .build()
}
