package com.divinegames.mmover

import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MovementDirectionTest {
    @Test fun realFrameLoopTurnsAndStopsForStripesAndMosaic() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val style = prefs.getString("animation_style", null)
        val speed = prefs.all["movement_speed"] as? Int
        try {
            prefs.edit().putInt("movement_speed", 500).commit()
            for (key in listOf("generated_stripes", "generated_mosaic")) {
                prefs.edit().putString("animation_style", key).commit()
                ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
                    lateinit var view: MovingBackgroundView
                    val loaded = CountDownLatch(1)
                    scenario.onFrameActivity {
                        view = MovingBackgroundView(it, null)
                        it.setContentView(view)
                        view.loadBackgroundWithCallback { loaded.countDown() }
                    }
                    assertTrue(loaded.await(10, TimeUnit.SECONDS))
                    var x = 0f
                    var y = 0f
                    scenario.onFrameActivity {
                        view.startMovement()
                        x = direction(view).x
                        y = direction(view).y
                    }
                    val started = SystemClock.elapsedRealtime()
                    var changed = false
                    while (!changed && SystemClock.elapsedRealtime() - started < 15000) {
                        scenario.onFrameActivity { changed = direction(view).x != x || direction(view).y != y }
                        if (!changed) SystemClock.sleep(25)
                    }
                    assertTrue("Direction must change for $key", changed)
                    assertTrue("Must travel before turning", SystemClock.elapsedRealtime() - started >= 3800)
                    var remaining = 0f
                    scenario.onFrameActivity {
                        if (key == "generated_stripes") {
                            assertEquals(0f, direction(view).x)
                            assertEquals(-y, direction(view).y)
                        }
                        view.stopMovement()
                        remaining = direction(view).remaining
                    }
                    val idle = CountDownLatch(1)
                    scenario.onFrameActivity { view.postDelayed({ idle.countDown() }, 150) }
                    assertTrue(idle.await(5, TimeUnit.SECONDS))
                    scenario.onFrameActivity { assertEquals(remaining, direction(view).remaining) }
                }
            }
        } finally {
            prefs.edit().apply {
                if (style == null) remove("animation_style") else putString("animation_style", style)
                if (speed == null) remove("movement_speed") else putInt("movement_speed", speed)
            }.commit()
        }
    }

    private fun direction(view: MovingBackgroundView) = MovingBackgroundView::class.java
        .getDeclaredField("direction").apply { isAccessible = true }.get(view) as MovementDirection
}
