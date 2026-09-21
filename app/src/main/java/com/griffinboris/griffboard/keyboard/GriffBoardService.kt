package com.griffinboris.griffboard.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GriffBoardService : InputMethodService(), KeyboardView.Listener {
    private val scope = MainScope()
    private val session = VoiceSession()
    private var voiceToken = 0L
    private var keyboard: KeyboardView? = null
    private var info = EditorInfo()
    private var message: String? = null
    private var suggestionJob: Job? = null
    private var suggestedBefore: String? = null
    private val dictionary by lazy {
        scope.async(Dispatchers.IO) {
            WordSuggestions(assets.open("english-frequency.txt").bufferedReader().useLines { lines ->
                lines.map { it.substringBefore(' ') }.filter { word -> word.all { it.isLetter() || it == '\'' } }.toList()
            })
        }
    }
    private val models by lazy { ModelStore(this) }
    private val preferences by lazy { KeyboardPreferences(this) }
    private val voice by lazy {
        VoiceController(scope, ::AudioCapture, WhisperTranscriber(), { updateStatus() }, { text ->
            if (session.accepts(voiceToken) && isInputViewShown) {
                currentInputConnection?.commitText(text, 1)
                updateTyping()
            }
        }, { error ->
            if (session.accepts(voiceToken)) message = error
        }, { progress ->
            if (session.accepts(voiceToken) && isInputViewShown) keyboard?.recordingProgress(progress)
        })
    }

    override fun onCreateInputView(): View = KeyboardView(ContextThemeWrapper(this, R.style.Theme_GriffBoard), this).also {
        window?.window?.let { keyboardWindow ->
            WindowCompat.getInsetsController(keyboardWindow, keyboardWindow.decorView).isAppearanceLightNavigationBars =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        }
        keyboard = it
        configureEditor()
        updateStatus()
        updateTyping()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearSuggestions()
        cancelVoice()
        info = attribute
        message = null
        configureEditor()
        updateStatus()
        updateTyping()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configureEditor()
        updateStatus()
        updateTyping()
    }

    private fun configureEditor() {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME
        val prose = EditorActions.prose(info.inputType)
        val before = if (prose) currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty() else ""
        val capitalize = prose && (WordSuggestions.sentenceStart(before) || (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0)
        keyboard?.editor(numeric, EditorActions.enterLabel(info), capitalize, prose)
    }

    override fun text(value: String) { clearMessage(); currentInputConnection?.commitText(value, 1); updateTyping() }
    override fun backspace() { clearMessage(); currentInputConnection?.let(EditorActions::backspace); updateTyping() }
    override fun enter() { clearMessage(); currentInputConnection?.let { EditorActions.enter(it, info) }; updateTyping() }

    private fun clearMessage() {
        if (message == null) return
        message = null
        updateStatus()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateTyping()
    }

    private fun updateTyping() {
        suggestionJob?.cancel()
        suggestedBefore = null
        if (!EditorActions.prose(info.inputType)) { keyboard?.suggestions(emptyList()); return }
        keyboard?.invalidateSuggestions()
        suggestionJob = scope.launch {
            delay(60)
            val connection = currentInputConnection ?: return@launch
            val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return@launch
            keyboard?.capitalize(WordSuggestions.sentenceStart(before) || connection.getCursorCapsMode(info.inputType) != 0)
            if (!EditorActions.suggestionsAllowed(info.inputType) || !connection.getSelectedText(0).isNullOrEmpty()) return@launch
            val engine = dictionary.await()
            val words = withContext(Dispatchers.Default) { engine.suggest(before) }
            suggestedBefore = before
            keyboard?.suggestions(words)
        }
    }

    override fun suggestion(value: String) {
        if (!EditorActions.suggestionsAllowed(info.inputType)) return
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return
        if (before != suggestedBefore || !connection.getSelectedText(0).isNullOrEmpty()) { updateTyping(); return }
        EditorActions.insertSuggestion(connection, before, value)
        clearMessage()
        updateTyping()
    }
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
            state == VoiceController.State.RECORDING -> "Recording · ${voice.progress.seconds}s"
            state == VoiceController.State.TRANSCRIBING -> "Transcribing on device…"
            state == VoiceController.State.CANCELLING -> "Cancelling…"
            message != null -> message!!
            models.selected() == null -> "Tap the mic to set up voice typing"
            else -> "${models.selected()!!.label} · on device"
        }
        keyboard?.voiceStatus(label, state == VoiceController.State.RECORDING,
            state == VoiceController.State.TRANSCRIBING || state == VoiceController.State.CANCELLING, !password,
            priority = message != null)
    }
    private fun cancelVoice() { session.invalidate(); voice.cancel() }
    override fun onFinishInputView(finishingInput: Boolean) {
        clearSuggestions()
        cancelVoice()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() { clearSuggestions(); cancelVoice(); super.onFinishInput() }
    override fun onWindowHidden() { clearSuggestions(); cancelVoice(); super.onWindowHidden() }
    private fun clearSuggestions() { suggestionJob?.cancel(); suggestedBefore = null; keyboard?.suggestions(emptyList()) }
    override fun onEvaluateFullscreenMode() = false
    override fun onDestroy() {
        cancelVoice()
        scope.cancel()
        keyboard = null
        super.onDestroy()
    }
}
