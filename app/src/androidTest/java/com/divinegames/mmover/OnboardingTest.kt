package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    private val mainScreens = mutableListOf<MainActivity>()

    @Test fun unfinishedOnboardingSurvivesRecreationAndNextLaunch() = withVersion(0) {
        ActivityScenario.launch(StartActivity::class.java).use { scenario ->
            repeat(2) {
                scenario.recreate()
                scenario.onActivity { assertNotNull(it.findViewById<View>(R.id.startButton)) }
                assertEquals(0, prefs.getInt("lastSeenVersionCode", 0))
            }
        }
        ActivityScenario.launch(StartActivity::class.java).use { scenario ->
            scenario.onActivity { assertNotNull(it.findViewById<View>(R.id.startButton)) }
        }
        instrumentation.runOnMainSync { assertTrue(mainScreens.isEmpty()) }
    }

    @Test fun forcedOnboardingSurvivesRecreationForCompletedVersion() = withVersion(BuildConfig.VERSION_CODE) {
        val intent = Intent(context, StartActivity::class.java).putExtra("forceOnboarding", true)
        ActivityScenario.launch<StartActivity>(intent).use { scenario ->
            repeat(2) {
                scenario.recreate()
                scenario.onActivity { assertNotNull(it.findViewById<View>(R.id.startButton)) }
            }
        }
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("lastSeenVersionCode", 0))
        instrumentation.runOnMainSync { assertTrue(mainScreens.isEmpty()) }
    }

    @Test fun startCompletesOnboardingAndFollowingLaunchSkipsIt() = withVersion(0) {
        ActivityScenario.launch(StartActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.startButton).performClick() }
            awaitMainCount(1)
            assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("lastSeenVersionCode", 0))
        }
        instrumentation.runOnMainSync { mainScreens.forEach { it.finish() } }
        instrumentation.waitForIdleSync()
        ActivityScenario.launch(StartActivity::class.java).use { awaitMainCount(2) }
    }

    private fun awaitMainCount(expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            var count = 0
            instrumentation.runOnMainSync { count = mainScreens.size }
            if (count == expected) return
            SystemClock.sleep(50)
        }
        fail("MainActivity count did not reach $expected")
    }

    private fun withVersion(version: Int, block: () -> Unit) {
        val previous = prefs.all["lastSeenVersionCode"] as? Int
        prefs.edit().putInt("lastSeenVersionCode", version).commit()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) {
                activity.adsController.requestPolicy.changingPrivacy = true
                mainScreens.add(activity)
            }
        }
        monitor.addLifecycleCallback(callback)
        try { block() } finally {
            instrumentation.runOnMainSync { mainScreens.forEach { it.finish() } }
            instrumentation.waitForIdleSync()
            monitor.removeLifecycleCallback(callback)
            prefs.edit().apply {
                if (previous == null) remove("lastSeenVersionCode") else putInt("lastSeenVersionCode", previous)
            }.commit()
        }
    }
}
