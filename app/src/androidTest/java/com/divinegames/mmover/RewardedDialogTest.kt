package com.divinegames.mmover

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test

class RewardedDialogTest {
    @Test fun loadingFailureAndReadyUpdateAnOpenDialog() {
        ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
            lateinit var dialog: StealthRewardDialog
            var watches = 0
            var retries = 0
            scenario.onActivity {
                dialog = StealthRewardDialog(it, onRetry = {
                    retries++
                    dialog.render(RewardedAdState.LOADING)
                }) { watches++ }
                dialog.show(RewardedAdState.LOADING)
            }
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(not(isEnabled())))
            onView(withText(R.string.rewarded_loading_button)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            scenario.onActivity { dialog.render(RewardedAdState.FAILED) }
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(not(isEnabled())))
            var failureText = ""
            scenario.onActivity { failureText = it.getString(R.string.rewarded_status_failed) }
            onView(withId(android.R.id.message)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText(org.hamcrest.Matchers.containsString(failureText))))
            screenshot("rewarded-failed")
            onView(withId(android.R.id.button3)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(not(isEnabled())))
            awaitUi(scenario) { retries == 1 }
            scenario.onActivity { assertEquals(1, retries); assertTrue(dialog.isShowing) }
            scenario.onActivity { assertEquals(0, watches); dialog.render(RewardedAdState.READY) }
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isEnabled())).perform(click())
            awaitUi(scenario) { watches == 1 && !dialog.isShowing }
            scenario.onActivity { assertEquals(1, watches); assertFalse(dialog.isShowing) }
        }
    }

    @Test fun unavailableAndCancelledDialogNeverRequestsPlayback() {
        ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
            lateinit var dialog: StealthRewardDialog
            var watches = 0
            scenario.onActivity {
                dialog = StealthRewardDialog(it) { watches++ }
                dialog.show(RewardedAdState.UNAVAILABLE)
            }
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(not(isEnabled())))
            onView(withId(android.R.id.button2)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            awaitUi(scenario) { !dialog.isShowing }
            scenario.onActivity {
                dialog.render(RewardedAdState.READY)
                assertEquals(0, watches)
                assertFalse(dialog.isShowing)
            }
        }
    }

    @Test fun menuSelectsFaqInLightTheme() = verifyMenu(AppCompatDelegate.MODE_NIGHT_NO)
    @Test fun menuSelectsFaqInDarkTheme() = verifyMenu(AppCompatDelegate.MODE_NIGHT_YES)

    private fun verifyMenu(mode: Int) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val previous = AppCompatDelegate.getDefaultNightMode()
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
        try {
            ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
                var selected = 0
                onView(withId(R.id.infoToolbar)).check(matches(isDisplayed()))
                scenario.onActivity {
                    createMainMenu(it.findViewById(R.id.infoToolbar)) { item ->
                        selected = item.itemId
                        true
                    }.show()
                }
                // Espresso's idle queue can precede the popup enter transition finishing.
                val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                while (true) {
                    try {
                        onView(withText(R.string.menu_contact)).check(matches(isDisplayed()))
                        break
                    } catch (error: AssertionError) {
                        if (android.os.SystemClock.elapsedRealtime() >= deadline) throw error
                        android.os.SystemClock.sleep(50)
                    }
                }
                onView(withText(R.string.menu_share)).check(matches(isDisplayed()))
                screenshot("menu-$mode")
                // Avoid matching the host toolbar title, which also uses the FAQ string.
                onView(withText(R.string.menu_faq)).inRoot(
                    androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()
                ).perform(click())
                awaitUi(scenario) { selected == R.id.menu_faq }
                scenario.onActivity { assertEquals(R.id.menu_faq, selected) }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(previous) }
        }
    }

    /** Input and AlertDialog dismissal may post work after Espresso becomes idle on API 32. */
    private fun awaitUi(scenario: ActivityScenario<InfoActivity>, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        do {
            var satisfied = false
            scenario.onActivity { satisfied = condition() }
            if (satisfied) return
            android.os.SystemClock.sleep(25)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        scenario.onActivity { assertTrue("UI action must complete within 5 seconds", condition()) }
    }

    private fun screenshot(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ui-three-checks")
            .apply { mkdirs() }
        val drawn = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            android.view.Choreographer.getInstance().postFrameCallback {
                android.view.Choreographer.getInstance().postFrameCallback { drawn.countDown() }
            }
        }
        check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(dir, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
