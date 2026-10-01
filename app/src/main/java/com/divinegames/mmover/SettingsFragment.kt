package com.divinegames.mmover

import android.os.Bundle
import android.view.View
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
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
            // Умножаем значение (0-180) на 5, чтобы получить реальные секунды (0-900)
            formatter = { value -> formatSecondsToMmSs(value * 5) }
        )

        setupSeekBarListener(
            key = "movement_speed",
            format = getString(R.string.speed_summary)
        )

        val languagePreference: DropDownPreference? = findPreference("language")

        val currentLanguage = LocaleHelper.getLanguage(requireContext())
        languagePreference?.value = currentLanguage

        languagePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref, newValue ->
                val newLanguage = newValue as String
                val oldLanguage = (pref as DropDownPreference).value

                if (newLanguage != oldLanguage) {
                    LocaleHelper.setNewLocale(requireContext(), newLanguage)
                    activity?.recreate()
                }
                true
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
            .setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        // A constrained parent owns the width before measurement. Never resize the
        // RecyclerView from its own doOnLayout callback while rows are rebinding.
        val side = resources.getDimensionPixelSize(R.dimen.prefs_side_padding)
        val bottom = resources.getDimensionPixelSize(R.dimen.prefs_bottom_padding)
        listView.setPadding(side, 0, side, bottom)
        listView.clipToPadding = false
        listView.itemAnimator = null
        setDivider(null)
        listView.isVerticalScrollBarEnabled = true
        listView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    }
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

    }

    private fun updatePreferenceSummaries() {
        val prefs = preferenceManager.sharedPreferences ?: return

        // 1. Обновляем подпись для Языка
        findPreference<DropDownPreference>("language")?.apply {
            val currentLangCode = LocaleHelper.getLanguage(requireContext())
            val index = findIndexOfValue(currentLangCode)
            if (index >= 0) {
                summary = entries[index]
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