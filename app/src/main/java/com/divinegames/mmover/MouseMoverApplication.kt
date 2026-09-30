package com.divinegames.mmover // Убедись, что это твой пакет

import android.app.Application
import com.yandex.mobile.ads.common.MobileAds
import androidx.preference.PreferenceManager

class MouseMoverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Этот код выполнится один раз при самом первом запуске приложения,
        // еще до того, как появится какой-либо экран.
        // Он гарантированно сохранит все настройки по умолчанию в файл.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // Инициализация Yandex Mobile Ads
        MobileAds.initialize(this) {
            // SDK готов, можно грузить рекламу
        }
        // 3. Увеличиваем счетчик запусков на 1 и сохраняем
        val launchCount = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launchCount).apply()
    }
}