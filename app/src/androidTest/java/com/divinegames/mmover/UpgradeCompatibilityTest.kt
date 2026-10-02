package com.divinegames.mmover

import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Invoked explicitly by the host runner after installing over the baseline APK. */
class UpgradeCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)

    @Test fun upgradePreservesRussianSettingsAndMigratesLegacyBackground() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installation") == "upgrade")
        assertEquals("com.divinegames.mmover", instrumentation.targetContext.packageName)
        assertEquals(104, BuildConfig.VERSION_CODE)
        assertEquals("1.04", BuildConfig.VERSION_NAME)
        assertEquals(7, prefs.getInt("active_duration", -1))
        assertEquals(700, prefs.getInt("movement_speed", -1))
        assertEquals(17, prefs.getInt("pause_duration", -1))
        assertEquals(65, prefs.getInt("brightness", -1))
        assertFalse(prefs.getBoolean("vibration_enabled", true))
        assertEquals("ru", prefs.getString("Locale.Helper.Selected.Language", null))
        assertEquals("generated_mosaic", prefs.getString("animation_style", null))
        assertTrue(prefs.getBoolean("stealth_mode_enabled", false))
        assertEquals("2026-09-30", prefs.getString("last_stealth_activation_date", null))
        assertEquals("retained", prefs.getString("porting_user_marker", null))
    }

    @Test fun cleanInstallGetsNewDefaults() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("installation") == "clean")
        assertEquals(10, prefs.getInt("active_duration", -1))
        assertEquals(500, prefs.getInt("movement_speed", -1))
        assertEquals(36, prefs.getInt("pause_duration", -1))
        assertEquals("generated_contrast_noise", prefs.getString("animation_style", null))
    }
}
