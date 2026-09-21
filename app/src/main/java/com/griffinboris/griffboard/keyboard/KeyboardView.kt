package com.griffinboris.griffboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.FrameLayout
import com.griffinboris.griffboard.R
import androidx.core.view.ViewCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import com.griffinboris.griffboard.settings.KeyboardPreferences
import com.griffinboris.griffboard.voice.RecordingProgress

@SuppressLint("SetTextI18n", "ViewConstructor") // Created by the IME, never inflated from XML.
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun text(value: String)
        fun backspace()
        fun enter()
        fun microphone()
        fun settings()
        fun switchKeyboard()
        fun suggestion(value: String) {}
        fun undoCorrection() {}
        fun acceptCorrection() {}
        fun dismissCorrection() {}
    }

    private val preferences = KeyboardPreferences(context)
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val panelColor = (if (dark) "#181A1F" else "#E9EBEF").toColorInt()
    private val keyColor = (if (dark) "#33363E" else "#FFFFFF").toColorInt()
    private val utilityColor = (if (dark) "#272B33" else "#D9DDE5").toColorInt()
    private val textColor = (if (dark) "#F6F7FB" else "#1B202B").toColorInt()
    private val accent = (if (dark) "#A9C4FF" else "#356AE6").toColorInt()
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val status = TextView(context)
    private val suggestions = LinearLayout(context).apply { isBaselineAligned = false }
    private var correctedWord: String? = null
    private var correctionPreview: Pair<String, String>? = null
    private val correctionLabel = key("", "Correction", false) {
        if (correctedWord != null) listener.undoCorrection() else listener.acceptCorrection()
    }.apply {
        textSize = 14f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(4), 0)
        setBackgroundColor(Color.TRANSPARENT)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val correctionDismiss = ImageButton(context).apply {
        setImageResource(R.drawable.ic_cancel)
        imageTintList = ColorStateList.valueOf(textColor)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(4), dp(6), dp(4), dp(6))
        background = background(Color.TRANSPARENT)
        setOnClickListener {
            if (correctedWord != null) listener.undoCorrection() else listener.dismissCorrection()
        }
    }
    private val correctionChip = LinearLayout(context).apply {
        isBaselineAligned = false
        background = InsetDrawable(background(utilityColor), dp(2), dp(6), dp(2), dp(6))
        addView(correctionLabel, LayoutParams(0, -1, 1f))
        addView(correctionDismiss, LayoutParams(dp(44), -1))
    }
    private val recordingOverlay = RecordingOverlay(context, textColor, accent) { listener.microphone() }
    private val letterKeys = mutableListOf<Pair<TextView, Char>>()
    private var shiftKey: ImageButton? = null
    private var manualShift = false
    private var autoCapitalization = true
    private var voiceActive = false
    private var statusPriority = false
    private val mic = ImageButton(context)
    private val emojiButton: TextView
    private var shifted = false
    private var capsLock = false
    private var symbols = false
    private var symbolPage = 0
    private var emoji = false
    private var numeric = false
    private var enterLabel = "↵"
    private var repeatKey: Runnable? = null
    private var repeatingView: View? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(panelColor)
        setPadding(dp(4), dp(3), dp(4), dp(48))
        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        toolbar.addView(key("⚙", "Keyboard settings", utility = true) { listener.settings() }.apply {
            textSize = 19f
            background = InsetDrawable(background, dp(2), dp(6), dp(2), dp(6))
        }, LayoutParams(dp(40), dp(48)))
        emojiButton = key("☺︎", "Emoji", utility = true) {
            emoji = !emoji
            symbols = false
            renderKeys()
        }
        emojiButton.textSize = 19f
        emojiButton.background = InsetDrawable(emojiButton.background, dp(2), dp(6), dp(2), dp(6))
        toolbar.addView(emojiButton, LayoutParams(dp(40), dp(48)))
        status.apply {
            text = "GriffBoard · on device"
            textSize = 12f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 2
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val voiceDisplay = FrameLayout(context).apply {
            addView(status, FrameLayout.LayoutParams(-1, -1))
            addView(suggestions, FrameLayout.LayoutParams(-1, -1))
            addView(correctionChip, FrameLayout.LayoutParams(-1, -1))
        }
        correctionChip.visibility = View.GONE
        suggestions.visibility = View.GONE
        toolbar.addView(voiceDisplay, LayoutParams(0, dp(48), 1f))
        mic.apply {
            contentDescription = "Start voice typing"
            setImageResource(R.drawable.ic_microphone)
            background = InsetDrawable(background(Color.rgb(53, 106, 230)), dp(2), dp(6), dp(2), dp(6))
            setOnClickListener { listener.microphone() }
        }
        toolbar.addView(mic, LayoutParams(dp(44), dp(48)))
        addView(toolbar)
        addView(FrameLayout(context).apply {
            addView(rows, FrameLayout.LayoutParams(-1, -2))
            addView(recordingOverlay, FrameLayout.LayoutParams(-1, 0))
        })
        rows.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = bottom - top
            if (recordingOverlay.layoutParams.height != height) {
                recordingOverlay.layoutParams = FrameLayout.LayoutParams(-1, height)
            }
        }
        recordingOverlay.visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            setPadding(dp(4) + bars.left, dp(3), dp(4) + bars.right, maxOf(dp(36), bars.bottom) + dp(12))
            insets
        }
        renderKeys()
    }

    fun editor(numeric: Boolean, enterLabel: String, capitalize: Boolean, autoCapitalization: Boolean = true) {
        this.numeric = numeric
        this.enterLabel = enterLabel
        shifted = capitalize
        this.autoCapitalization = autoCapitalization
        manualShift = false
        capsLock = false
        symbols = false
        symbolPage = 0
        emoji = false
        renderKeys()
    }

    fun voiceStatus(message: String, recording: Boolean, busy: Boolean, allowed: Boolean, priority: Boolean = false) {
        voiceActive = recording || busy
        statusPriority = priority
        status.text = if (voiceActive) "Voice typing · on device" else message
        status.accessibilityLiveRegion = if (recording) View.ACCESSIBILITY_LIVE_REGION_NONE else View.ACCESSIBILITY_LIVE_REGION_POLITE
        recordingOverlay.show(message, recording, busy)
        rows.visibility = if (voiceActive) View.INVISIBLE else View.VISIBLE
        rows.importantForAccessibility = if (voiceActive) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        emojiButton.isEnabled = !numeric && !voiceActive
        emojiButton.alpha = if (emojiButton.isEnabled) 1f else 0.35f
        showSuggestionStrip()
        mic.setImageResource(if (recording) R.drawable.ic_stop else if (busy) R.drawable.ic_cancel else R.drawable.ic_microphone)
        mic.contentDescription = if (recording) "Stop and transcribe" else if (busy) "Cancel transcription" else "Start voice typing"
        mic.isEnabled = allowed
        mic.alpha = if (allowed) 1f else 0.35f
    }

    fun recordingProgress(progress: RecordingProgress) {
        recordingOverlay.progress(progress)
    }

    fun suggestions(values: List<String>) {
        suggestions.removeAllViews()
        values.forEach { value ->
            suggestions.addView(key(value, "Suggest $value", false) { listener.suggestion(value) }.apply {
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setBackgroundColor(Color.TRANSPARENT)
            }, LayoutParams(0, -1, 1f))
        }
        showSuggestionStrip()
    }

    fun invalidateSuggestions() {
        previewCorrection(null, null)
        for (index in 0 until suggestions.childCount) suggestions.getChildAt(index).isEnabled = false
    }

    fun correction(original: String?) {
        correctedWord = original
        showSuggestionStrip()
    }

    fun previewCorrection(original: String?, replacement: String?) {
        correctionPreview = if (original != null && replacement != null) original to replacement else null
        showSuggestionStrip()
    }

    private fun showSuggestionStrip() {
        val chip = !voiceActive && !statusPriority && (correctedWord != null || correctionPreview != null)
        val show = !voiceActive && !statusPriority && !chip && suggestions.isNotEmpty()
        if (correctedWord != null) {
            correctionLabel.text = "Undo “$correctedWord”"
            correctionLabel.contentDescription = "Undo correction. Restore $correctedWord"
            correctionDismiss.contentDescription = "Undo correction"
        } else correctionPreview?.let { (original, replacement) ->
            correctionLabel.text = replacement
            correctionLabel.contentDescription = "Accept $replacement. Also accepted on Space, punctuation, or Enter"
            correctionDismiss.contentDescription = "Keep $original. Dismiss correction"
        }
        correctionChip.visibility = if (chip) View.VISIBLE else View.GONE
        suggestions.visibility = if (show) View.VISIBLE else View.GONE
        status.visibility = if (show || chip) View.GONE else View.VISIBLE
    }

    fun capitalize(value: Boolean) {
        if (capsLock || manualShift) return
        shifted = value
        refreshLetters()
    }

    private fun refreshLetters() {
        letterKeys.forEach { (key, char) -> key.text = letter(char); key.contentDescription = letter(char) }
        shiftKey?.apply {
            setImageResource(when { capsLock -> R.drawable.ic_caps_lock; shifted -> R.drawable.ic_shift_on; else -> R.drawable.ic_shift })
            imageTintList = ColorStateList.valueOf(if (shifted) accent else textColor)
            isSelected = shifted
            contentDescription = when {
                capsLock -> "Caps lock on. Tap for lowercase"
                shifted -> "Uppercase on. Tap for caps lock"
                else -> "Shift. Tap for uppercase"
            }
        }
    }

    private fun renderKeys() {
        stopRepeat()
        rows.removeAllViews()
        letterKeys.clear()
        shiftKey = null
        emojiButton.isEnabled = !numeric
        emojiButton.alpha = if (numeric) 0.35f else 1f
        emojiButton.contentDescription = if (emoji) "Return to letters" else "Emoji"
        if (numeric) {
            listOf("123", "456", "789").forEach { values -> row(values.map { it.toString() }) }
            row(listOf("+", "-", "*", "#", ":", "/"))
            val bottom = newRow()
            addKey(bottom, ".") { listener.text(".") }
            addKey(bottom, "0") { listener.text("0") }
            addDelete(bottom)
            addKey(bottom, enterLabel, utility = true) { listener.enter() }
            return
        }
        if (emoji) {
            listOf(listOf("😀", "😂", "🥰", "😎", "🤔", "😭"), listOf("👍", "🙏", "❤️", "🎉", "🔥", "✨")).forEach { row(it) }
        } else if (symbols) {
            renderSymbols()
        } else {
            if (preferences.numberRow) row("1234567890".map { it.toString() })
            letterRow("qwertyuiop")
            letterRow("asdfghjkl", 14)
            val third = newRow()
            shiftKey = addIconKey(third, R.drawable.ic_shift, "Shift. Tap for uppercase") {
                when {
                    capsLock -> { capsLock = false; shifted = false }
                    shifted -> capsLock = true
                    else -> shifted = true
                }
                manualShift = true
                refreshLetters()
            }
            "zxcvbnm".forEach { char -> addLetter(third, char) }
            addDelete(third)
        }
        if (emoji) {
            val extra = newRow()
            listOf(",", "[", "]", "{", "}", "<", ">", "€", "£").forEach { value -> addKey(extra, value) { type(value) } }
            addDelete(extra)
        }
        val bottom = newRow()
        addKey(bottom, if (symbols || emoji) "ABC" else "!#1", 1.4f, true) {
            symbols = !symbols && !emoji
            symbolPage = 0
            emoji = false
            renderKeys()
        }.contentDescription = if (symbols || emoji) "Return to letters" else "Show symbols"
        addKey(bottom, "!", 0.9f) { type("!") }
        val space = addKey(bottom, "English (US)", 5f) { type(" ") }
        space.textSize = 13f
        space.contentDescription = "Space. Hold to switch keyboard"
        space.setOnLongClickListener { listener.switchKeyboard(); true }
        addKey(bottom, ".", 0.9f) { type(".") }
        addKey(bottom, enterLabel, 1.4f, true) { listener.enter() }.apply {
            textSize = if (enterLabel == "↵") 24f else 15f
            contentDescription = if (enterLabel == "↵") "Enter" else enterLabel
        }
        refreshLetters()
    }

    private fun renderSymbols() {
        row("1234567890".map { it.toString() })
        if (symbolPage == 0) {
            row(listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]"))
            row(listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"))
        } else {
            row(listOf("`", "~", "\\", "|", "{", "}", "€", "£", "¥", "₩"))
            row(listOf("°", "•", "○", "●", "□", "■", "♤", "♡", "◇", "♧"))
        }
        val third = newRow()
        addKey(third, "${symbolPage + 1}/2", 1.4f, true) {
            symbolPage = 1 - symbolPage
            renderKeys()
        }.apply {
            textSize = 19f
            contentDescription = "Symbols page ${symbolPage + 1} of 2. Show page ${2 - symbolPage}"
        }
        val values = if (symbolPage == 0) listOf("-", "'", "\"", ":", ";", ",", "?")
        else listOf("☆", "▪", "¤", "《", "》", "¡", "¿")
        values.forEach { value -> addKey(third, value) { type(value) } }
        addDelete(third)
    }

    private fun letter(char: Char) = if (shifted) char.uppercase() else char.toString()
    private fun type(value: String) {
        listener.text(value)
        if (!capsLock) {
            if (value.any(Char::isLetterOrDigit)) { shifted = false; manualShift = false }
            else if (autoCapitalization && value in listOf(".", "!", "?", "\n")) { shifted = true; manualShift = false }
            refreshLetters()
        }
    }
    private fun addLetter(row: LinearLayout, char: Char) {
        val key = addKey(row, letter(char)) { type(letter(char)) }
        letterKeys.add(key to char)
    }
    private fun letterRow(letters: String, inset: Int = 0) {
        val row = newRow().apply { setPadding(dp(inset), 0, dp(inset), 0) }
        letters.forEach { addLetter(row, it) }
    }
    private fun row(values: List<String>, inset: Int = 0) {
        val row = newRow().apply { setPadding(dp(inset), 0, dp(inset), 0) }
        values.forEach { value -> addKey(row, value) { type(value) } }
    }
    private fun newRow() = LinearLayout(context).apply {
        isBaselineAligned = false
        isMotionEventSplittingEnabled = true
        rows.addView(this, LayoutParams(-1, -2))
    }
    private fun addKey(row: LinearLayout, label: String, weight: Float = 1f, utility: Boolean = false,
        action: () -> Unit): TextView {
        val key = key(label, label, utility, action)
        row.addView(key, LayoutParams(0, keyHeight(), weight))
        // Keep the visual gutters inside each key's touch target.
        key.background = InsetDrawable(key.background, dp(2))
        return key
    }
    private fun keyHeight(): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return dp(if (landscape) 40 else 50)
    }
    private fun addIconKey(row: LinearLayout, icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(textColor)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(0, 0, 0, 0)
        contentDescription = description
        background = InsetDrawable(background(utilityColor), dp(2))
        setOnClickListener {
            if (preferences.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
        row.addView(this, LayoutParams(0, keyHeight(), 1.4f))
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun addDelete(row: LinearLayout) {
        val delete = addIconKey(row, R.drawable.ic_backspace, "Delete") { listener.backspace() }
        delete.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopRepeat()
                    val repeat = object : Runnable {
                        override fun run() { listener.backspace(); view.postDelayed(this, 65) }
                    }
                    repeatKey = repeat
                    repeatingView = view
                    view.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopRepeat()
            }
            false
        }
    }
    private fun stopRepeat() {
        repeatKey?.let { repeatingView?.removeCallbacks(it) }
        repeatKey = null
        repeatingView = null
    }
    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }
    private fun key(label: String, description: String, utility: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label
        contentDescription = description
        textSize = if (label.singleOrNull()?.isDigit() == true) 20f else 21f
        gravity = Gravity.CENTER
        setTextColor(textColor)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        background = background(if (utility) utilityColor else keyColor)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (preferences.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
    }
    private fun background(color: Int) = RippleDrawable(ColorStateList.valueOf(0x22356AE6),
        GradientDrawable().apply { setColor(color); cornerRadius = dp(7).toFloat() }, null)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
