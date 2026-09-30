package com.divinegames.mmover

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Этот метод вызывается раньше всех. Здесь мы "подменяем" язык.
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onResume() {
        super.onResume()
        // Проверяем, что текущий экран - НЕ SplashScreenActivity
        if (this !is SplashScreenActivity) {
            // Если это любой другой экран, применяем яркость из настроек
            restoreUserSettings()
        }
        // Если это SplashScreenActivity, мы ничего не делаем,
        // и система сама использует яркость телефона.
    }

    public fun restoreUserSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val brightnessValue = prefs.getInt("brightness", 100) / 100f
        setScreenBrightness(brightnessValue)
    }

    public fun setScreenBrightness(level: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = level.coerceIn(0f, 1f)
        window.attributes = layoutParams
    }
}