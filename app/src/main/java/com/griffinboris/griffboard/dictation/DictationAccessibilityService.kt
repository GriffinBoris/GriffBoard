package com.griffinboris.griffboard.dictation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.griffinboris.griffboard.R
import com.griffinboris.griffboard.keyboard.EditorActions
import com.griffinboris.griffboard.models.ModelStore
import com.griffinboris.griffboard.settings.KeyboardPreferences
import com.griffinboris.griffboard.settings.SettingsActivity
import com.griffinboris.griffboard.settings.dp
import com.griffinboris.griffboard.voice.AudioCapture
import com.griffinboris.griffboard.voice.VoiceController
import com.griffinboris.griffboard.voice.VoiceSession
import com.griffinboris.griffboard.voice.WhisperTranscriber
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlin.math.abs
import java.lang.ref.WeakReference

@RequiresApi(33)
class DictationAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private val session = VoiceSession()
    private var token = 0L
    private var target: InputMethod.AccessibilityInputConnection? = null
    private var sessionPackage: String? = null
    private var lastPackage: String? = null
    private var resultText: String? = null
    private var panel: DictationPanel? = null
    private var bubble: ImageButton? = null
    private var foreground = false
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private val idleClose: Runnable = Runnable { if (voice.state == VoiceController.State.IDLE) closePanel() }
    private val voice: VoiceController by lazy {
        VoiceController(scope, ::AudioCapture, WhisperTranscriber(), { state ->
            panel?.state(state)
            if (state == VoiceController.State.IDLE) {
                if (panel == null) stopRecordingService() else main.postDelayed(idleClose, 120_000)
            }
        }, { text ->
            if (session.accepts(token) && panel != null) {
                val connection = target
                connection?.commitText(text, 1, null)
                resultText = text
                panel?.result(text, connection != null)
            }
        }, { error -> panel?.error(error) }, { progress -> panel?.progress(progress) })
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) { closePanel(); removeBubble(); return }
            refreshFloatingButton()
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, RECEIVER_NOT_EXPORTED)
    }

    override fun onServiceConnected() {
        activeReference = WeakReference(this)
        DictationShortcuts.sync(this)
    }

    override fun onCreateInputMethod() = object : InputMethod(this) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) { editorChanged() }
        override fun onFinishInput() { editorChanged() }
        override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
            newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
            if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) editorChanged()
        }
    }

    private fun editorChanged() {
        session.invalidate()
        target = null
        if (voice.state != VoiceController.State.IDLE) {
            voice.cancel()
            panel?.error("The text field changed. Tap Record to start again.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        lastPackage = packageName
        if (voice.state != VoiceController.State.IDLE && sessionPackage != packageName) editorChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "close") { closePanel(); return START_NOT_STICKY }
        if (intent?.action != "open") { stopSelf(); return START_NOT_STICKY }
        val ready = intent.getParcelableExtra("ready", ResultReceiver::class.java)
        try {
            startForeground(DictationShortcuts.RECORDING_NOTIFICATION, DictationShortcuts.recordingNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            foreground = true
            if (active === this && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) showPanel()
            else stopRecordingService()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Allow microphone access in GriffBoard settings.", Toast.LENGTH_LONG).show()
            stopRecordingService()
        } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
            Toast.makeText(this, "Voice could not start. Unlock your phone and try the shortcut again.", Toast.LENGTH_LONG).show()
            stopRecordingService()
        } finally { ready?.send(0, null) }
        return START_NOT_STICKY
    }

    private fun showPanel() {
        if (panel != null) return
        removeBubble()
        val context = ContextThemeWrapper(this, R.style.Theme_GriffBoard)
        val view = DictationPanel(context, ::primaryAction, ::closePanel, {
            resultText?.let {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Dictation", it))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
        }, {
            closePanel()
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        panel = view
        ModelStore(this).selected()?.let { view.ready(it.label) }
        windows.addView(view, layout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(400)), -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        })
        main.postDelayed(idleClose, 120_000)
    }

    private fun primaryAction() {
        when (voice.state) {
            VoiceController.State.RECORDING -> { voice.stopRecording(); return }
            VoiceController.State.TRANSCRIBING -> { voice.cancel(); return }
            VoiceController.State.CANCELLING -> return
            VoiceController.State.IDLE -> Unit
        }
        if (resultText != null) { closePanel(); return }
        val editor = inputMethod?.currentInputEditorInfo
        if (editor != null && EditorActions.isPassword(editor.inputType)) {
            panel?.error("Voice input is off in password fields."); return
        }
        val models = ModelStore(this)
        val model = models.selected()
        if (model == null) { panel?.error("Choose a downloaded voice model in Settings first."); return }
        main.removeCallbacks(idleClose)
        token = session.begin()
        target = inputMethod?.currentInputConnection
        sessionPackage = editor?.packageName ?: lastPackage
        voice.start(models.file(model).absolutePath, if (model.englishOnly) "en" else KeyboardPreferences(this).language)
    }

    private fun layout(width: Int, height: Int) = WindowManager.LayoutParams(width, height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT)

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded") // Drag coordinates use physical screen pixels.
    fun refreshFloatingButton() {
        val show = active === this && DictationPreferences(this).floating && panel == null &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!show) { removeBubble(); return }
        if (bubble != null) return
        val button = ImageButton(this).apply {
            contentDescription = "Open GriffBoard Voice. Drag to move."
            setImageResource(R.drawable.ic_microphone)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF356AE6.toInt()) }
            setOnClickListener { startActivity(DictationShortcuts.launchIntent(this@DictationAccessibilityService, "floating")) }
        }
        val position = layout(dp(52), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = resources.displayMetrics.widthPixels - dp(68)
            y = resources.displayMetrics.heightPixels / 3
        }
        var startX = 0f
        var startY = 0f
        var originX = 0
        var originY = 0
        var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    originX = position.x; originY = position.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop) moved = true
                    if (moved) {
                        position.x = (originX + event.rawX - startX).toInt().coerceIn(0, resources.displayMetrics.widthPixels - dp(52))
                        position.y = (originY + event.rawY - startY).toInt().coerceIn(0, resources.displayMetrics.heightPixels - dp(100))
                        windows.updateViewLayout(view, position)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) view.performClick()
            }
            true
        }
        bubble = button
        windows.addView(button, position)
    }

    private fun removeBubble() { bubble?.let(windows::removeView); bubble = null }
    private fun closePanel() {
        main.removeCallbacks(idleClose)
        session.invalidate()
        target = null
        resultText = null
        voice.cancel()
        panel?.let(windows::removeView)
        panel = null
        if (voice.state == VoiceController.State.IDLE) stopRecordingService()
        refreshFloatingButton()
    }
    private fun stopRecordingService() {
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }
    override fun onInterrupt() { closePanel() }
    override fun onUnbind(intent: Intent?): Boolean {
        activeReference.clear()
        closePanel()
        removeBubble()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        activeReference.clear()
        main.removeCallbacksAndMessages(null)
        closePanel()
        removeBubble()
        unregisterReceiver(screen)
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private var activeReference = WeakReference<DictationAccessibilityService>(null)
        val active: DictationAccessibilityService? get() = activeReference.get()
    }
}
