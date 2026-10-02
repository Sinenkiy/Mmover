package com.divinegames.mmover

import android.app.ActivityManager
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in diagnostic, not a benchmark of production ad SDK memory. */
class BackgroundMemoryProfileTest {
    @Test fun profileMovingBackgroundsAndRepeatedSwitches() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoryProfile") == "true")
        val withAds = InstrumentationRegistry.getArguments().getString("memoryAds") == "true"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = listOf("animation_style", "active_duration", "pause_duration", "movement_speed", "vibration_enabled")
            .associateWith { prefs.all[it] }
        val directory = File(context.getExternalFilesDir(null), if (withAds) "background-memory-sdk" else "background-memory").apply { mkdirs() }
        val csv = File(directory, "samples.csv")
        csv.writeText("phase,pattern,pss_kib,private_kib,java_used_kib,native_alloc_kib,graphics_kib\n")
        val manager = context.getSystemService(ActivityManager::class.java)
        val ram = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        File(directory, "device.txt").writeText("SDK=${android.os.Build.VERSION.SDK_INT}\n" +
            "RAM_bytes=${ram.totalMem}\nmemoryClass_MiB=${manager.memoryClass}\n" +
            "largeMemoryClass_MiB=${manager.largeMemoryClass}\nruntimeMax_bytes=${Runtime.getRuntime().maxMemory()}\n" +
            "screen=${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}\n" +
            "Debug build with instrumentation; demo SDK enabled=$withAds; active=10s,pause=0,speed=500,vibration=false\n")
        fun sample(phase: String, key: String) {
            val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            val runtime = Runtime.getRuntime()
            csv.appendText("$phase,$key,${memory.totalPss},${memory.totalPrivateDirty + memory.totalPrivateClean}," +
                "${(runtime.totalMemory() - runtime.freeMemory()) / 1024},${Debug.getNativeHeapAllocatedSize() / 1024}," +
                "${memory.getMemoryStat("summary.graphics")}\n")
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (!withAds && activity is MainActivity && stage == Stage.CREATED) {
                activity.adsController.requestPolicy.changingPrivacy = true
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            prefs.edit().putString("animation_style", ProceduralBackground.keys.first())
                .putInt("active_duration", 10).putInt("pause_duration", 0)
                .putInt("movement_speed", 500).putBoolean("vibration_enabled", false).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 15000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onFrameActivity { ready = it.findViewById<View>(R.id.playPauseButton).isEnabled }
                    if (!ready) SystemClock.sleep(50)
                }
                assertTrue("Main must finish its initial load", ready)
                fun load(key: String) {
                    val loaded = CountDownLatch(1)
                    scenario.onFrameActivity {
                        prefs.edit().putString("animation_style", key).commit()
                        it.findViewById<MovingBackgroundView>(R.id.movingBackground).loadBackgroundWithCallback { loaded.countDown() }
                    }
                    assertTrue("load $key", loaded.await(15, TimeUnit.SECONDS))
                    scenario.onFrameActivity {
                        val view = it.findViewById<MovingBackgroundView>(R.id.movingBackground)
                        val bitmap = MovingBackgroundView::class.java.getDeclaredField("bitmap")
                            .apply { isAccessible = true }.get(view) as Bitmap
                        assertEquals(230400, bitmap.allocationByteCount)
                    }
                }
                ProceduralBackground.keys.forEach { load(it); SystemClock.sleep(500) }
                scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
                scenario.onFrameActivity {
                    val view = it.findViewById<MovingBackgroundView>(R.id.movingBackground)
                    assertEquals(true, MovingBackgroundView::class.java.getDeclaredField("isMoving")
                        .apply { isAccessible = true }.get(view))
                }
                repeat(2) { round ->
                    for (key in ProceduralBackground.keys) {
                        load(key)
                        SystemClock.sleep(2000)
                        repeat(3) {
                            sample("moving_$round", key)
                            SystemClock.sleep(2000)
                        }
                    }
                }
                // Before/after comparisons use the same explicit GC and settling procedure.
                Runtime.getRuntime().gc()
                SystemClock.sleep(2000)
                sample("before_switches_gc", "all")
                repeat(100) { load(ProceduralBackground.keys[it % ProceduralBackground.keys.size]) }
                sample("after_switches_raw", "all")
                Runtime.getRuntime().gc()
                SystemClock.sleep(2000)
                sample("after_switches_gc", "all")
                scenario.onFrameActivity {
                    File(directory, "device.txt").appendText("SDK diagnostics=" + it.adsController.diagnostics + "\n")
                }
                val fd = instrumentation.uiAutomation.executeShellCommand("dumpsys meminfo ${context.packageName}")
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                    File(directory, "meminfo.txt").writeBytes(input.readBytes())
                }
            }
        } finally {
            monitor.removeLifecycleCallback(callback)
            prefs.edit().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        null -> remove(key)
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }.commit()
        }
    }
}
