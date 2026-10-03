package com.windwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.After
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
 * Output: app/build/widget-previews/. Copy into res/drawable-nodpi/ when a renderer changes.
 * Doubles as a smoke test that the renderers draw without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetPreviewGenerator {

    private val context = RuntimeEnvironment.getApplication()
    private val outDir = File("build/widget-previews").apply { mkdirs() }
    private val originalLocale = Locale.getDefault()

    // Previews show what the phone shows (decimal comma)
    @Before
    fun setLocale() = Locale.setDefault(Locale("pt", "BR"))

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun `render modern preview`() {
        // Modern: scale = height / 160, corner radius 28 * scale
        val height = 600
        val bitmap = WindModernRenderer(context).render(sampleData(), 1160, height)
        save(roundCorners(bitmap, 28f * height / 160f), "widget_preview_modern.png")
    }

    @Test
    fun `render clean preview`() {
        // Clean: scale = height / 140, corner radius 20 * scale
        val height = 600
        val bitmap = WindCleanRenderer(context).render(sampleData(), 1160, height)
        save(roundCorners(bitmap, 20f * height / 140f), "widget_preview_clean.png")
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

    /** Renderers draw on RGB_565 (no alpha): make the area outside the rounded card transparent. */
    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
