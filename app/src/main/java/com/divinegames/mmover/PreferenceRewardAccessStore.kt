package com.divinegames.mmover

import android.content.SharedPreferences

internal class PreferenceRewardAccessStore(private val prefs: SharedPreferences) : RewardAccessStore {
    override fun read() = RewardAccessRecord(
        prefs.getString("last_stealth_activation_date", "").orEmpty(),
        prefs.getBoolean("stealth_mode_enabled", false)
    )

    override fun write(record: RewardAccessRecord) {
        prefs.edit()
            .putString("last_stealth_activation_date", record.date)
            .putBoolean("stealth_mode_enabled", record.enabled)
            .apply()
    }
}
