package com.divinegames.mmover

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackgroundPreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun previewFollowsSelectionRecreationAndReleasesOnDetach() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val saved = prefs.getString("animation_style", null)
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                var preview: PatternPreviewView? = null
                for (key in ProceduralBackground.keys) {
                    scenario.onActivity {
                        val f = it.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
                        f.findPreference<BackgroundPreviewPreference>("animation_style")!!.value = key
                        f.scrollToPreference("animation_style")
                    }
                    await {
                        scenario.onActivity {
                            it.findViewById<PatternPreviewView>(R.id.backgroundPreview)?.let { v -> preview = v }
                        }
                        preview != null && field(requireNotNull(preview), "loadedKey") == key
                    }
                    scenario.onActivity {
                        assertEquals(230400, (field(requireNotNull(preview), "bitmap") as Bitmap).byteCount)
                        assertTrue(requireNotNull(preview).width > 0 && requireNotNull(preview).height > 0)
                    }
                }
                val previous = requireNotNull(preview)
                val bitmap = field(previous, "bitmap") as Bitmap
                scenario.recreate()
                assertTrue(bitmap.isRecycled)
                assertNull(field(previous, "bitmap"))
                scenario.onActivity {
                    val f = it.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
                    f.scrollToPreference("animation_style")
                }
                await {
                    var ready = false
                    scenario.onActivity {
                        val v = it.findViewById<PatternPreviewView>(R.id.backgroundPreview)
                        ready = v != null && field(v, "loadedKey") == ProceduralBackground.keys.last()
                    }
                    ready
                }
                // Loading finishes before the render thread presents the frame.
                instrumentation.uiAutomation.waitForIdle(500, 5000)
                val shot = instrumentation.uiAutomation.takeScreenshot()
                val bounds = android.graphics.Rect()
                scenario.onActivity { it.findViewById<PatternPreviewView>(R.id.backgroundPreview).getGlobalVisibleRect(bounds) }
                var black = false
                var white = false
                for (y in bounds.top + 16 until minOf(bounds.bottom - 16, shot.height) step 8) {
                    for (x in bounds.left + 16 until minOf(bounds.right - 16, shot.width) step 8) {
                        val color = shot.getPixel(x, y)
                        black = black || color == android.graphics.Color.BLACK
                        white = white || color == android.graphics.Color.WHITE
                    }
                }
                assertTrue("Preview must be visible in the presented frame", black && white)
                val file = File(instrumentation.targetContext.getExternalFilesDir(null), "background-preview.png")
                file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                shot.recycle()
            }
        } finally {
            prefs.edit().apply { if (saved == null) remove("animation_style") else putString("animation_style", saved) }.commit()
        }
    }

    @Test fun newDefaultsDoNotOverwriteExistingChoices() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val saved = listOf("active_duration", "movement_speed").associateWith { prefs.all[it] }
        try {
            prefs.edit().remove("active_duration").remove("movement_speed").commit()
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val f = it.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
                    assertEquals(10, f.findPreference<SeekBarPreference>("active_duration")!!.value)
                    assertEquals(500, f.findPreference<SeekBarPreference>("movement_speed")!!.value)
                }
            }
            prefs.edit().putInt("active_duration", 7).putInt("movement_speed", 650).commit()
            ActivityScenario.launch(SettingsActivity::class.java).use {
                assertEquals(7, prefs.getInt("active_duration", -1))
                assertEquals(650, prefs.getInt("movement_speed", -1))
            }
        } finally {
            prefs.edit().apply {
                saved.forEach { (key, value) -> if (value == null) remove(key) else putInt(key, value as Int) }
            }.commit()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Preview did not finish loading")
    }

    private fun field(view: PatternPreviewView, name: String): Any? =
        PatternPreviewView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)
}
