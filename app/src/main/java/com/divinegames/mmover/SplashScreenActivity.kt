package com.divinegames.mmover

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager

class SplashScreenActivity : BaseActivity() {

    private val SPLASH_DELAY = 2000L // 2 секунды

    override fun onCreate(savedInstanceState: Bundle?) {
        // Мы не используем setContentView, так как фон уже задан темой
        super.onCreate(savedInstanceState)

        // 1. Устанавливаем настройки по умолчанию "на лету"
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        // 2. (КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ) Принудительно читаем любую настройку,
        //    чтобы заставить систему сохранить все значения по умолчанию в файл.
        //    Это небольшой "трюк", который гарантирует сохранение.
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean("vibration_enabled", true)

        // 3. Запускаем таймер и переход на следующий экран
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                // После задержки просто идем на StartActivity
                startActivity(Intent(this, StartActivity::class.java))
                finish()
            }
        }, SPLASH_DELAY)
    }
}