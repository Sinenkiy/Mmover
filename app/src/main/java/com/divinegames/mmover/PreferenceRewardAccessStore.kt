package com.divinegames.mmover

import android.content.SharedPreferences

internal class PreferenceRewardAccessStore(
    private val prefs: SharedPreferences,
    private val dateKey: String = "last_stealth_activation_date",
    private val enabledKey: String = "stealth_mode_enabled"
) : RewardAccessStore {
    override fun read() = RewardAccessRecord(
        prefs.getString(dateKey, "").orEmpty(),
        prefs.getBoolean(enabledKey, false)
    )

    override fun write(record: RewardAccessRecord) {
        prefs.edit()
            .putString(dateKey, record.date)
            .putBoolean(enabledKey, record.enabled)
            .apply()
    }
    companion object {
        const val TIMER_DATE = "last_timer_activation_date"
        const val TIMER_ENABLED = "timer_access_enabled"
    }
}
