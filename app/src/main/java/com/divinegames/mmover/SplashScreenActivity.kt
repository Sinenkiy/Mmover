package com.divinegames.mmover

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager

class SplashScreenActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false
    private val openStart = Runnable {
        if (!navigated && !isFinishing && !isDestroyed && hasWindowFocus() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            navigated = true
            startActivity(Intent(this, StartActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
    }

    override fun onResume() {
        super.onResume()
        scheduleNavigation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleNavigation() else handler.removeCallbacks(openStart)
    }

    private fun scheduleNavigation() {
        handler.removeCallbacks(openStart)
        if (!navigated && hasWindowFocus()) handler.postDelayed(openStart, 2000L)
    }

    override fun onPause() {
        handler.removeCallbacks(openStart)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(openStart)
        super.onDestroy()
    }
}
