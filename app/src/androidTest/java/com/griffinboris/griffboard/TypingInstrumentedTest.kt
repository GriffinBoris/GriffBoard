package com.griffinboris.griffboard

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.keyboard.EditorActions
import com.griffinboris.griffboard.keyboard.KeyboardView
import com.griffinboris.griffboard.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun overlappingTouchesSurviveAutomaticShiftAndPunctuationCapitalizes() {
        val text = StringBuilder()
        val listener = object : KeyboardView.Listener {
            override fun text(value: String) { text.append(value) }
            override fun backspace() = Unit
            override fun enter() = Unit
            override fun microphone() = Unit
            override fun settings() = Unit
            override fun switchKeyboard() = Unit
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var keyboard: KeyboardView
            scenario.onActivity {
                keyboard = KeyboardView(it, listener)
                it.setContentView(keyboard)
                keyboard.editor(false, "↵", true)
            }
            instrumentation.waitForIdleSync()
            val first = position(keyboard, "A")
            val second = position(keyboard, "B")
            val down = SystemClock.uptimeMillis()
            touch(keyboard, down, MotionEvent.ACTION_DOWN, listOf(0 to first))
            touch(keyboard, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(0 to first, 1 to second))
            touch(keyboard, down, MotionEvent.ACTION_POINTER_UP, listOf(0 to first, 1 to second))
            touch(keyboard, down, MotionEvent.ACTION_UP, listOf(1 to second))
            assertEquals("Ab", text.toString())
            for (label in listOf(".", "Space. Hold to switch keyboard", "C", "d", "!", "E")) {
                tap(keyboard, label)
            }
            assertEquals("Ab. Cd!E", text.toString())
        }
    }

    @Test fun shiftTypesOneUppercaseLetterThenLocksOnSecondTapUntilToggledOff() {
        val text = StringBuilder()
        val listener = object : KeyboardView.Listener {
            override fun text(value: String) { text.append(value) }
            override fun backspace() { if (text.isNotEmpty()) text.deleteCharAt(text.lastIndex) }
            override fun enter() = Unit
            override fun microphone() = Unit
            override fun settings() = Unit
            override fun switchKeyboard() = Unit
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var keyboard: KeyboardView
            scenario.onActivity {
                keyboard = KeyboardView(it, listener)
                it.setContentView(keyboard)
                keyboard.editor(false, "↵", false)
            }
            instrumentation.waitForIdleSync()
            tap(keyboard, "Shift. Tap for uppercase")
            tap(keyboard, "A")
            tap(keyboard, "b")
            assertEquals("Ab", text.toString())

            tap(keyboard, "Shift. Tap for uppercase")
            tap(keyboard, "Uppercase on. Tap for caps lock")
            tap(keyboard, "C")
            tap(keyboard, ".")
            tap(keyboard, "Space. Hold to switch keyboard")
            scenario.onActivity { keyboard.capitalize(false) }
            tap(keyboard, "D")
            assertEquals("AbC. D", text.toString())

            tap(keyboard, "Caps lock on. Tap for lowercase")
            scenario.onActivity { keyboard.capitalize(true) }
            tap(keyboard, "e")
            tap(keyboard, "Delete")
            tap(keyboard, "f")
            assertEquals("AbC. Df", text.toString())

            scenario.onActivity { keyboard.editor(false, "↵", true) }
            tap(keyboard, "Uppercase on. Tap for caps lock")
            tap(keyboard, "G")
            tap(keyboard, "H")
            assertEquals("AbC. DfGH", text.toString())
            scenario.onActivity { keyboard.editor(false, "↵", false) }
            tap(keyboard, "i")
            assertEquals("AbC. DfGHi", text.toString())
        }
    }

    @Test fun suggestionReplacesWholeWordAtCursorAndPreservesSurroundingText() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = object : BaseInputConnection(editor, true) {
                override fun getEditable() = editor.text
            }
            editor.setText("Say helo, world")
            editor.setSelection(6)
            EditorActions.insertSuggestion(connection, "Say he", "hello")
            assertEquals("Say hello, world", editor.text.toString())
            editor.setText("hel")
            editor.setSelection(3)
            EditorActions.insertSuggestion(connection, "hel", "hello")
            assertEquals("hello ", editor.text.toString())
        }
    }

    private fun tap(keyboard: KeyboardView, description: String) {
        val point = position(keyboard, description)
        val start = SystemClock.uptimeMillis()
        touch(keyboard, start, MotionEvent.ACTION_DOWN, listOf(0 to point))
        touch(keyboard, start, MotionEvent.ACTION_UP, listOf(0 to point))
    }

    private fun position(keyboard: KeyboardView, description: String): Pair<Float, Float> {
        var point = 0f to 0f
        instrumentation.runOnMainSync {
            fun find(view: View): View? {
                if (view.contentDescription == description) return view
                return (view as? ViewGroup)?.children?.firstNotNullOfOrNull { find(it) }
            }
            val key = requireNotNull(find(keyboard)) { description }
            val rect = Rect()
            key.getDrawingRect(rect)
            keyboard.offsetDescendantRectToMyCoords(key, rect)
            point = rect.exactCenterX() to rect.exactCenterY()
        }
        return point
    }

    private fun touch(keyboard: KeyboardView, down: Long, action: Int, pointers: List<Pair<Int, Pair<Float, Float>>>) {
        instrumentation.runOnMainSync {
            val properties = pointers.map { MotionEvent.PointerProperties().apply { id = it.first; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coords = pointers.map { MotionEvent.PointerCoords().apply { x = it.second.first; y = it.second.second; pressure = 1f; size = 1f } }.toTypedArray()
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, pointers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { keyboard.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }
}
