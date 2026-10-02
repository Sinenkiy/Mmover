package com.divinegames.mmover

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val currentSettings = AtomicReference<SettingsActivity>()
    private val currentMain = AtomicReference<MainActivity>()
    private val mainCreations = AtomicInteger()
    private val startCreations = AtomicInteger()

    @Test fun unchangedSettingsKeepMainAndLoadedBitmap() = withMain { scenario ->
        val original = currentMain.get()
        val bg = original.findViewById<MovingBackgroundView>(R.id.movingBackground)
        val creationsBeforeSettings = mainCreations.get()
        val bitmap = field(bg, "bitmap")
        val request = field(bg, "loadRequestId")
        openSettings(scenario)
        instrumentation.runOnMainSync { currentSettings.get().onBackPressedDispatcher.onBackPressed() }
        await("return to Main") { original.lifecycle.currentState == Lifecycle.State.RESUMED }
        scenario.onActivity {
            assertSame(original, it)
            assertSame(bitmap, field(bg, "bitmap"))
            assertEquals(request, field(bg, "loadRequestId"))
            assertEquals(creationsBeforeSettings, mainCreations.get())
            assertEquals(0, startCreations.get())
        }
    }

    @Test fun changedSettingsApplyWithoutRecreatingMain() = withMain { scenario ->
        val original = currentMain.get()
        val creationsBeforeSettings = mainCreations.get()
        openSettings(scenario)
        instrumentation.runOnMainSync {
            val settings = currentSettings.get()
            val fragment = settings.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
            fragment.findPreference<SeekBarPreference>("brightness")!!.value = 40
            fragment.findPreference<DropDownPreference>("animation_style")!!.value = "generated_stripes"
            assertTrue(settings.onSupportNavigateUp())
        }
        await("changed background") {
            original.lifecycle.currentState == Lifecycle.State.RESUMED &&
                field(original.findViewById<MovingBackgroundView>(R.id.movingBackground), "currentBackgroundKey") == "generated_stripes"
        }
        scenario.onActivity {
            assertSame(original, it)
            assertEquals(0.4f, it.window.attributes.screenBrightness, 0.001f)
            assertEquals(creationsBeforeSettings, mainCreations.get())
            assertEquals(0, startCreations.get())
        }
    }

    @Test fun restoredSettingsFragmentRetainsSavedState() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.supportFragmentManager.executePendingTransactions()
                val fragment = it.supportFragmentManager.findFragmentById(android.R.id.content)!!
                fragment.savedStateRegistry.registerSavedStateProvider("test-marker") {
                    Bundle().apply { putString("value", "retained") }
                }
            }
            scenario.recreate()
            scenario.onActivity {
                it.supportFragmentManager.executePendingTransactions()
                assertEquals(1, it.supportFragmentManager.fragments.size)
                val fragment = it.supportFragmentManager.findFragmentById(android.R.id.content)!!
                assertEquals("retained", fragment.savedStateRegistry.consumeRestoredStateForKey("test-marker")?.getString("value"))
            }
        }
    }

    @Test fun languageChangeRecreatesMainOnceWithoutStartActivity() = withMain { scenario ->
        val original = currentMain.get()
        val creationsBeforeSettings = mainCreations.get()
        openSettings(scenario)
        val oldSettings = currentSettings.get()
        instrumentation.runOnMainSync {
            val fragment = oldSettings.supportFragmentManager.findFragmentById(android.R.id.content) as SettingsFragment
            val language = fragment.findPreference<DropDownPreference>("language")!!
            assertTrue(language.callChangeListener("de"))
            language.value = "de"
        }
        await("settings recreation") { currentSettings.get() !== oldSettings && currentSettings.get().lifecycle.currentState == Lifecycle.State.RESUMED }
        instrumentation.runOnMainSync { currentSettings.get().onBackPressedDispatcher.onBackPressed() }
        await("translated Main") { currentMain.get() !== original && currentMain.get().lifecycle.currentState == Lifecycle.State.RESUMED }
        instrumentation.runOnMainSync {
            assertEquals("de", currentMain.get().resources.configuration.locales[0].language)
            assertEquals(creationsBeforeSettings + 1, mainCreations.get())
            assertEquals(0, startCreations.get())
        }
    }

    private fun openSettings(scenario: ActivityScenario<MainActivity>) {
        // API 25 deliberately keeps the existing 40dp header, clipping a 48dp target.
        // Exercise navigation here; visible hit targets are checked separately on device.
        scenario.onActivity { it.findViewById<View>(R.id.settingsButton).performClick() }
        await("settings visible") {
            val settings = currentSettings.get()
            settings?.lifecycle?.currentState == Lifecycle.State.RESUMED &&
                settings.supportFragmentManager.findFragmentById(android.R.id.content)?.view?.isLaidOut == true
        }
        instrumentation.waitForIdleSync()
    }

    private fun withMain(block: (ActivityScenario<MainActivity>) -> Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val saved = listOf("brightness", "animation_style", "language", "Locale.Helper.Selected.Language").associateWith { prefs.all[it] }
        val originalLocale = Locale.getDefault()
        prefs.edit().putInt("brightness", 80).putString("animation_style", "generated_mosaic")
            .putString("language", "en").putString("Locale.Helper.Selected.Language", "en").commit()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) {
                currentMain.set(activity)
                mainCreations.incrementAndGet()
                activity.adsController.requestPolicy.changingPrivacy = true
            }
            if (activity is SettingsActivity && stage == Stage.RESUMED) currentSettings.set(activity)
            if (activity is StartActivity && stage == Stage.CREATED) startCreations.incrementAndGet()
        }
        monitor.addLifecycleCallback(callback)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("background loaded") { currentMain.get().findViewById<View>(R.id.optionsButton).isEnabled }
                block(scenario)
            }
        } finally {
            instrumentation.runOnMainSync {
                currentSettings.get()?.let { if (!it.isDestroyed) it.finish() }
                currentMain.get()?.let { if (!it.isDestroyed) it.finish() }
            }
            monitor.removeLifecycleCallback(callback)
            prefs.edit().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        null -> remove(key)
                        is Int -> putInt(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
            Locale.setDefault(originalLocale)
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Timeout: $description")
    }

    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(instance)
}
