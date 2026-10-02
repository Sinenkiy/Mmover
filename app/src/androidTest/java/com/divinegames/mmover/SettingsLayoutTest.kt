package com.divinegames.mmover

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun sliderAndListWidthsStayStableAcrossValueChangesAndRecreation() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val keys = listOf("active_duration", "pause_duration", "brightness", "movement_speed")
        val saved = keys.associateWith { prefs.all[it] }
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                awaitLayout(scenario)
                var initialWidth = 0
                scenario.onActivity {
                    val list = fragment(it).listView
                    initialWidth = list.width
                    assertTrue(initialWidth <= it.resources.getDimensionPixelSize(R.dimen.prefs_max_width))
                    assertEquals(list.width, (list.parent as View).width)
                }
                for (key in keys) {
                    scenario.onActivity { fragment(it).scrollToPreference(key) }
                    instrumentation.waitForIdleSync()
                    var sliderWidth = 0
                    var rowWidth = 0
                    var rowPosition = -1
                    scenario.onActivity {
                        val f = fragment(it)
                        val p = f.findPreference<SeekBarPreference>(key)!!
                        rowPosition = (f.listView.adapter as androidx.preference.PreferenceGroupAdapter)
                            .getPreferenceAdapterPosition(p)
                    }
                    await(scenario) { fragment(it).listView.findViewHolderForAdapterPosition(rowPosition) != null }
                    scenario.onActivity {
                        val row = fragment(it).listView.findViewHolderForAdapterPosition(rowPosition)!!.itemView
                        rowWidth = row.width
                        sliderWidth = row.findViewById<SeekBar>(androidx.preference.R.id.seekbar).width
                        assertTrue(sliderWidth > 0)
                    }
                    for (atMaximum in listOf(false, true, false, true)) {
                        scenario.onActivity {
                            val p = fragment(it).findPreference<SeekBarPreference>(key)!!
                            val value = if (atMaximum) p.max else p.min
                            assertTrue(p.callChangeListener(value))
                            p.value = value
                        }
                        instrumentation.waitForIdleSync()
                        scenario.onActivity {
                            val f = fragment(it)
                            assertEquals("List width changed for $key", initialWidth, f.listView.width)
                            val row = f.listView.findViewHolderForAdapterPosition(rowPosition)!!.itemView
                            assertEquals("Row width changed for $key", rowWidth, row.width)
                            assertEquals("Slider width changed for $key", sliderWidth,
                                row.findViewById<SeekBar>(androidx.preference.R.id.seekbar).width)
                        }
                    }
                    if (key == "pause_duration") {
                        var left = 0f
                        var right = 0f
                        var y = 0f
                        scenario.onActivity {
                            val row = fragment(it).listView.findViewHolderForAdapterPosition(rowPosition)!!.itemView
                            val slider = row.findViewById<SeekBar>(androidx.preference.R.id.seekbar)
                            val position = IntArray(2)
                            slider.getLocationOnScreen(position)
                            left = (position[0] + slider.paddingLeft).toFloat()
                            right = (position[0] + slider.width - slider.paddingRight).toFloat()
                            y = position[1] + slider.height / 2f
                        }
                        val down = SystemClock.uptimeMillis()
                        fun touch(action: Int, x: Float) {
                            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                            instrumentation.sendPointerSync(event)
                            event.recycle()
                        }
                        touch(MotionEvent.ACTION_DOWN, right)
                        for (step in 1..10) {
                            touch(MotionEvent.ACTION_MOVE, right - (right - left) * step / 10)
                            instrumentation.waitForIdleSync()
                            scenario.onActivity {
                                val row = fragment(it).listView.findViewHolderForAdapterPosition(rowPosition)!!.itemView
                                assertEquals(sliderWidth, row.findViewById<SeekBar>(androidx.preference.R.id.seekbar).width)
                            }
                        }
                        touch(MotionEvent.ACTION_UP, left)
                        instrumentation.waitForIdleSync()
                        scenario.onActivity {
                            val p = fragment(it).findPreference<SeekBarPreference>(key)!!
                            assertEquals("Dragging must reach the minimum", p.min, p.value)
                            assertFalse(p.showSeekBarValue)
                        }
                    }
                }
                scenario.recreate()
                awaitLayout(scenario)
                scenario.onActivity { assertEquals(initialWidth, fragment(it).listView.width) }
                scenario.onActivity { fragment(it).scrollToPreference("active_duration") }
                instrumentation.waitForIdleSync()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "settings-ui").apply { mkdirs() }
                    File(dir, "settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally {
            val editor = prefs.edit()
            saved.forEach { (key, value) -> if (value is Int) editor.putInt(key, value) else editor.remove(key) }
            editor.commit()
        }
    }

    private fun fragment(activity: SettingsActivity) =
        activity.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment

    private fun awaitLayout(scenario: ActivityScenario<SettingsActivity>) = await(scenario) {
        val f = it.supportFragmentManager.findFragmentById(android.R.id.content) as? SettingsFragment
        f?.view != null && f.listView.width > 0 && f.listView.childCount > 0 && !f.listView.isLayoutRequested
    }

    private fun await(scenario: ActivityScenario<SettingsActivity>, check: (SettingsActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Settings layout did not become ready")
    }
}
