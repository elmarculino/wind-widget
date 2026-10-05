package com.windwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindTideLocationsTest {

    @Test
    fun `Ponta Verde is the default and always first`() {
        val locations = WindTideLocations(FakeSharedPreferences())

        assertEquals(listOf(WindTideLocation.PONTA_VERDE), locations.all())
        assertEquals(WindTideLocation.PONTA_VERDE, locations.forWidget(7))

        locations.delete(WindTideLocation.PONTA_VERDE.id)
        assertEquals(WindTideLocation.PONTA_VERDE, locations.all().first())
    }

    @Test
    fun `added places persist and a widget keeps its place after it is deleted`() {
        val prefs = FakeSharedPreferences()
        val added = WindTideLocations(prefs).add(" Milagres ", 1234, "praia-de-sao-miguel-dos-milagres")
        WindTideLocations(prefs).setForWidget(7, added)

        assertEquals(listOf("Farol da Ponta Verde · Maceió", "Milagres"), WindTideLocations(prefs).all().map { it.name })

        WindTideLocations(prefs).delete(added.id)
        assertEquals(1, WindTideLocations(prefs).all().size)
        assertEquals(added, WindTideLocations(prefs).forWidget(7))

        WindTideLocations(prefs).removeWidget(7)
        assertEquals(WindTideLocation.PONTA_VERDE, WindTideLocations(prefs).forWidget(7))
    }

    @Test
    fun `station id from an id or a link`() {
        assertEquals(15572, WindTideLocation.parseStationId("15572"))
        assertEquals(15572, WindTideLocation.parseStationId(" https://www.windguru.cz/station/15572/ "))
        assertNull(WindTideLocation.parseStationId("windguru"))
        assertNull(WindTideLocation.parseStationId(""))
    }

    @Test
    fun `tide slug from a slug or a link`() {
        assertEquals("praia-de-ponta-verde", WindTideLocation.parseTideSlug("praia-de-ponta-verde"))
        assertEquals("praia-de-ponta-verde", WindTideLocation.parseTideSlug("https://tabuasdemare.com.br/praia-de-ponta-verde?x=1"))
        assertNull(WindTideLocation.parseTideSlug("Praia de Ponta Verde!"))
        assertNull(WindTideLocation.parseTideSlug(""))
    }
}
