package com.divinegames.mmover

import java.time.Duration
import java.time.ZonedDateTime

internal data class RewardAccessRecord(val date: String = "", val enabled: Boolean = false)

internal interface RewardAccessStore {
    fun read(): RewardAccessRecord
    fun write(record: RewardAccessRecord)
}

/** Calendar-day access; disabling the mode does not discard today's earned entitlement. */
internal class RewardAccess(
    private val store: RewardAccessStore,
    // Supply only a verified purchase entitlement when billing is integrated.
    private val permanentAccess: () -> Boolean = { false },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() }
) {
    fun hasAccess(): Boolean = permanentAccess() || store.read().date == today()

    fun isEnabled(): Boolean {
        val record = store.read()
        if (permanentAccess() || record.date == today()) return record.enabled
        if (record.enabled) store.write(record.copy(enabled = false))
        return false
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val record = store.read()
        val permitted = enabled && (permanentAccess() || record.date == today())
        if (record.enabled != permitted) store.write(record.copy(enabled = permitted))
        return permitted
    }

    fun grant() = store.write(RewardAccessRecord(today(), true))

    fun nextRefreshDelayMs(): Long {
        val current = now()
        val midnight = current.toLocalDate().plusDays(1).atStartOfDay(current.zone)
        // Also recheck after wall-clock/time-zone changes while the screen is visible.
        return Duration.between(current, midnight).toMillis().coerceIn(1, 60_000)
    }

    private fun today() = now().toLocalDate().toString()
}

/** One presentation can grant at most once; failure, dismissal and disposal close it. */
internal class RewardSession(grant: () -> Unit) {
    private var grantCallback: (() -> Unit)? = grant
    private var rewarded = false
    private var closed = false

    fun reward() {
        if (closed || rewarded) return
        rewarded = true
        val callback = grantCallback
        grantCallback = null
        callback?.invoke()
    }

    fun close() { closed = true; grantCallback = null }
}
