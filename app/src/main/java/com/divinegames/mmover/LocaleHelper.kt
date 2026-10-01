package com.divinegames.mmover

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {

    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    fun onAttach(context: Context): Context {
        val language = getPersistedLanguage(context, Locale.getDefault().language)
        return updateContextLocale(context, language)
    }

    fun getLanguage(context: Context): String {
        return getPersistedLanguage(context, Locale.getDefault().language)
    }

    /**
     * Вызывать только когда пользователь ЯВНО выбрал новый язык в настройках.
     * Эта функция сохраняет язык и возвращает context с новой локалью.
     */
    fun setNewLocale(context: Context, language: String): Context {
        persistLanguage(context, language)
        return updateContextLocale(context, language)
    }

    /**
     * Только применяет локаль к context, ничего не сохраняя.
     */
    private fun updateContextLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    private fun getPersistedLanguage(context: Context, defaultLanguage: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return prefs.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
    }
}