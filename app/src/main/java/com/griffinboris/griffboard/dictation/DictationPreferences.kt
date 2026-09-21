package com.griffinboris.griffboard.dictation

import android.content.Context
import androidx.core.content.edit

class DictationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("dictation", Context.MODE_PRIVATE)
    var floating: Boolean
        get() = preferences.getBoolean("floating", false)
        set(value) { preferences.edit { putBoolean("floating", value) } }
    var tile: Boolean
        get() = preferences.getBoolean("tile", false)
        set(value) { preferences.edit { putBoolean("tile", value) } }
    var notification: Boolean
        get() = preferences.getBoolean("notification", false)
        set(value) { preferences.edit { putBoolean("notification", value) } }
    var button: Boolean
        get() = preferences.getBoolean("button", false)
        set(value) { preferences.edit { putBoolean("button", value) } }

    fun allows(source: String?) = when (source) {
        "floating" -> floating
        "tile" -> tile
        "notification" -> notification
        "button" -> button
        else -> false
    }
}
