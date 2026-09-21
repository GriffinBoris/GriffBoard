package com.griffinboris.griffboard.settings

import android.annotation.SuppressLint
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.griffinboris.griffboard.models.ModelDownloadWorker
import com.griffinboris.griffboard.models.ModelStore
import com.griffinboris.griffboard.models.WhisperModel

@SuppressLint("SetTextI18n")
class ModelCard(
    private val activity: AppCompatActivity,
    private val model: WhisperModel,
    private val changed: () -> Unit,
) {
    private val ui = SettingsViews(activity)
    private val store = ModelStore(activity)
    private val workManager = WorkManager.getInstance(activity)
    private val column = ui.column()
    private val status: TextView = ui.label("", 14f).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val progress = LinearProgressIndicator(activity).apply { max = 100 }
    private val action = ui.button("Download") { act() }
    private val remove = ui.button("Remove", secondary = true) { delete() }
    private var work: WorkInfo? = null
    val view = ui.card(column)

    init {
        column.addView(ui.label(model.label, 18f, true))
        column.addView(ui.label("${model.description}\n${model.sizeLabel} download", 14f))
        column.addView(status)
        column.addView(progress)
        column.addView(LinearLayout(activity).apply {
            addView(action, LinearLayout.LayoutParams(0, -2, 1f))
            addView(remove, LinearLayout.LayoutParams(-2, -2).apply { marginStart = activity.dp(8) })
        })
        workManager.getWorkInfosForUniqueWorkLiveData(ModelDownloadWorker.workName(model)).observe(activity) { entries ->
            work = entries.firstOrNull { !it.state.isFinished } ?: entries.firstOrNull()
            refresh()
        }
        refresh()
    }

    fun refresh() {
        val current = work
        val active = current != null && !current.state.isFinished
        val installed = store.installed(model)
        val selected = store.selected()?.id == model.id
        val percent = current?.progress?.getInt("percent", 0) ?: 0
        status.text = when {
            active && current.state == WorkInfo.State.RUNNING -> "Downloading · $percent%"
            active -> "Waiting for a network connection…"
            installed && selected -> "✓ Selected for voice typing"
            installed -> "Downloaded · ready to use"
            current?.state == WorkInfo.State.FAILED -> current.outputData.getString("error") ?: "Download interrupted. Please retry."
            current?.state == WorkInfo.State.CANCELLED -> "Download cancelled"
            model.id == "base.en-q5_1" -> "Recommended to start"
            else -> "Not downloaded"
        }
        progress.visibility = if (active) View.VISIBLE else View.GONE
        progress.progress = percent
        action.text = when { active -> "Cancel"; selected -> "Selected"; installed -> "Use model"; else -> "Download" }
        action.isEnabled = !selected || active
        action.visibility = if (selected && !active) View.GONE else View.VISIBLE
        view.strokeColor = com.google.android.material.color.MaterialColors.getColor(view,
            if (selected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant)
        remove.visibility = if (installed && !active) View.VISIBLE else View.GONE
    }

    private fun act() {
        if (work?.state?.isFinished == false) {
            workManager.cancelUniqueWork(ModelDownloadWorker.workName(model))
        } else if (store.installed(model)) {
            store.select(model)
            changed()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Download ${model.label}?")
                .setMessage("${model.sizeLabel} will be downloaded from Hugging Face. Wi-Fi is recommended. Once downloaded, voice typing works offline.")
                .setPositiveButton("Download") { _, _ -> ModelDownloadWorker.enqueue(activity, model) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun delete() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Remove ${model.label}?")
            .setMessage("This frees ${model.sizeLabel} of storage. You can download it again later.")
            .setPositiveButton("Remove") { _, _ ->
                try { store.delete(model); changed() }
                catch (error: IllegalStateException) {
                    MaterialAlertDialogBuilder(activity).setMessage(error.message).setPositiveButton("OK", null).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
