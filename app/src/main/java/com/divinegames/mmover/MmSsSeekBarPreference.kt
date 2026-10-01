package com.divinegames.mmover

import android.content.Context
import android.util.AttributeSet


import androidx.preference.SeekBarPreference

class MmSsSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    // важное отличие — берем стиль из androidx.preference
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle
) : SeekBarPreference(context, attrs, defStyleAttr) {

    init {
        // The localized summary displays minutes/seconds. AndroidX's numeric label
        // would overwrite that format with raw five-second steps during dragging.
        showSeekBarValue = false
    }
}