package com.griffinboris.griffboard.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.griffinboris.griffboard.R
import com.griffinboris.griffboard.models.ModelStore
import com.griffinboris.griffboard.settings.KeyboardPreferences
import com.griffinboris.griffboard.settings.SettingsActivity
import com.griffinboris.griffboard.voice.AudioCapture
import com.griffinboris.griffboard.voice.VoiceController
import com.griffinboris.griffboard.voice.VoiceSession
import com.griffinboris.griffboard.voice.WhisperTranscriber
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class GriffBoardService : InputMethodService(), KeyboardView.Listener {
    private val scope = MainScope()
    private val session = VoiceSession()
    private var voiceToken = 0L
    private var keyboard: KeyboardView? = null
    private var info = EditorInfo()
    private var message: String? = null
    private val models by lazy { ModelStore(this) }
    private val preferences by lazy { KeyboardPreferences(this) }
    private val voice by lazy {
        VoiceController(scope, ::AudioCapture, WhisperTranscriber(), { updateStatus() }, { text ->
            if (session.accepts(voiceToken) && isInputViewShown) {
                currentInputConnection?.commitText(text, 1)
                message = "Inserted · tap the mic to dictate again"
            }
        }, { error ->
            if (session.accepts(voiceToken)) message = error
        })
    }

    override fun onCreateInputView(): View = KeyboardView(ContextThemeWrapper(this, R.style.Theme_GriffBoard), this).also {
        keyboard = it
        configureEditor()
        updateStatus()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        cancelVoice()
        info = attribute
        message = null
        configureEditor()
        updateStatus()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configureEditor()
        updateStatus()
    }

    private fun configureEditor() {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME
        val capitalize = !EditorActions.isPassword(info.inputType) &&
            (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        keyboard?.editor(numeric, EditorActions.enterLabel(info), capitalize)
    }

    override fun text(value: String) { currentInputConnection?.commitText(value, 1) }
    override fun backspace() { currentInputConnection?.let(EditorActions::backspace) }
    override fun enter() { currentInputConnection?.let { EditorActions.enter(it, info) } }
    override fun microphone() {
        when (voice.state) {
            VoiceController.State.RECORDING -> { voice.stopRecording(); return }
            VoiceController.State.TRANSCRIBING -> { cancelVoice(); return }
            VoiceController.State.CANCELLING -> return
            VoiceController.State.IDLE -> Unit
        }
        if (EditorActions.isPassword(info.inputType)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            settings()
            return
        }
        val model = models.selected()
        if (model == null) { settings(); return }
        message = null
        voiceToken = session.begin()
        voice.start(models.file(model).absolutePath, if (model.englishOnly) "en" else preferences.language)
    }

    override fun settings() {
        cancelVoice()
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun switchKeyboard() { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

    private fun updateStatus() {
        val state = voice.state
        val password = EditorActions.isPassword(info.inputType)
        val label = when {
            password -> "Password field · voice off"
            state == VoiceController.State.RECORDING -> "Listening · tap ■ to finish · 30s max"
            state == VoiceController.State.TRANSCRIBING -> "Transcribing on device…"
            state == VoiceController.State.CANCELLING -> "Cancelling…"
            message != null -> message!!
            models.selected() == null -> "Tap the mic to set up voice typing"
            else -> "${models.selected()!!.label} · on device"
        }
        keyboard?.voiceStatus(label, state == VoiceController.State.RECORDING,
            state == VoiceController.State.TRANSCRIBING || state == VoiceController.State.CANCELLING, !password)
    }
    private fun cancelVoice() { session.invalidate(); voice.cancel() }
    override fun onFinishInputView(finishingInput: Boolean) {
        cancelVoice()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() { cancelVoice(); super.onFinishInput() }
    override fun onWindowHidden() { cancelVoice(); super.onWindowHidden() }
    override fun onEvaluateFullscreenMode() = false
    override fun onDestroy() {
        cancelVoice()
        scope.cancel()
        keyboard = null
        super.onDestroy()
    }
}
