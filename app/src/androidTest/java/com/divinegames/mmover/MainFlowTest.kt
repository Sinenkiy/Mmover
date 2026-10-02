package com.divinegames.mmover

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)

    @Test fun headerFitsSystemInsetsWithoutClippingOrAccumulatingPadding() = withMain { scenario ->
        fun checkHeader(label: String) {
            await(scenario, "header laid out") {
                val header = it.findViewById<View>(R.id.titleBar)
                header.isLaidOut && !header.isLayoutRequested
            }
            scenario.onFrameActivity { activity ->
                val header = activity.findViewById<View>(R.id.titleBar)
                val row = activity.findViewById<View>(R.id.titleContent)
                val density = activity.resources.displayMetrics.density
                val touchSize = (48 * density + 0.5f).toInt()
                val safe = androidx.core.view.ViewCompat.getRootWindowInsets(header)!!
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                fun position(view: View) = IntArray(2).also(view::getLocationOnScreen)
                val headerPosition = position(header)
                val rowPosition = position(row)
                assertEquals("One full touch target, no extra API-dependent space", touchSize, row.height)
                assertEquals("Inset contributes exactly once", row.height + header.paddingTop, header.height)
                assertEquals("Controls immediately below system safe area", safe.top, rowPosition[1])
                for (id in listOf(R.id.settingsButton, R.id.backButton, R.id.optionsButton)) {
                    val button = activity.findViewById<View>(id)
                    val xy = position(button)
                    assertEquals(touchSize, button.width)
                    assertEquals(touchSize, button.height)
                    assertTrue("Button below status bar / camera", xy[1] >= safe.top)
                    assertTrue("Button fully inside header", xy[1] + button.height <= headerPosition[1] + header.height)
                    assertTrue("Button inside left safe edge", xy[0] >= safe.left)
                    assertTrue("Button inside right safe edge", xy[0] + button.width <=
                        activity.window.decorView.width - safe.right)
                }
                assertEquals("Banner starts below header", header.bottom,
                    activity.findViewById<View>(R.id.ad_view_container_top).top)
                val dir = File(activity.getExternalFilesDir(null), "header-checks").apply { mkdirs() }
                File(dir, "$label.txt").writeText("API=${android.os.Build.VERSION.SDK_INT} density=$density " +
                    "headerDp=${header.height / density} rowDp=${row.height / density} " +
                    "headerScreenY=${headerPosition[1]} rowScreenY=${rowPosition[1]} safeTop=${safe.top}")
                // Capture the app layout independently of any external UMP dialog.
                val root = activity.findViewById<View>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                File(dir, "$label-layout.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        checkHeader("initial")
        repeat(3) {
            scenario.onFrameActivity {
                androidx.core.view.ViewCompat.requestApplyInsets(it.findViewById(R.id.titleBar))
            }
            instrumentation.waitForIdleSync()
            checkHeader("redispatched")
        }
        scenario.recreate()
        checkHeader("recreated")
    }

    @Test fun compactControlsLeaveCenterClearAndPaymentDoesNotGrantAccess() = withMain { scenario ->
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        scenario.onFrameActivity { activity ->
            if (android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.glass_header),
                    activity.window.statusBarColor)
            }
            val top = activity.findViewById<View>(R.id.modePanel)
            val bottom = activity.findViewById<View>(R.id.controlsPanel)
            assertTrue("Mouse surface stays clear", top.bottom < bottom.top)
            assertEquals(activity.getString(R.string.motion_description, 3, 0, 5),
                activity.findViewById<TextView>(R.id.modeDescription).text.toString())
            assertEquals(activity.getString(R.string.app_name),
                activity.findViewById<TextView>(R.id.titleTextView).text.toString())
        }
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withId(R.id.removeAdsButton)
        ).perform(androidx.test.espresso.action.ViewActions.click())
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withText(R.string.remove_ads_unavailable)
        ).check(androidx.test.espresso.assertion.ViewAssertions.matches(
            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withText(android.R.string.ok)
        ).perform(androidx.test.espresso.action.ViewActions.click())
        assertFalse(prefs.getBoolean("stealth_mode_enabled", false))
        assertStopped(scenario)
        screenshot("main-controls")
    }

    @Test fun movementCyclesStopsAndStaysStoppedAfterBackground() = withMain { scenario ->
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        await(scenario, "movement starts") { isMoving(it) }
        var position = 0f to 0f
        scenario.onFrameActivity {
            val bg = it.findViewById<MovingBackgroundView>(R.id.movingBackground)
            position = field(bg, "bitmapX") as Float to field(bg, "bitmapY") as Float
            assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        }
        await(scenario, "bitmap actually moves", 1500) {
            val bg = it.findViewById<MovingBackgroundView>(R.id.movingBackground)
            position != (field(bg, "bitmapX") as Float to field(bg, "bitmapY") as Float)
        }
        await(scenario, "automatic waiting") { it.findViewById<View>(R.id.centerStatusTextView).visibility == View.VISIBLE }
        scenario.onFrameActivity {
            assertFalse(isMoving(it))
            assertEquals(0.02f, it.window.attributes.screenBrightness, 0.001f)
        }
        // Avoid screenshot I/O during the short automatic-restart window.
        await(scenario, "automatic restart", 8000) { isMoving(it) }
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        assertStopped(scenario)
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        SystemClock.sleep(3500)
        assertStopped(scenario)
        screenshot("stopped-after-background")
    }

    @Test fun zeroPauseAndRapidClicksDoNotLeaveTimersRunning() = withMain { scenario ->
        prefs.edit().putInt("active_duration", 1).putInt("pause_duration", 0).commit()
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        SystemClock.sleep(3200)
        scenario.onFrameActivity {
            assertTrue(isMoving(it))
            assertEquals(View.GONE, it.findViewById<View>(R.id.centerStatusTextView).visibility)
            it.findViewById<View>(R.id.playPauseButton).performClick()
            repeat(10) { _ -> it.findViewById<View>(R.id.playPauseButton).performClick() }
        }
        SystemClock.sleep(1500)
        assertStopped(scenario)
    }

    @Test fun blockedAdsDoNotInitializeAndRewardIsNotGranted() = withMain { scenario ->
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        scenario.onFrameActivity {
            assertFalse(it.adsController.diagnostics.sdkInitializationStarted)
            assertFalse(it.adsController.diagnostics.bannersInitialized)
            it.adsController.showRewarded()
            assertFalse(prefs.getBoolean("stealth_mode_enabled", false))
            assertFalse(it.adsController.diagnostics.rewardedLoaded)

            // The core function remains usable while ad requests are blocked.
            it.findViewById<View>(R.id.playPauseButton).performClick()
            assertTrue(isMoving(it))
        }
    }

    @Test fun recreatedScreenClosesOldAdsControllerAndRejectsFurtherRequests() = withMain { scenario ->
        lateinit var oldController: MainAdsController
        scenario.onFrameActivity { oldController = it.adsController }
        scenario.recreate()
        scenario.onFrameActivity {
            assertNotSame(oldController, it.adsController)
            assertTrue(oldController.diagnostics.closed)
            oldController.onResume()
            oldController.showRewarded()
            oldController.retryRewarded()
            assertFalse(oldController.diagnostics.sdkInitializationStarted)
            assertFalse(oldController.diagnostics.rewardedLoaded)
        }
    }

    @Test fun frameLoopStopsOnPauseAndDetach() = withMain { scenario ->
        prefs.edit().putInt("active_duration", 20).commit()
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        lateinit var background: MovingBackgroundView
        scenario.onFrameActivity {
            background = it.findViewById(R.id.movingBackground)
            it.findViewById<View>(R.id.playPauseButton).performClick()
        }
        fun position() = field(background, "bitmapX") as Float to field(background, "bitmapY") as Float
        var before = 0f to 0f
        scenario.onFrameActivity { before = position() }
        await(scenario, "frame loop moves bitmap") { position() != before }
        scenario.moveToState(Lifecycle.State.CREATED)
        instrumentation.runOnMainSync { before = position() }
        SystemClock.sleep(250)
        instrumentation.runOnMainSync {
            assertEquals(before, position())
            assertEquals(false, field(background, "isMoving"))
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertStopped(scenario)
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        await(scenario, "frame loop restarts") { position() != before }
        scenario.recreate()
        instrumentation.runOnMainSync { before = position() }
        SystemClock.sleep(250)
        instrumentation.runOnMainSync {
            assertFalse(background.isAttachedToWindow)
            assertEquals(before, position())
            assertEquals(false, field(background, "isMoving"))
        }
        assertStopped(scenario)
    }

    @Test fun accessibilityAndProgrammaticClicksApplyRewardRules() = withMain { scenario ->
        await(scenario, "background ready") { it.findViewById<View>(R.id.modeSwitch).isEnabled }
        scenario.onFrameActivity {
            val toggle = it.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.modeSwitch)
            assertFalse(toggle.isPressed)
            assertTrue(toggle.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null))
            assertFalse(toggle.isChecked)
            assertFalse(prefs.getBoolean("stealth_mode_enabled", false))
        }
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withText(R.string.stealth_not_now)
        ).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        prefs.edit().putString("last_stealth_activation_date", java.time.LocalDate.now().toString())
            .putBoolean("stealth_mode_enabled", true).commit()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFrameActivity {
            val toggle = it.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.modeSwitch)
            assertTrue(toggle.isChecked) // Rendering must not consume or disable the reward.
            assertTrue(prefs.getBoolean("stealth_mode_enabled", false))
            toggle.performClick()
            assertFalse(toggle.isChecked)
            assertFalse(prefs.getBoolean("stealth_mode_enabled", true))
            toggle.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null)
            assertTrue(toggle.isChecked)
            assertTrue(prefs.getBoolean("stealth_mode_enabled", false))
        }
    }

    private fun isMoving(activity: MainActivity): Boolean =
        activity.findViewById<TextView>(R.id.playPauseButton).text.toString() ==
            activity.getString(R.string.motion_stop) &&
            activity.findViewById<View>(R.id.centerStatusTextView).visibility == View.GONE

    @Test fun waitingStopsOnClickAndOnBackgroundAndDoesNotResumeAfterRecreation() = withMain { scenario ->
        prefs.edit().putInt("active_duration", 1).commit()
        await(scenario, "background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        await(scenario, "waiting before manual stop") {
            it.findViewById<View>(R.id.centerStatusTextView).visibility == View.VISIBLE
        }
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        SystemClock.sleep(5500)
        assertStopped(scenario)
        scenario.onFrameActivity { it.findViewById<View>(R.id.playPauseButton).performClick() }
        await(scenario, "waiting before background") {
            it.findViewById<View>(R.id.centerStatusTextView).visibility == View.VISIBLE
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        SystemClock.sleep(5500)
        assertStopped(scenario)
        scenario.recreate()
        await(scenario, "recreated background ready") { it.findViewById<View>(R.id.playPauseButton).isEnabled }
        assertStopped(scenario)
    }
    private fun withMain(block: (ActivityScenario<MainActivity>) -> Unit) {
        val keys = listOf("active_duration", "pause_duration", "brightness", "vibration_enabled",
            "stealth_mode_enabled", "last_stealth_activation_date", "never_show_rating", "stop_click_count", "animation_style")
        val saved = keys.associateWith { prefs.all[it] }
        prefs.edit().putInt("active_duration", 3).putInt("pause_duration", 1).putInt("brightness", 80)
            .putBoolean("vibration_enabled", false).putBoolean("stealth_mode_enabled", false)
            .putString("last_stealth_activation_date", "").putBoolean("never_show_rating", true)
            .putString("animation_style", "background_blocks_large").commit()
        // Isolate movement tests from external ads. This exercises the real request gate.
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) {
                activity.adsController.requestPolicy.changingPrivacy = true
            }
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        monitor.addLifecycleCallback(callback)
        try {
            ActivityScenario.launch(MainActivity::class.java).use(block)
        } finally {
            monitor.removeLifecycleCallback(callback)
            val editor = prefs.edit()
            saved.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
        }
    }

    private fun assertStopped(scenario: ActivityScenario<MainActivity>) = scenario.onFrameActivity {
        assertFalse(isMoving(it))
        assertEquals(View.GONE, it.findViewById<View>(R.id.centerStatusTextView).visibility)
        assertEquals(it.getString(R.string.motion_start), it.findViewById<TextView>(R.id.playPauseButton).text.toString())
        assertEquals(0.8f, it.window.attributes.screenBrightness, 0.001f)
        assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun await(scenario: ActivityScenario<MainActivity>, description: String, timeout: Long = 15000,
                      condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            var done = false
            scenario.onFrameActivity { done = condition(it) }
            if (done) return
            SystemClock.sleep(50)
        }
        fail("Timeout: $description")
    }

    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(instance)

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "api36-checks").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
