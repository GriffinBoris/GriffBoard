package com.griffinboris.griffboard.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Rect
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.griffinboris.griffboard.models.ModelCatalog
import com.griffinboris.griffboard.BuildConfig

@SuppressLint("SetTextI18n")
class SettingsActivity : AppCompatActivity() {
    private val ui by lazy { SettingsViews(this) }
    private val preferences by lazy { KeyboardPreferences(this) }
    private val cards = mutableListOf<ModelCard>()
    private lateinit var keyboardStatus: TextView
    private lateinit var enableButton: MaterialButton
    private lateinit var chooseButton: MaterialButton
    private lateinit var microphoneButton: MaterialButton
    private lateinit var setupHint: TextView
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        refresh()
        if (!allowed) MaterialAlertDialogBuilder(this)
            .setTitle("Microphone access is off")
            .setMessage("Typing still works. To use voice typing, allow the microphone in Android app settings.")
            .setPositiveButton("App settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
            }
            .setNegativeButton("Not now", null).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = ui.column().apply { setPadding(dp(22), dp(18), dp(22), dp(28)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val root = FrameLayout(this).apply { addView(scroll, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            view.post {
                view.findFocus()?.let { focused ->
                    focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height), true)
                }
            }
            insets
        }
        content.addView(ui.label("GriffBoard", 32f, true))
        content.addView(ui.label("Typing and voice, on your device.", 16f))
        content.addView(ui.section("Keyboard setup"))
        val setup = ui.column()
        keyboardStatus = ui.label("", 16f, true)
        setup.addView(keyboardStatus)
        enableButton = ui.button("Enable GriffBoard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        chooseButton = ui.button("Choose GriffBoard") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        setup.addView(enableButton)
        setup.addView(chooseButton)
        microphoneButton = ui.button("Allow microphone for voice typing", secondary = true) { permission.launch(Manifest.permission.RECORD_AUDIO) }
        setup.addView(microphoneButton)
        content.addView(ui.card(setup))
        setupHint = ui.label("Android shows a standard warning when you enable a keyboard. Your typing and recordings stay on this device.", 13f)
        content.addView(setupHint)
        content.addView(ui.section("Try your keyboard"))
        content.addView(ui.card(EditText(this).apply {
            hint = "Tap here to type or dictate…"
            contentDescription = "Keyboard test field"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 4
            isSaveEnabled = false
        }))
        content.addView(ui.section("Keyboard preferences"))
        val options = ui.column()
        options.addView(toggle("Number row", preferences.numberRow) { preferences.numberRow = it })
        options.addView(toggle("Key vibration", preferences.haptics) { preferences.haptics = it })
        options.addView(toggle("Auto-correct common English typos", preferences.autoCorrect) { preferences.autoCorrect = it })
        val languageNames = arrayOf("Detect language", "English", "Spanish", "French", "German", "Italian", "Portuguese", "Japanese", "Korean", "Chinese")
        val languageCodes = arrayOf("auto", "en", "es", "fr", "de", "it", "pt", "ja", "ko", "zh")
        val language = ui.button("", secondary = true) {}
        fun languageLabel() { language.text = "Voice language · ${languageNames[languageCodes.indexOf(preferences.language)]}" }
        languageLabel()
        language.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Voice language")
                .setSingleChoiceItems(languageNames, languageCodes.indexOf(preferences.language)) { dialog, index ->
                    preferences.language = languageCodes[index]
                    languageLabel()
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        options.addView(language)
        options.addView(ui.label("English-only models always use English. Keyboard appearance follows your system’s light or dark theme.", 13f))
        content.addView(ui.card(options))
        content.addView(ui.section("Voice models"))
        content.addView(ui.label("Download a model, then choose Use model. Base is a good starting point; larger models need more memory and time.", 14f))
        ModelCatalog.models.forEach { model ->
            val card = ModelCard(this, model) { cards.forEach { it.refresh() } }
            cards += card
            content.addView(card.view)
        }
        content.addView(ui.section("Private by default"))
        content.addView(ui.label("Audio stays in memory and is discarded after dictation. There is no account, cloud transcription, typing history, or analytics. Internet access is used only when you download models.", 14f))
        content.addView(ui.label("Tap the microphone to start. The keyboard becomes a live waveform with a timer; tap it to stop and transcribe. There is no fixed recording limit; longer recordings use more memory. Closing the keyboard cancels dictation.", 14f))
        content.addView(ui.label("GriffBoard ${BuildConfig.VERSION_NAME} · whisper.cpp\nModel weights: OpenAI Whisper (MIT)", 12f))
        content.addView(ui.button("Open-source licenses", secondary = true) {
            MaterialAlertDialogBuilder(this).setTitle("Open-source licenses")
                .setMessage(listOf("THIRD_PARTY_NOTICES.md", "ENGLISH_WORDS_LICENSE.txt").joinToString("\n\n") {
                    assets.open(it).bufferedReader().use { reader -> reader.readText() }
                })
                .setPositiveButton("Close", null).show()
        })
    }

    private fun toggle(title: String, value: Boolean, changed: (Boolean) -> Unit) = MaterialSwitch(this).apply {
        text = title
        isChecked = value
        minHeight = dp(56)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnCheckedChangeListener { _, checked -> changed(checked) }
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }
    private fun refresh() {
        if (!::keyboardStatus.isInitialized) return
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        val allowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        keyboardStatus.text = when {
            selected && allowed -> "Ready to type · microphone allowed"
            selected -> "Ready to type · voice needs microphone access"
            enabled -> "Enabled · choose GriffBoard next"
            else -> "Enable GriffBoard to get started"
        }
        enableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        chooseButton.visibility = if (enabled && !selected) View.VISIBLE else View.GONE
        microphoneButton.visibility = if (allowed) View.GONE else View.VISIBLE
        setupHint.visibility = if (enabled) View.GONE else View.VISIBLE
        cards.forEach { it.refresh() }
    }
}
