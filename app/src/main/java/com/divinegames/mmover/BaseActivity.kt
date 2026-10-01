package com.divinegames.mmover

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (this !is SplashScreenActivity) {
            restoreUserSettings()
        }
    }

    protected fun restoreUserSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val brightnessValue = prefs.getInt("brightness", 100) / 100f
        setScreenBrightness(brightnessValue)
    }

    protected fun setScreenBrightness(level: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = level.coerceIn(0f, 1f)
        window.attributes = layoutParams
    }

    protected fun canShowUi(): Boolean {
        return !isFinishing &&
                !isDestroyed &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    protected fun showSafeToast(message: String) {
        runOnUiThread {
            if (!canShowUi()) return@runOnUiThread
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    protected fun showSafeToast(resId: Int) {
        runOnUiThread {
            if (!canShowUi()) return@runOnUiThread
            Toast.makeText(applicationContext, getString(resId), Toast.LENGTH_SHORT).show()
        }
    }
}