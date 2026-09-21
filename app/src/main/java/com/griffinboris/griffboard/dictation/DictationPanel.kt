package com.griffinboris.griffboard.dictation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.color.MaterialColors
import com.griffinboris.griffboard.keyboard.VoiceWaveform
import com.griffinboris.griffboard.settings.SettingsViews
import com.griffinboris.griffboard.settings.dp
import com.griffinboris.griffboard.voice.RecordingProgress
import com.griffinboris.griffboard.voice.VoiceController

@SuppressLint("ViewConstructor", "SetTextI18n")
class DictationPanel(context: Context, action: () -> Unit, close: () -> Unit, copy: () -> Unit,
    settings: () -> Unit) : ScrollView(context) {
    private val ui = SettingsViews(context)
    private val content = ui.column()
    private val title = ui.label("Ready to dictate", 21f, true)
    private val detail = ui.label("Tap a text field, then Record. Your keyboard stays selected.", 14f)
    private val waveform = VoiceWaveform(context, MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
    private val transcript = ui.label("", 16f).apply { maxLines = 4; isSaveEnabled = false }
    private val primary = ui.button("Record", action = action)
    private val copyButton = ui.button("Copy", true, copy)
    private var hasResult = false

    init {
        addView(content)
        content.setPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
        background = GradientDrawable().apply {
            setColor(MaterialColors.getColor(this@DictationPanel, com.google.android.material.R.attr.colorSurface))
            cornerRadius = context.dp(24).toFloat()
            setStroke(context.dp(1), MaterialColors.getColor(this@DictationPanel, com.google.android.material.R.attr.colorOutlineVariant))
        }
        clipToOutline = true
        elevation = context.dp(12).toFloat()
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.button("Settings", true, settings))
        })
        content.addView(detail)
        content.addView(waveform, LinearLayout.LayoutParams(-1, context.dp(110)))
        waveform.setOnClickListener { action() }
        waveform.contentDescription = "Voice waveform"
        content.addView(transcript)
        content.addView(LinearLayout(context).apply {
            isBaselineAligned = false
            addView(primary, LinearLayout.LayoutParams(0, -2, 1f))
            addView(copyButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(8) })
            addView(ui.button("Close", true, close), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(8) })
        })
        transcript.visibility = View.GONE
        copyButton.visibility = View.GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (resources.displayMetrics.heightPixels - context.dp(140)).coerceAtLeast(context.dp(120))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
    }

    fun ready(model: String) { detail.text = "$model · on device. Tap Record to start." }
    fun state(state: VoiceController.State) {
        val recording = state == VoiceController.State.RECORDING
        val busy = state == VoiceController.State.TRANSCRIBING || state == VoiceController.State.CANCELLING
        waveform.setTranscribing(busy)
        primary.isEnabled = state != VoiceController.State.CANCELLING
        if (recording) {
            hasResult = false
            transcript.text = ""
            transcript.visibility = View.GONE
            copyButton.visibility = View.GONE
            waveform.clear()
            title.text = "Recording · 0s"
            detail.text = "Tap Stop to transcribe. Changing fields cancels recording."
        } else if (busy) title.text = if (state == VoiceController.State.CANCELLING) "Cancelling…" else "Transcribing…"
        else if (!hasResult) title.text = "Ready to dictate"
        primary.text = when {
            recording -> "Stop"
            busy -> "Cancel"
            hasResult -> "Done"
            else -> "Record"
        }
        waveform.contentDescription = when {
            recording -> "Recording waveform. Tap to stop and transcribe"
            busy -> "Transcribing waveform. Tap to cancel"
            else -> "Voice waveform"
        }
    }
    fun progress(value: RecordingProgress) {
        title.text = "Recording · ${value.seconds}s"
        waveform.addSamples(value.waveform)
    }
    fun result(text: String, inserted: Boolean) {
        hasResult = true
        title.text = "Transcribed"
        detail.text = if (inserted) "Text sent to your field. You can also copy it." else "Tap Copy, then paste into your app."
        transcript.text = text
        transcript.visibility = View.VISIBLE
        copyButton.visibility = View.VISIBLE
    }
    fun error(message: String) { title.text = "Ready to dictate"; detail.text = message }
}
