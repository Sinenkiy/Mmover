package com.divinegames.mmover

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class MouseMoverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Этот код выполнится один раз при самом первом запуске приложения,
        // еще до того, как появится какой-либо экран.
        // Он гарантированно сохранит все настройки по умолчанию в файл.
        // 1. Устанавливаем настройки по умолчанию
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        // --- ЛОГИКА СЧЕТЧИКА ЗАПУСКОВ ---
        // 2. Получаем доступ к SharedPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val defaults = prefs.edit()
        if (!prefs.contains("active_duration")) defaults.putInt("active_duration", 10)
        if (!prefs.contains("movement_speed")) defaults.putInt("movement_speed", 500)
        if (!prefs.contains("animation_style")) defaults.putString("animation_style", "generated_contrast_noise")
        defaults.apply()
        val previousStyle = prefs.getString("animation_style", null)
        val migratedStyle = ProceduralBackground.migrateKey(previousStyle)
        if (migratedStyle != previousStyle) {
            prefs.edit { putString("animation_style", migratedStyle) }
        }

        // 3. Увеличиваем счетчик запусков на 1 и сохраняем
        val launchCount = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launchCount).apply()
    }
}
