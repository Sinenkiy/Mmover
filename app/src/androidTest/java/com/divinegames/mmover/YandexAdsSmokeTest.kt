package com.divinegames.mmover

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in; debug demo IDs only. Never clicks a banner. */
class YandexAdsSmokeTest {
    @Test fun optionalSdkDoesNotBlockMovement() {
        assumeTrue(BuildConfig.DEBUG && InstrumentationRegistry.getArguments().getString("optionalSdk") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val old = prefs.all["active_duration"]
        prefs.edit().putInt("active_duration", 20).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready = false
                val deadline = SystemClock.elapsedRealtime() + 15000
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onFrameActivity { ready = it.findViewById<View>(R.id.playPauseButton).isEnabled }
                    SystemClock.sleep(50)
                }
                assertTrue("Optional SDK must not block the main controls", ready)
                scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
                SystemClock.sleep(5000)
                scenario.onFrameActivity {
                    assertEquals(it.getString(R.string.motion_stop),
                        it.findViewById<android.widget.TextView>(R.id.playPauseButton).text.toString())
                    it.findViewById<View>(R.id.playPauseButton).performClick()
                    assertEquals(it.getString(R.string.motion_start),
                        it.findViewById<android.widget.TextView>(R.id.playPauseButton).text.toString())
                }
            }
        } finally {
            prefs.edit().apply { if (old is Int) putInt("active_duration", old) else remove("active_duration") }.commit()
        }
    }

    @Test fun demoAdsLoadAndBannersSurviveOnboarding() {
        assumeTrue(BuildConfig.DEBUG && InstrumentationRegistry.getArguments().getString("liveYandex") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var state: MainAdsController.Diagnostics? = null
            val deadline = SystemClock.elapsedRealtime() + 90000
            do {
                scenario.onFrameActivity { state = it.adsController.diagnostics }
                if (state?.run { topLoaded && bottomLoaded && rewardedLoaded } == true) break
                SystemClock.sleep(250)
            } while (SystemClock.elapsedRealtime() < deadline)
            assertTrue("Demo loading: $state", state?.run { topLoaded && bottomLoaded && rewardedLoaded } == true)
            lateinit var original: MainAdsController
            scenario.onFrameActivity {
                original = it.adsController
                it.findViewById<View>(R.id.backButton).performClick()
            }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.startButton))
                .perform(androidx.test.espresso.action.ViewActions.click())
            scenario.onFrameActivity {
                assertSame(original, it.adsController)
                assertTrue(it.adsController.diagnostics.topLoaded)
                assertTrue(it.adsController.diagnostics.bottomLoaded)
                assertTrue(it.adsController.diagnostics.rewardedLoaded)
            }
        }
    }
}
