package com.windwidget

import org.junit.Assert.assertEquals
import org.junit.Test

class WindDataTest {

    private fun cardinal(degrees: Float) =
        WindData("x", emptyList(), emptyList(), emptyList(), emptyList(), currentDirection = degrees).directionCardinal

    @Test
    fun `16-point compass sectors`() {
        assertEquals("N", cardinal(0f))
        assertEquals("N", cardinal(11.2f))
        assertEquals("NNE", cardinal(11.25f))
        assertEquals("ENE", cardinal(74f))
        assertEquals("E", cardinal(90f))
        assertEquals("WNW", cardinal(300f))
        assertEquals("NNW", cardinal(337f))
        assertEquals("N", cardinal(349f))
        assertEquals("N", cardinal(359.9f))
    }

    @Test
    fun `out-of-range degrees wrap around`() {
        assertEquals("N", cardinal(360f))
        assertEquals("E", cardinal(450f))
        assertEquals("W", cardinal(-90f))
    }
}
