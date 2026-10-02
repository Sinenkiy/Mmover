package com.divinegames.mmover

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SplashLifecycleTest {
    @Test fun visibleSplashWaitsTwoSecondsBeforeNavigating() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(StartActivity::class.java.name, null, true)
        val resumedAt = java.util.concurrent.atomic.AtomicLong(0)
        val lifecycleMonitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is SplashScreenActivity && stage == Stage.RESUMED) {
                resumedAt.compareAndSet(0, SystemClock.elapsedRealtime())
            }
        }
        lifecycleMonitor.addLifecycleCallback(callback)
        try {
            instrumentation.targetContext.startActivity(
                Intent(instrumentation.targetContext, SplashScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (monitor.hits == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
            assertEquals(1, monitor.hits)
            val elapsed = SystemClock.elapsedRealtime() - resumedAt.get()
            assertTrue("Splash skipped its two-second delay: $elapsed ms", elapsed >= 1950)
            assertTrue("Splash remained visible too long: $elapsed ms", elapsed < 5000)
        } finally {
            lifecycleMonitor.removeLifecycleCallback(callback)
            instrumentation.removeMonitor(monitor)
        }
    }
    @Test fun pausedSplashDoesNotNavigateAndResumesOnlyOnce() = verifyInterruption(false)

    @Test fun destroyedSplashDoesNotNavigate() = verifyInterruption(true)

    private fun verifyInterruption(destroy: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Block StartActivity: these tests must never reach production ads in MainActivity.
        val monitor = instrumentation.addMonitor(StartActivity::class.java.name, null, true)
        val interrupted = CountDownLatch(1)
        val firstResume = AtomicBoolean(true)
        val splash = AtomicReference<Activity>()
        val lifecycleMonitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is SplashScreenActivity) {
                splash.set(activity)
                if (stage == Stage.RESUMED && firstResume.compareAndSet(true, false)) {
                    // Interrupt before the window gains focus and navigation becomes eligible.
                    // ActivityScenario.launch waits for idle and can outlast a short splash.
                    if (destroy) activity.finish() else activity.moveTaskToBack(true)
                }
                if (stage == if (destroy) Stage.DESTROYED else Stage.STOPPED) {
                    interrupted.countDown()
                }
            }
        }
        val intent = Intent(instrumentation.targetContext, SplashScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        lifecycleMonitor.addLifecycleCallback(callback)
        try {
            instrumentation.targetContext.startActivity(intent)
            assertTrue("Splash did not reach the interrupted state", interrupted.await(15, TimeUnit.SECONDS))
            // Exceed the real deadline: an uncancelled callback would now attempt navigation.
            SystemClock.sleep(2300)
            instrumentation.waitForIdleSync()
            assertEquals(0, monitor.hits)
            if (!destroy) {
                instrumentation.targetContext.startActivity(intent)
                val deadline = SystemClock.elapsedRealtime() + 10000
                while (monitor.hits == 0 && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                }
                instrumentation.waitForIdleSync()
                assertEquals(1, monitor.hits)
                SystemClock.sleep(2300)
                assertEquals(1, monitor.hits)
            }
        } finally {
            lifecycleMonitor.removeLifecycleCallback(callback)
            instrumentation.runOnMainSync { splash.get()?.let { if (!it.isDestroyed) it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }
}
