package com.divinegames.mmover

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Explicit long-press feedback, independent of the system tooltip implementation. */
internal class ButtonHints(owner: LifecycleOwner) : DefaultLifecycleObserver {
    private var popup: PopupWindow? = null
    private var anchor: View? = null
    private val hide = Runnable { dismiss() }

    init { owner.lifecycle.addObserver(this) }

    fun bind(button: View) {
        button.setOnLongClickListener { view ->
            if (!view.isAttachedToWindow || !view.isEnabled || view.contentDescription.isNullOrBlank()) {
                return@setOnLongClickListener false
            }
            dismiss()
            val density = view.resources.displayMetrics.density
            fun dp(value: Int) = (value * density + 0.5f).toInt()
            val label = AppCompatTextView(view.context).apply {
                text = view.contentDescription
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.glass_text))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                maxWidth = (view.resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(48))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            anchor = view
            popup = PopupWindow(label, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
                setBackgroundDrawable(ContextCompat.getDrawable(view.context, R.drawable.popup_menu))
                elevation = dp(6).toFloat()
                // The finger is still on the anchor when the hint appears. Do not
                // intercept its release as an outside touch and immediately close.
                isTouchable = false
                isOutsideTouchable = false
                setOnDismissListener {
                    anchor?.removeCallbacks(hide)
                    anchor = null
                    popup = null
                }
                showAsDropDown(view, 0, dp(4), Gravity.END)
            }
            view.postDelayed(hide, 3000L)
            true // Do not also invoke the button's normal action on release.
        }
    }

    private fun dismiss() {
        anchor?.removeCallbacks(hide)
        popup?.dismiss()
        popup = null
        anchor = null
    }

    override fun onPause(owner: LifecycleOwner) {
        dismiss()
    }
    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
        owner.lifecycle.removeObserver(this)
    }
}
