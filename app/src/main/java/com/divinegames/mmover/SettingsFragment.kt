package com.divinegames.mmover // Убедись, что это твой пакет

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val brightnessPref = findPreference<SeekBarPreference>("brightness")?.apply {
            // начальная подпись
            summary = "${value} %"

            setOnPreferenceChangeListener { pref, newValue ->
                val v = (newValue as Int).coerceIn(1, 100)
                pref.summary = "$v %"

                // применяем яркость к текущему окну
                val window = requireActivity().window
                val lp = window.attributes
                lp.screenBrightness = v / 100f   // 0f..1f, -1f = по системе
                window.attributes = lp           // ВАЖНО: переустановить attrs обратно

                true // сохранить значение в SharedPreferences
            }
        }

        setupSeekBarListener(
            key = "active_duration",
            format = "%d sec",
            formatter = { value -> "$value ${getString(R.string.seconds_abbreviation_single)}" }
        )
        setupSeekBarListener(
            key = "pause_duration",
            format = "",
            // Умножаем значение (0-360) на 5, чтобы получить реальные секунды (0-1800)
            formatter = { value -> formatSecondsToMmSs(value * 5) }
        )

        setupSeekBarListener(
            key = "movement_speed",
            format = getString(R.string.speed_summary)
        )

        val languagePreference: DropDownPreference? = findPreference("language")
        // 1. Устанавливаем текущее значение.
        //    Теперь выпадающий список всегда будет показывать реальный язык приложения.
        val currentLanguage = LocaleHelper.getLanguage(requireContext())
        languagePreference?.value = currentLanguage

        // 2. Настраиваем слушатель, который сработает только при РЕАЛЬНОМ изменении.
        languagePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val newLanguage = newValue as String
                // Наша проверка теперь будет работать правильно
                if (newLanguage != currentLanguage) {
                    LocaleHelper.setLocale(requireContext(), newLanguage)
                    activity?.setResult(Activity.RESULT_OK)
                    activity?.recreate()
                }
                true
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bg = ContextCompat.getColor(requireContext(), R.color.settings_background)

        // красим весь контейнер и сам список
        view.setBackgroundColor(bg)
        listView.setBackgroundColor(bg)

        // убираем анимации, которые размазывают на 7.x
        (listView as? RecyclerView)?.itemAnimator = null

        // на API 25–26 иногда помогает софт-слой у самого списка
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            listView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        val rv = listView

        // симметричные поля по бокам + «сдвинуть вниз»
        val side = resources.getDimensionPixelSize(R.dimen.prefs_side_padding)   // напр., 24dp
        val top  = resources.getDimensionPixelSize(R.dimen.prefs_top_padding)    // напр., 48dp
        rv.setPadding(side, top, side, 0)
        rv.clipToPadding = false

        // опционально: ограничить максимальную ширину и выровнять по центру на широких экранах
        val maxWidth = resources.getDimensionPixelSize(R.dimen.prefs_max_width)  // напр., 560dp
        rv.doOnLayout {
            val lp = rv.layoutParams
            // если родитель шире maxWidth — центрируем список
            if (rv.width > maxWidth) {
                lp.width = maxWidth
                when (lp) {
                    is FrameLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
                    is LinearLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
                }
                rv.layoutParams = lp
            }
        }

        // Получаем список (RecyclerView), который отображает настройки
        val listView = listView

        // 1. Включаем вертикальную полосу прокрутки
        listView.isVerticalScrollBarEnabled = true

        // 2. Отключаем исчезновение (fading). Теперь полоска будет видна ВСЕГДА.
        listView.isScrollbarFadingEnabled = false

        // Опционально: можно переместить скроллбар поверх содержимого, чтобы он не занимал место
        listView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    }
    // -->

    override fun onStart() {
        super.onStart()
        findPreference<SeekBarPreference>("brightness")?.value?.let { v ->
            val lp = requireActivity().window.attributes
            lp.screenBrightness = v / 100f
            requireActivity().window.attributes = lp
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreferenceSummaries()
        //updateUiForStealthMode()
    }

    /*
    private fun updateUiForStealthMode() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isStealthMode = prefs.getBoolean("stealth_mode_enabled", false)

        val activeDurationPref: Preference? = findPreference("active_duration")
        val pauseDurationPref: Preference? = findPreference("pause_duration")
        val stealthInfoPref: Preference? = findPreference("stealth_mode_info")

        activeDurationPref?.isEnabled = !isStealthMode
        pauseDurationPref?.isEnabled = !isStealthMode
        stealthInfoPref?.isVisible = isStealthMode
    }
     */

    // ДОБАВЬ ЭТУ НОВУЮ ФУНКЦИЮ
    private fun updatePreferenceSummaries() {
        val prefs = preferenceManager.sharedPreferences ?: return

        // 1. Обновляем подпись для Языка
        findPreference<DropDownPreference>("language")?.apply {
            val currentLangCode = prefs.getString("language", null) ?: LocaleHelper.getLanguage(requireContext())
            val index = findIndexOfValue(currentLangCode)
            if (index >= 0) {
                summary = entries[index] // Показывает "Русский" или "English"
            }
        }

        findPreference<DropDownPreference>("animation_style")?.apply {
            // стартовая подпись по текущему значению
            summary = entry

            setOnPreferenceChangeListener { pref, newValue ->
                val dp = pref as DropDownPreference
                val value = newValue as String
                val idx = dp.findIndexOfValue(value)
                if (idx >= 0) {
                    dp.summary = dp.entries[idx]   // моментально обновляем summary
                } else {
                    dp.summary = null              // fallback
                }
                true
            }
        }
    }

    private fun setupSeekBarListener(
        key: String,
        format: String,
        formatter: ((Int) -> String)? = null
    ) {
        val seekBarPreference: SeekBarPreference? = findPreference(key)

        seekBarPreference?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Int
            seekBarPreference.summary = formatter?.invoke(value) ?: String.format(format, value)
            true
        }

        val initialValue = seekBarPreference?.value ?: 0
        seekBarPreference?.summary = formatter?.invoke(initialValue) ?: String.format(format, initialValue)
    }

    private fun formatSecondsToMmSs(seconds: Int): String {
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong())
        val remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes)
        // Получаем правильные сокращения для текущего языка
        val minText = getString(R.string.minutes_abbreviation)
        val secText = getString(R.string.seconds_abbreviation)

        // Используем переменные minText и secText в строке форматирования
        return String.format("%02d %s %02d %s", minutes, minText, remainingSeconds, secText)
    }
}