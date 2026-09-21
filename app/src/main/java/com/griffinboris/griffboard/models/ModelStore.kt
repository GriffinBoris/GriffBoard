package com.griffinboris.griffboard.models

import android.content.Context
import androidx.core.content.edit
import java.io.File

class ModelStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("models", Context.MODE_PRIVATE)

    fun file(model: WhisperModel) = File(directory, model.filename)
    fun installed(model: WhisperModel) = file(model).length() == model.bytes
    fun selected(): WhisperModel? = ModelCatalog.models.firstOrNull {
        it.id == preferences.getString("selected", null) && installed(it)
    }
    fun select(model: WhisperModel) {
        check(installed(model)) { "Download this model first." }
        preferences.edit { putString("selected", model.id) }
    }
    fun delete(model: WhisperModel) {
        check(!file(model).exists() || file(model).delete()) { "Could not remove the model." }
        if (preferences.getString("selected", null) == model.id) {
            preferences.edit { remove("selected") }
        }
    }
}
