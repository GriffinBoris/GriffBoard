package com.griffinboris.griffboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import com.griffinboris.griffboard.R
import androidx.core.view.ViewCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowInsetsCompat
import com.griffinboris.griffboard.settings.KeyboardPreferences

@SuppressLint("SetTextI18n", "ViewConstructor") // Created by the IME, never inflated from XML.
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun text(value: String)
        fun backspace()
        fun enter()
        fun microphone()
        fun settings()
        fun switchKeyboard()
    }

    private val preferences = KeyboardPreferences(context)
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val panelColor = (if (dark) "#181A1F" else "#E9EBEF").toColorInt()
    private val keyColor = (if (dark) "#33363E" else "#FFFFFF").toColorInt()
    private val utilityColor = (if (dark) "#272B33" else "#D9DDE5").toColorInt()
    private val textColor = (if (dark) "#F6F7FB" else "#1B202B").toColorInt()
    private val accent = Color.rgb(53, 106, 230)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    private val status = TextView(context)
    private val mic = ImageButton(context)
    private val emojiButton: TextView
    private var shifted = false
    private var capsLock = false
    private var symbols = false
    private var emoji = false
    private var numeric = false
    private var enterLabel = "↵"
    private var repeatKey: Runnable? = null
    private var repeatingView: View? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(panelColor)
        setPadding(dp(4), dp(3), dp(4), dp(36))
        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        toolbar.addView(key("⚙", "Keyboard settings", utility = true) { listener.settings() }, LayoutParams(dp(44), dp(44)))
        emojiButton = key("☺", "Emoji", utility = true) {
            emoji = !emoji
            symbols = false
            renderKeys()
        }
        toolbar.addView(emojiButton, LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) })
        status.apply {
            text = "GriffBoard · on device"
            textSize = 12f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 2
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        toolbar.addView(status, LayoutParams(0, dp(48), 1f))
        mic.apply {
            contentDescription = "Start voice typing"
            setImageResource(R.drawable.ic_microphone)
            background = background(accent)
            setOnClickListener { listener.microphone() }
        }
        toolbar.addView(mic, LayoutParams(dp(52), dp(44)))
        addView(toolbar)
        addView(rows)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            setPadding(dp(4) + bars.left, dp(3), dp(4) + bars.right, maxOf(dp(36), bars.bottom))
            insets
        }
        renderKeys()
    }

    fun editor(numeric: Boolean, enterLabel: String, capitalize: Boolean) {
        this.numeric = numeric
        this.enterLabel = enterLabel
        shifted = capitalize
        capsLock = false
        symbols = false
        emoji = false
        renderKeys()
    }

    fun voiceStatus(message: String, recording: Boolean, busy: Boolean, allowed: Boolean) {
        status.text = message
        mic.setImageResource(if (recording) R.drawable.ic_stop else if (busy) R.drawable.ic_cancel else R.drawable.ic_microphone)
        mic.contentDescription = if (recording) "Stop and transcribe" else if (busy) "Cancel transcription" else "Start voice typing"
        mic.isEnabled = allowed
        mic.alpha = if (allowed) 1f else 0.35f
    }

    private fun renderKeys() {
        stopRepeat()
        rows.removeAllViews()
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
            row("1234567890".map { it.toString() })
            row(listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"))
            row(listOf("_", "=", "/", ":", ";", "\"", "'", "!", "?", "\\"))
        } else {
            if (preferences.numberRow) row("1234567890".map { it.toString() }, small = true)
            row("qwertyuiop".map { letter(it) })
            row("asdfghjkl".map { letter(it) }, inset = 14)
            val third = newRow()
            val shift = addKey(third, if (capsLock) "⇪" else "⇧", 1.4f, true) {
                shifted = !shifted
                capsLock = false
                renderKeys()
            }
            shift.contentDescription = "Shift. Hold for caps lock"
            shift.setOnLongClickListener { capsLock = true; shifted = true; renderKeys(); true }
            "zxcvbnm".forEach { char -> addKey(third, letter(char)) { type(letter(char)) } }
            addDelete(third)
        }
        if (symbols || emoji) {
            val extra = newRow()
            listOf(",", "[", "]", "{", "}", "<", ">", "€", "£").forEach { value -> addKey(extra, value) { type(value) } }
            addDelete(extra)
        }
        val bottom = newRow()
        addKey(bottom, if (symbols || emoji) "ABC" else "!#1", 1.4f, true) {
            symbols = !symbols && !emoji
            emoji = false
            renderKeys()
        }
        addKey(bottom, "!", 0.9f) { listener.text("!") }
        val space = addKey(bottom, "English (US)", 5f) { listener.text(" ") }
        space.textSize = 13f
        space.contentDescription = "Space. Hold to switch keyboard"
        space.setOnLongClickListener { listener.switchKeyboard(); true }
        addKey(bottom, ".", 0.9f) { listener.text(".") }
        addKey(bottom, enterLabel, 1.4f, true) { listener.enter() }.apply { textSize = if (enterLabel == "↵") 24f else 15f }
    }

    private fun letter(char: Char) = if (shifted) char.uppercase() else char.toString()
    private fun type(value: String) {
        listener.text(value)
        if (shifted && !capsLock) { shifted = false; renderKeys() }
    }
    private fun row(values: List<String>, small: Boolean = false, inset: Int = 0) {
        val row = newRow().apply { setPadding(dp(inset), 0, dp(inset), 0) }
        values.forEach { value -> addKey(row, value, small = small) { type(value) } }
    }
    private fun newRow() = LinearLayout(context).apply {
        isBaselineAligned = false
        rows.addView(this, LayoutParams(-1, -2))
    }
    private fun addKey(row: LinearLayout, label: String, weight: Float = 1f, utility: Boolean = false,
        small: Boolean = false, action: () -> Unit): TextView {
        val key = key(label, label, utility, action)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        row.addView(key, LayoutParams(0, dp(if (landscape) 38 else if (small) 38 else 50), weight).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        })
        if (small) { key.textSize = 17f; key.background = background(utilityColor) }
        return key
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun addDelete(row: LinearLayout) {
        val delete = addKey(row, "⌫", 1.4f, true) { listener.backspace() }
        delete.contentDescription = "Delete"
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
        textSize = 22f
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
