package com.divinegames.mmover

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder

class BackgroundPreviewPreference(context: Context, attrs: AttributeSet?) : DropDownPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.backgroundPreview) as PatternPreviewView).showPattern(value, entry)
    }
}
