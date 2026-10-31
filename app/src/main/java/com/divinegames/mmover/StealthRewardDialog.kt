package com.divinegames.mmover

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.divinegames.mmover.databinding.DialogRewardAccessBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal enum class RewardedAdState { UNAVAILABLE, LOADING, READY, FAILED }

/** Presentation only: loading, consent and granting access remain with the controllers. */
internal class StealthRewardDialog(
    private val context: Context,
    onRetry: () -> Unit = {},
    @androidx.annotation.StringRes titleRes: Int = R.string.stealth_dialog_title,
    @androidx.annotation.StringRes private val messageRes: Int = R.string.stealth_dialog_message,
    onDismiss: () -> Unit = {},
    onWatch: () -> Unit
) {
    private var state = RewardedAdState.UNAVAILABLE
    private val builder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_MouseMover_RewardDialog)
    private val ui = DialogRewardAccessBinding.inflate(LayoutInflater.from(builder.context))
    // Keep actions in the scrollable content: the native stacked footer clips at large font sizes.
    private val dialog = builder.setTitle(titleRes).setView(ui.root).create()

    init {
        dialog.setOnDismissListener { onDismiss() }
        ui.rewardRetry.setOnClickListener { if (state == RewardedAdState.FAILED) onRetry() }
        ui.rewardCancel.setOnClickListener { dialog.dismiss() }
        ui.rewardWatch.setOnClickListener {
            if (state == RewardedAdState.READY) {
                dialog.dismiss()
                onWatch()
            }
        }
    }

    val isShowing get() = dialog.isShowing

    fun show(initialState: RewardedAdState) {
        dialog.show()
        render(initialState)
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
        ui.rewardMessage.text = context.getString(R.string.rewarded_dialog_message_format,
            context.getString(messageRes), context.getString(status))
        ui.rewardWatch.apply {
            setText(if (state == RewardedAdState.LOADING) R.string.rewarded_loading_button else R.string.stealth_watch_ad)
            isEnabled = state == RewardedAdState.READY
            alpha = if (isEnabled) 1f else 0.45f
        }
        ui.rewardRetry.isVisible = state == RewardedAdState.FAILED
    }

    fun dismiss() = dialog.dismiss()
}
