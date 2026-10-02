package com.divinegames.mmover

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Insets for help screens; Main applies its top insets to the header only. */
internal fun AppCompatActivity.applyHelpInsets(root: View) {
    @Suppress("DEPRECATION")
    window.statusBarColor = ContextCompat.getColor(this, R.color.settings_surface)
    WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
