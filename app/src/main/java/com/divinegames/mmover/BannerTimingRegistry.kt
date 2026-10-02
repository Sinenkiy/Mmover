package com.divinegames.mmover

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Main-thread registry. Only primitive deadlines survive Activity/process recreation. */
internal object BannerTimingRegistry {
    private val slots = mutableMapOf<String, BannerTimingState>()

    fun slot(context: Context, name: String): BannerTimingState = slots.getOrPut(name) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("banner_timing", Context.MODE_PRIVATE)
        val boot = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val next = BannerTimingState.restoreDeadline(
            if (prefs.contains("$name.next")) prefs.getLong("$name.next", 0) else null,
            prefs.getInt("$name.boot", -2), boot, SystemClock.elapsedRealtime()
        )
        BannerTimingState(next, prefs.getLong("$name.retry", 60_000L).coerceIn(60_000L, 300_000L)) { deadline, retry ->
            prefs.edit().putInt("$name.boot", boot).putLong("$name.next", deadline)
                .putLong("$name.retry", retry).apply()
        }
    }
}
