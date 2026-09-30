package com.divinegames.mmover

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference

class MmSsSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    // важное отличие — берем стиль из androidx.preference
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle
) : SeekBarPreference(context, attrs, defStyleAttr) {

    init {
        // чтобы элемент справа вообще рисовался
        showSeekBarValue = true
    }

    var stepSec = 5  // множитель

    private fun format(v: Int): String {
        val m = v / 60
        val s = v % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val valueView = holder.findViewById(androidx.preference.R.id.seekbar_value) as? TextView
        valueView?.text = format(value * stepSec)
    }

    override fun setValue(v: Int) {
        super.setValue(v)
        // просим перерисовать, чтобы обновился текст
        notifyChanged()
    }
}