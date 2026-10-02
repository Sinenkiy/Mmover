package com.divinegames.mmover

import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat

internal fun createMainMenu(anchor: View, onItemClick: (MenuItem) -> Boolean): PopupMenu =
    PopupMenu(anchor.context, anchor).apply {
        menuInflater.inflate(R.menu.main_menu, menu)
        setForceShowIcon(true)
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon?.mutate()?.setTint(
                ContextCompat.getColor(anchor.context, R.color.glass_text)
            )
        }
        setOnMenuItemClickListener(onItemClick)
    }
