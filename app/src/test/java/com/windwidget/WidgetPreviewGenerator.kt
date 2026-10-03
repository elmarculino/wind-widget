package com.windwidget

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Renders widget previews with the real renderers (Robolectric native graphics).
 * Output: app/build/widget-previews/. Copy the widget_preview_*.png files into res/drawable-nodpi/
 * when a renderer changes. review/ holds every style in a normal and a stress case (offline,
 * strong gusty wind, long name, shifting direction) for eyeballing layout problems.
 * Doubles as a smoke test that the renderers draw without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetPreviewGenerator {

    private val context = RuntimeEnvironment.getApplication()
    private val outDir = File("build/widget-previews").apply { mkdirs() }
    private val originalLocale = Locale.getDefault()

    private companion object {
        const val COMPACT_BG = 0xCC3A3D42.toInt()  // WindCompactRenderer.COLOR_BG
    }

    // Previews show what the phone shows (decimal comma)
    @Before
    fun setLocale() = Locale.setDefault(Locale("pt", "BR"))

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun `render modern preview`() {
        save(WindModernRenderer(context).render(sampleData(), 1160, 600, clipCorners = true), "widget_preview_modern.png")
    }

    @Test
    fun `render clean preview`() {
        save(WindCleanRenderer(context).render(sampleData(), 1160, 600), "widget_preview_clean.png")
    }

    @Test
    fun `render every style, normal and stress`() {
        File(outDir, "review").mkdirs()
        for ((case, data) in listOf(
            "normal" to sampleData(),
            "stress" to stressData(),
            "demo" to sampleData().copy(dataStatus = WindDataStatus.DEMO)
        )) {
            // Pixel sizes ~ the providers' defaults (dp x 3). Chart clips its own corners here, as on
            // pre-Android 12 devices (on 12+ the layout's clipToOutline does it).
            val renders = mapOf(
                "chart" to WindChartRenderer(context).render(data, 1080, 540, clipCorners = true),
                "bar" to WindBarRenderer(context).render(data, 1080, 300, clipCorners = true),
                "clean" to WindCleanRenderer(context).render(data, 1080, 540, clipCorners = true),
                "compact" to WindCompactRenderer(context).render(data, 360, 360, clipCorners = true),
                "modern" to WindModernRenderer(context).render(data, 1080, 540, clipCorners = true)
            )
            renders.forEach { (style, bitmap) ->
                save(bitmap, "review/${style}_$case.png")
                assertCornersTransparent(bitmap, "${style}_$case")
            }
            assertBottomEdgeEmpty(renders.getValue("compact"), COMPACT_BG, "compact_$case")
        }
    }

    @Test
    fun `corners stay transparent at a portrait aspect`() {
        // Portrait 4x2 cell: narrower and taller than the design size
        val data = stressData()
        assertCornersTransparent(WindChartRenderer(context).render(data, 750, 690, clipCorners = true), "chart portrait")
        assertCornersTransparent(WindCleanRenderer(context).render(data, 750, 690, clipCorners = true), "clean portrait")
        assertCornersTransparent(WindModernRenderer(context).render(data, 750, 690, clipCorners = true), "modern portrait")
        assertCornersTransparent(WindCompactRenderer(context).render(data, 300, 420, clipCorners = true), "compact portrait")
    }

    private fun assertCornersTransparent(bitmap: Bitmap, name: String) {
        val w = bitmap.width - 1
        val h = bitmap.height - 1
        for ((x, y) in listOf(0 to 0, w to 0, 0 to h, w to h)) {
            assertEquals("$name: alpha at corner ($x,$y)", 0, Color.alpha(bitmap.getPixel(x, y)))
        }
    }

    /** Text pushed past the bottom (the old Compact gust line) leaves non-background pixels in the last 3%. */
    private fun assertBottomEdgeEmpty(bitmap: Bitmap, background: Int, name: String) {
        val band = (bitmap.height * 0.97f).toInt() until bitmap.height
        val inner = (bitmap.width * 0.2f).toInt() until (bitmap.width * 0.8f).toInt()  // skip rounded corners
        for (y in band) for (x in inner) {
            val p = bitmap.getPixel(x, y)
            // ±2 per channel: translucent colours round-trip through premultiplied alpha
            val same = listOf(24, 16, 8, 0).all { shift ->
                kotlin.math.abs((p ushr shift and 0xFF) - (background ushr shift and 0xFF)) <= 2
            }
            assertTrue("$name: content at ($x,$y): ${Integer.toHexString(p)}", same)
        }
    }

    /** Offline, 30 kt and rising with strong gusts peaking at the right edge, long name, veering wind. */
    private fun stressData(): WindData {
        val speeds = (0 until 36).map { 14f + it * 0.45f + if (it % 3 == 0) 3f else 0f }
        return sampleData().copy(
            locationName = "Praia do Patacho - Porto de Pedras, Alagoas (estação 2)",
            speeds = speeds,
            directions = speeds.indices.map { (it * 10f) % 360 },
            gusts = speeds.map { it * 1.6f },
            currentSpeed = speeds.last(),
            currentDirection = 300f,  // WNW: longest 16-point label
            currentGust = speeds.last() * 1.6f,
            dataStatus = WindDataStatus.STALE
        )
    }

    /** Deterministic 3h series shaped like a real afternoon at the MiCasa station. */
    private fun sampleData(): WindData {
        val speeds = listOf(
            11.0f, 11.6f, 11.8f, 13.6f, 13.8f, 12.4f, 12.0f, 11.8f, 11.2f, 11.4f, 11.3f, 9.2f,
            8.6f, 8.9f, 8.5f, 8.8f, 8.7f, 9.4f, 9.0f, 10.2f, 9.3f, 10.0f, 10.6f, 11.5f,
            12.8f, 13.2f, 13.2f, 13.6f, 13.3f, 12.0f, 11.4f, 11.8f, 10.8f, 8.4f, 9.2f, 9.6f
        )
        val times = (0 until speeds.size).map { i ->
            val minutes = 14 * 60 + 15 + i * 5
            "2026-10-03T%02d:%02d".format(minutes / 60, minutes % 60)
        }
        return WindData(
            locationName = "São Miguel dos Milagres - Alagoas, MiCasa",
            times = times,
            speeds = speeds,
            directions = speeds.map { 74f },
            gusts = speeds.map { it * 1.3f },
            currentSpeed = 11.5f,
            currentDirection = 74f,
            currentGust = 15f,
            dataStatus = WindDataStatus.LIVE,
            lastUpdatedMillis = 1791051360000L
        )
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
