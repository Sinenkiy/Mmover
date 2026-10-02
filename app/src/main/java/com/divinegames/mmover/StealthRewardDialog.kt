package com.divinegames.mmover

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal enum class RewardedAdState { UNAVAILABLE, LOADING, READY, FAILED }

/** Presentation only: loading, consent and granting access remain with the controllers. */
internal class StealthRewardDialog(
    private val context: Context,
    onRetry: () -> Unit = {},
    onWatch: () -> Unit
) {
    private var state = RewardedAdState.UNAVAILABLE
    private val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_MouseMover_RewardDialog)
        .setTitle(R.string.stealth_dialog_title)
        .setMessage(R.string.stealth_dialog_message)
        .setPositiveButton(R.string.stealth_watch_ad, null)
        .setNegativeButton(R.string.stealth_not_now, null)
        .setNeutralButton(R.string.rewarded_retry, null)
        .create()

    init {
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (state == RewardedAdState.FAILED) onRetry()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (state == RewardedAdState.READY) {
                    dialog.dismiss()
                    onWatch()
                }
            }
            render(state)
        }
    }

    val isShowing get() = dialog.isShowing

    fun show(initialState: RewardedAdState) {
        state = initialState
        dialog.show()
    }

    fun render(newState: RewardedAdState) {
        state = newState
        if (!dialog.isShowing) return
        val status = when (state) {
            RewardedAdState.UNAVAILABLE -> R.string.rewarded_status_unavailable
            RewardedAdState.LOADING -> R.string.rewarded_status_loading
            RewardedAdState.READY -> R.string.rewarded_status_ready
            RewardedAdState.FAILED -> R.string.rewarded_status_failed
        }
        dialog.setMessage(context.getString(R.string.stealth_dialog_message) + "\n\n" + context.getString(status))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setText(if (state == RewardedAdState.LOADING) R.string.rewarded_loading_button else R.string.stealth_watch_ad)
            isEnabled = state == RewardedAdState.READY
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility =
            if (state == RewardedAdState.FAILED) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun dismiss() = dialog.dismiss()
}
