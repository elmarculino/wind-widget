package com.windwidget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

class TideFetcherTest {

    private val page = javaClass.classLoader!!.getResource("tabuasdemare_ponta_verde.html")!!.readText()
    private val location = WindTideLocation.PONTA_VERDE

    /** 2026-10-05 (the fixture's day) at [hour]:[minute] in Maceió. */
    private fun at(hour: Int, minute: Int, day: Int = 5): Long =
        LocalDate.of(2026, 10, day).atTime(LocalTime.of(hour, minute)).atZone(TideFetcher.ZONE).toInstant().toEpochMilli()

    @Test
    fun `parses the three days of extremes in order`() {
        val table = TideParser.parse(page)

        assertEquals("2026-10-05", table.dateIso)
        assertEquals(at(23, 6, day = 4), table.extremes[3].timeMillis)   // last of prevEvents
        assertEquals(at(5, 51), table.extremes[4].timeMillis)            // first of events
        assertEquals(TideType.LOW, table.extremes[4].type)
        assertEquals(0.47f, table.extremes[4].heightM, 0.001f)
        assertEquals(table.extremes.sortedBy { it.timeMillis }, table.extremes)
    }

    @Test(expected = IOException::class)
    fun `a page without a tide table is an error`() {
        TideParser.parse("<html><body>Página não encontrada</body></html>")
    }

    @Test
    fun `height matches the Echo dashboard`() {
        // Expected values from echo-show-vento server.py (_compute_from_extremes) on the same page
        val extremes = TideParser.parse(page).extremes
        fun tide(h: Int, m: Int) = TideMath.tideAt(extremes, at(h, m), "x")!!

        assertEquals(1.05f, tide(9, 0).heightNowM, 0.005f)
        assertTrue(tide(9, 0).rising)
        assertEquals(1.16f, tide(2, 0).heightNowM, 0.005f)
        assertFalse(tide(2, 0).rising)
        assertEquals(1.72f, tide(23, 30).heightNowM, 0.005f)
        assertEquals(0.47f, tide(5, 51).heightNowM, 0.005f)
    }

    @Test
    fun `last extreme and the next two`() {
        val tide = TideMath.tideAt(TideParser.parse(page).extremes, at(9, 0), "x")!!

        assertEquals(at(5, 51), tide.lastExtreme!!.timeMillis)
        assertEquals(listOf(at(12, 4), at(18, 14)), tide.upcoming.map { it.timeMillis })
        assertEquals(listOf(TideType.HIGH, TideType.LOW), tide.upcoming.map { it.type })
    }

    @Test
    fun `fetches once a day and then uses the cached table`() = runBlocking {
        val prefs = FakeSharedPreferences()
        val requests = mutableListOf<okhttp3.Request>()
        val result = TideFetcher(prefs, stubHttp(requests) { 200 to page }).fetch(location, at(9, 0))

        assertEquals(1.05f, result!!.heightNowM, 0.005f)
        assertEquals("/praia-de-ponta-verde", requests.single().url.encodedPath)

        // Later the same day the site is down: the day's table still answers
        val later = TideFetcher(prefs, stubHttp { 503 to "" }).fetch(location, at(15, 0))
        assertFalse(later!!.rising)
    }

    @Test
    fun `never uses another day's table`() = runBlocking {
        val prefs = FakeSharedPreferences()
        TideFetcher(prefs, stubHttp { 200 to page }).fetch(location, at(9, 0))

        // Next day, site down: yesterday's cache must not be used
        assertNull(TideFetcher(prefs, stubHttp { 503 to "" }).fetch(location, at(9, 0, day = 6)))
        // Next day, site still serving yesterday's page: not used either
        assertNull(TideFetcher(prefs, stubHttp { 200 to page }).fetch(location, at(9, 0, day = 6)))
    }
}
