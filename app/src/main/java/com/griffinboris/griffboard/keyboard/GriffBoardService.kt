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
    private var appliedCorrection: AutoCorrect.Applied? = null
    private var pendingCorrection: AutoCorrect.Candidate? = null
    private var dismissedCorrection: AutoCorrect.Candidate? = null
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

    override fun text(value: String) {
        clearMessage()
        val dismissed = dismissedCorrection
        clearCorrection()
        val connection = currentInputConnection ?: return
        if (preferences.autoCorrect && EditorActions.suggestionsAllowed(info.inputType)) {
            appliedCorrection = AutoCorrect.apply(connection, value, dismissed)
        }
        if (appliedCorrection == null) connection.commitText(value, 1)
        keyboard?.correction(appliedCorrection?.original)
        updateTyping()
    }
    override fun backspace() {
        clearMessage()
        if (!restoreCorrection()) currentInputConnection?.let(EditorActions::backspace)
        clearCorrection()
        updateTyping()
    }
    override fun enter() {
        clearMessage()
        val connection = currentInputConnection ?: return
        if (preferences.autoCorrect && EditorActions.suggestionsAllowed(info.inputType)) {
            AutoCorrect.apply(connection, "", dismissedCorrection)
        }
        clearCorrection()
        EditorActions.enter(connection, info)
        updateTyping()
    }

    override fun undoCorrection() { restoreCorrection(); updateTyping() }

    override fun acceptCorrection() {
        val pending = pendingCorrection ?: return
        val connection = currentInputConnection ?: return
        if (!preferences.autoCorrect || !EditorActions.suggestionsAllowed(info.inputType) ||
            AutoCorrect.candidate(connection) != pending) { updateTyping(); return }
        text(" ")
    }

    override fun dismissCorrection() {
        val pending = pendingCorrection ?: return
        val connection = currentInputConnection ?: return
        if (AutoCorrect.candidate(connection) == pending) dismissedCorrection = pending
        updateTyping()
    }

    private fun restoreCorrection(): Boolean {
        val applied = appliedCorrection ?: return false
        clearCorrection()
        if (!EditorActions.suggestionsAllowed(info.inputType)) return false
        return currentInputConnection?.let { AutoCorrect.undo(it, applied) } == true
    }

    private fun clearCorrection() {
        appliedCorrection = null
        pendingCorrection = null
        dismissedCorrection = null
        keyboard?.correction(null)
        keyboard?.previewCorrection(null, null)
    }

    private fun clearMessage() {
        if (message == null) return
        message = null
        updateStatus()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        appliedCorrection?.let { applied ->
            if (newSelStart != applied.cursor || newSelEnd != applied.cursor ||
                currentInputConnection?.let { AutoCorrect.matches(it, applied) } != true) clearCorrection()
        }
        dismissedCorrection?.let {
            if (newSelStart != it.cursor || newSelEnd != it.cursor) dismissedCorrection = null
        }
        updateTyping()
    }

    private fun updateTyping() {
        suggestionJob?.cancel()
        suggestedBefore = null
        pendingCorrection = null
        keyboard?.previewCorrection(null, null)
        if (!EditorActions.prose(info.inputType)) { keyboard?.suggestions(emptyList()); return }
        keyboard?.invalidateSuggestions()
        suggestionJob = scope.launch {
            delay(60)
            val connection = currentInputConnection ?: return@launch
            val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return@launch
            keyboard?.capitalize(WordSuggestions.sentenceStart(before) || connection.getCursorCapsMode(info.inputType) != 0)
            if (!EditorActions.suggestionsAllowed(info.inputType) || !connection.getSelectedText(0).isNullOrEmpty()) {
                keyboard?.suggestions(emptyList())
                return@launch
            }
            pendingCorrection = if (preferences.autoCorrect) AutoCorrect.candidate(connection)?.takeUnless { it == dismissedCorrection } else null
            keyboard?.previewCorrection(pendingCorrection?.original, pendingCorrection?.replacement)
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
        clearCorrection()
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
        clearCorrection()
        message = null
        voiceToken = session.begin()
        voice.start(models.file(model).absolutePath, if (model.englishOnly) "en" else preferences.language)
    }

    override fun settings() {
        clearCorrection()
        cancelVoice()
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun switchKeyboard() { clearCorrection(); getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

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
    private fun clearSuggestions() {
        suggestionJob?.cancel()
        suggestedBefore = null
        keyboard?.suggestions(emptyList())
        clearCorrection()
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onDestroy() {
        cancelVoice()
        scope.cancel()
        keyboard = null
        super.onDestroy()
    }
}
