package com.griffinboris.griffboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.griffinboris.griffboard.voice.RecordingProgress

@SuppressLint("ViewConstructor", "SetTextI18n")
class RecordingOverlay(context: Context, textColor: Int, accent: Int, action: () -> Unit) : LinearLayout(context) {
    private val title = TextView(context).apply { textSize = 22f; setTextColor(textColor); gravity = Gravity.CENTER }
    private val hint = TextView(context).apply { textSize = 14f; setTextColor(textColor); gravity = Gravity.CENTER }
    private val waveform = VoiceWaveform(context, accent)
    private var recording = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        addView(title, LayoutParams(-1, -2))
        addView(waveform, LayoutParams(-1, 0, 1f).apply { setMargins(0, padding, 0, padding) })
        addView(hint, LayoutParams(-1, -2))
        isFocusable = true
        setOnClickListener { action() }
    }

    fun show(message: String, isRecording: Boolean, busy: Boolean) {
        if (isRecording && !recording) waveform.clear()
        recording = isRecording
        visibility = if (isRecording || busy) View.VISIBLE else View.GONE
        title.text = message
        hint.text = if (isRecording) "Tap to stop and transcribe" else "Tap to cancel"
        contentDescription = if (isRecording) "Recording waveform. Tap to stop and transcribe" else "Recorded waveform. Tap to cancel transcription"
        waveform.setTranscribing(busy)
        if (!isRecording && !busy) waveform.clear()
    }

    fun progress(value: RecordingProgress) {
        title.text = "Recording · ${value.seconds}s"
        waveform.addSamples(value.waveform)
    }
}
