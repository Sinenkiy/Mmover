package com.divinegames.mmover

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/** Opt-in demo playback. Unlabelled SDK close controls may require manual closing within 60s. */
class YandexRewardPlaybackTest {
    @Test fun demoPlaybackGrantsAccessAndReturnsToMain() {
        assumeTrue(BuildConfig.DEBUG && InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val savedDate = prefs.getString("last_stealth_activation_date", null)
        val savedEnabled = prefs.all["stealth_mode_enabled"]
        prefs.edit().remove("last_stealth_activation_date").putBoolean("stealth_mode_enabled", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready = false
                val loadDeadline = SystemClock.elapsedRealtime() + 90000
                while (!ready && SystemClock.elapsedRealtime() < loadDeadline) {
                    scenario.onFrameActivity { ready = it.adsController.rewardedState == RewardedAdState.READY }
                    if (!ready) SystemClock.sleep(100)
                }
                assertTrue("Demo rewarded must load", ready)
                scenario.onFrameActivity { it.adsController.showRewarded() }
                val rewardDeadline = SystemClock.elapsedRealtime() + 90000
                while (!prefs.getBoolean("stealth_mode_enabled", false) && SystemClock.elapsedRealtime() < rewardDeadline) {
                    SystemClock.sleep(250)
                }
                assertTrue("Completed demo playback must grant access", prefs.getBoolean("stealth_mode_enabled", false))
                assertEquals(LocalDate.now().toString(), prefs.getString("last_stealth_activation_date", null))
                // SDK close control is used below; Back may be deliberately ignored by an ad.
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "rewarded-complete.png").outputStream().use {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
                val returnDeadline = SystemClock.elapsedRealtime() + 60000
                while (scenario.state != Lifecycle.State.RESUMED && SystemClock.elapsedRealtime() < returnDeadline) {
                    clickClose(instrumentation.uiAutomation.rootInActiveWindow)
                    SystemClock.sleep(300)
                }
                if (scenario.state != Lifecycle.State.RESUMED) {
                    val output = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "rewarded-close-tree.txt")
                    output.writeText(describe(instrumentation.uiAutomation.rootInActiveWindow))
                }
                assertEquals("Return after the demo", Lifecycle.State.RESUMED, scenario.state)
                scenario.onFrameActivity {
                    assertTrue(it.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.modeSwitch).isChecked)
                }
            }
        } finally {
            prefs.edit().apply {
                if (savedDate == null) remove("last_stealth_activation_date") else putString("last_stealth_activation_date", savedDate)
                if (savedEnabled is Boolean) putBoolean("stealth_mode_enabled", savedEnabled) else remove("stealth_mode_enabled")
            }.commit()
        }
    }
    private fun clickClose(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val label = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}".trim()
        val closeLabel = Regex("(?i)^(close|close ad|закрыть|закрыть рекламу|schließen|fermer)$").matches(label)
        val closeId = node.viewIdResourceName?.substringAfter(":id/")?.let {
            it == "close" || it == "close_button" || it == "closeButton" || it == "ad_close"
        } == true
        if ((closeLabel || closeId) && node.isVisibleToUser && node.isClickable &&
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        for (index in 0 until node.childCount) if (clickClose(node.getChild(index))) return true
        return false
    }

    private fun describe(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null) return "null"
        return "${" ".repeat(depth)}${node}\n" + (0 until node.childCount).joinToString("") {
            describe(node.getChild(it), depth + 1)
        }
    }
}

