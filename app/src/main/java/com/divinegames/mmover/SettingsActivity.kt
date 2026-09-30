package com.divinegames.mmover // Ваш пакет

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // НАЧАЛО ВСТАВКИ: КОД ДЛЯ ОТСТУПОВ
        // Получаем корневой контейнер этой активити
        val rootView = findViewById<View>(android.R.id.content)

        // Говорим системе: "Когда будешь рисовать экран, учти размеры системных панелей"
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Добавляем внутренние отступы (padding):
            // Сверху — для статус-бара, Снизу — для кнопок навигации
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)

            insets
        }
        // КОНЕЦ ВСТАВКИ



        // Вставляем наш фрагмент с настройками в контейнер
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
        // --- НОВАЯ ЛОГИКА ДЛЯ КНОПКИ "НАЗАД" ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Создаем интент для перезапуска приложения через StartActivity
                val intent = Intent(this@SettingsActivity, StartActivity::class.java)

                // Флаги, которые очищают все предыдущие экраны
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                // Запускаем!
                startActivity(intent)
            }
        })
    }
}