package com.divinegames.mmover

import android.os.Bundle
import android.text.Html
import android.widget.ImageView
import android.widget.TextView

class InfoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val imageView = findViewById<ImageView>(R.id.infoImageView)
        val textView = findViewById<TextView>(R.id.infoTextView)

        // Получаем данные, которые передала MainActivity
        val imageResId = intent.getIntExtra("EXTRA_IMAGE_RES_ID", 0)
        val textResId = intent.getIntExtra("EXTRA_TEXT_RES_ID", 0)
        val titleResId = intent.getIntExtra("EXTRA_TITLE_RES_ID", 0)

        // Устанавливаем заголовок экрана
        if (titleResId != 0) {
            title = getString(titleResId)
        }

        // Устанавливаем картинку и текст
        if (imageResId != 0) {
            imageView.setImageResource(imageResId)
        }

        // --- Логика для текста (с версией) ---
        if (textResId != 0) {
            var finalHtmlText = ""

            // Проверяем, это экран FAQ?
            if (textResId == R.string.faq_text) {
                // 1. Получаем версию приложения (напр., "1.03")
                val versionName = BuildConfig.VERSION_NAME
                // 2. Получаем наш префикс ("Версия приложения:")
                val versionPrefix = getString(R.string.app_version_prefix)
                // 3. Собираем строку
                val versionString = "$versionPrefix $versionName<br><br>" // <br> это перенос строки в HTML
                // 4. Добавляем ее в начало текста FAQ
                finalHtmlText = versionString + getString(R.string.faq_text)
            } else {
                // Для других экранов (если они будут) просто показываем текст
                finalHtmlText = getString(textResId)
            }

            // Устанавливаем итоговый текст с обработкой HTML-тегов
            textView.text = Html.fromHtml(finalHtmlText, Html.FROM_HTML_MODE_LEGACY)
        }
    }
}