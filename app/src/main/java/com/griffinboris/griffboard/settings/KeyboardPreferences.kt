package com.griffinboris.griffboard.settings

import android.content.Context
import androidx.core.content.edit

class KeyboardPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    var haptics: Boolean
        get() = preferences.getBoolean("haptics", true)
        set(value) { preferences.edit { putBoolean("haptics", value) } }
    var numberRow: Boolean
        get() = preferences.getBoolean("numberRow", true)
        set(value) { preferences.edit { putBoolean("numberRow", value) } }
    var language: String
        get() = preferences.getString("language", "auto")!!
        set(value) { preferences.edit { putString("language", value) } }
}
