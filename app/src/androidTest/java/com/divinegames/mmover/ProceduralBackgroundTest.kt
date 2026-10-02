package com.divinegames.mmover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProceduralBackgroundTest {
    @Test fun allBackgroundsRenderMoveStopAndReleaseAcrossReloads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val saved = prefs.getString("animation_style", null)
        try {
            ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
                lateinit var view: MovingBackgroundView
                scenario.onFrameActivity {
                    view = MovingBackgroundView(it, null)
                    it.setContentView(view)
                }
                val keys = listOf("background_blocks_small", "background_stripes") +
                    ProceduralBackground.keys + listOf("background_blocks_medium", "background_blocks_large")
                for (key in keys) {
                    val loaded = CountDownLatch(1)
                    scenario.onFrameActivity {
                        prefs.edit().putString("animation_style", key).commit()
                        view.loadBackgroundWithCallback { loaded.countDown() }
                    }
                    assertTrue("load $key", loaded.await(10, TimeUnit.SECONDS))
                    scenario.onFrameActivity {
                        val bitmap = field(view, "bitmap") as Bitmap
                        assertFalse(bitmap.isRecycled)
                        if (key.startsWith("generated_")) assertEquals(240 * 240 * 4, bitmap.byteCount)
                        val frame = Bitmap.createBitmap(301, 503, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(frame))
                            assertEquals("coverage $key", 255, Color.alpha(frame.getPixel(300, 502)))
                            val directory = java.io.File(it.getExternalFilesDir(null), "pattern-previews").apply { mkdirs() }
                            java.io.File(directory, "$key.png").outputStream().use { output ->
                                frame.compress(Bitmap.CompressFormat.PNG, 100, output)
                            }
                            view.setPowerSavingMode(true)
                            view.draw(Canvas(frame))
                            assertEquals(Color.BLACK, frame.getPixel(150, 250))
                            view.setPowerSavingMode(false)
                        } finally { frame.recycle() }
                    }
                }
                val loaded = CountDownLatch(1)
                scenario.onFrameActivity {
                    prefs.edit().putString("animation_style", "generated_mosaic").commit()
                    view.loadBackgroundWithCallback { loaded.countDown() }
                }
                assertTrue(loaded.await(10, TimeUnit.SECONDS))
                val moved = CountDownLatch(1)
                scenario.onFrameActivity {
                    view.startMovement()
                    view.postDelayed({
                        view.stopMovement()
                        moved.countDown()
                    }, 250)
                }
                assertTrue(moved.await(5, TimeUnit.SECONDS))
                var x = 0f
                var y = 0f
                scenario.onFrameActivity {
                    x = field(view, "bitmapX") as Float
                    y = field(view, "bitmapY") as Float
                    assertTrue(x != 0f || y != 0f)
                }
                val stopped = CountDownLatch(1)
                scenario.onFrameActivity { view.postDelayed({ stopped.countDown() }, 100) }
                assertTrue(stopped.await(5, TimeUnit.SECONDS))
                scenario.onFrameActivity {
                    assertEquals(x, field(view, "bitmapX"))
                    assertEquals(y, field(view, "bitmapY"))
                    val old = field(view, "bitmap") as Bitmap
                    it.setContentView(android.widget.FrameLayout(it))
                    assertTrue(old.isRecycled)
                    assertNull(field(view, "tileShader"))
                }
            }
        } finally {
            prefs.edit().apply {
                if (saved == null) remove("animation_style") else putString("animation_style", saved)
            }.commit()
        }
    }

    private fun field(view: MovingBackgroundView, name: String): Any? =
        MovingBackgroundView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)
}
