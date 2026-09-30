// OnboardingActivity.kt — стабильная версия
package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.widget.Button
import com.divinegames.mmover.BuildConfig
import com.divinegames.mmover.databinding.ActivityOnboardingBinding
import androidx.core.text.HtmlCompat
import android.widget.TextView

import androidx.annotation.StringRes

import android.text.method.LinkMovementMethod
class OnboardingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        // Вся логика, связанная с рекламой, УДАЛЕНА
        //MobileAds.initialize(this)

        // 1. Находим наш TextView
        val descriptionTextView = findViewById<TextView>(R.id.descriptionTextView)

        // 2. Получаем строку с HTML-тегами
        val formattedText = getString(R.string.start_screen_description)

        // 3. Превращаем HTML в форматированный текст и устанавливаем его
        descriptionTextView.text = HtmlCompat.fromHtml(formattedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        //val tv = findViewById<TextView>(R.id.descriptionTextView) // твой id
        //tv.text = HtmlCompat.fromHtml(getString(R.string.start_screen_description),
        //    HtmlCompat.FROM_HTML_MODE_LEGACY)

        // MobileAds.initialize(this) {}
        // findViewById<AdView>(R.id.adViewTop).loadAd(AdRequest.Builder().build())
        // findViewById<AdView>(R.id.adViewBottom).loadAd(AdRequest.Builder().build())

        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener {
            // Когда пользователь нажимает "Старт", мы помечаем, что первый запуск прошел
            val prefs: SharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE).apply()

            // И переходим на главный экран
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun TextView.setHtml(@StringRes resId: Int) {
        val spanned = HtmlCompat.fromHtml(context.getString(resId), HtmlCompat.FROM_HTML_MODE_LEGACY)
        // BufferType принудительно, чтобы не потерять спаны на старых API
        this.setText(spanned, TextView.BufferType.SPANNABLE)
    }
}