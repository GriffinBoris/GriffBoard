package com.griffinboris.griffboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.keyboard.AutoCorrect
import com.griffinboris.griffboard.keyboard.WordSuggestions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCorrectInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun bundledDictionaryCorrectsUnknownWordsAndHonorsDismissalAndUndo() {
        val dictionary = WordSuggestions(instrumentation.targetContext.assets.open("english-frequency.txt")
            .bufferedReader().useLines { lines ->
                lines.associate { it.substringBefore(' ') to it.substringAfter(' ').toLong() }
            })
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            for ((typo, correction) in listOf("keyboarf" to "keyboard", "speling" to "spelling", "typign" to "typing")) {
                editor.setText(typo)
                editor.setSelection(editor.length())
                val candidate = requireNotNull(AutoCorrect.candidate(connection, dictionary))
                assertEquals(correction, candidate.replacement)
                assertNull(AutoCorrect.apply(connection, " ", candidate, dictionary))
                assertEquals(typo, editor.text.toString())
                val applied = requireNotNull(AutoCorrect.apply(connection, " ", dictionary = dictionary))
                assertEquals("$correction ", editor.text.toString())
                assertTrue(AutoCorrect.undo(connection, applied))
                assertEquals("$typo ", editor.text.toString())
            }
            listOf("world", "hellp").forEach { word ->
                editor.setText(word)
                editor.setSelection(editor.length())
                assertNull(AutoCorrect.candidate(connection, dictionary))
            }
        }
    }

    @Test fun dismissedCandidateIsKeptButDoesNotSuppressTheNextOccurrence() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            editor.setText("teh")
            editor.setSelection(3)
            val dismissed = requireNotNull(AutoCorrect.candidate(connection))
            assertNull(AutoCorrect.apply(connection, " ", dismissed))
            assertEquals("teh", editor.text.toString())
            connection.commitText(" teh", 1)
            assertNotNull(AutoCorrect.apply(connection, " ", dismissed))
            assertEquals("teh the ", editor.text.toString())
        }
    }

    @Test fun correctionCanFinishAWordBeforeEnterWithoutAddingAnExtraSpace() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            editor.setText("teh")
            editor.setSelection(3)
            assertNotNull(AutoCorrect.apply(connection, ""))
            com.griffinboris.griffboard.keyboard.EditorActions.enter(connection, EditorInfo())
            assertEquals("the\n", editor.text.toString())
        }
    }

    @Test fun correctionAndUndoPreservePunctuationAndSurroundingText() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            editor.setText("Say teh next")
            editor.setSelection(7)
            val applied = requireNotNull(AutoCorrect.apply(connection, ","))
            assertEquals("Say the, next", editor.text.toString())
            assertTrue(AutoCorrect.undo(connection, applied))
            assertEquals("Say teh, next", editor.text.toString())

            editor.setText("alot")
            editor.setSelection(4)
            val expanded = requireNotNull(AutoCorrect.apply(connection, " "))
            assertEquals("a lot ", editor.text.toString())
            assertTrue(AutoCorrect.undo(connection, expanded))
            assertEquals("alot ", editor.text.toString())
        }
    }

    @Test fun correctionSkipsSelectionsPartialWordsAndOrdinaryKeystrokes() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            editor.setText("tehory")
            editor.setSelection(3)
            assertNull(AutoCorrect.apply(connection, " "))
            assertEquals("tehory", editor.text.toString())
            editor.setText("teh")
            editor.setSelection(0, 3)
            assertNull(AutoCorrect.apply(connection, " "))
            editor.setSelection(3)
            assertNull(AutoCorrect.apply(connection, "x"))
            assertEquals("teh", editor.text.toString())
        }
    }

    @Test fun undoRejectsCursorMovesAndChangesToSurroundingText() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            val connection = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            editor.setText("say teh")
            editor.setSelection(7)
            val applied = requireNotNull(AutoCorrect.apply(connection, " "))
            editor.setSelection(4)
            assertFalse(AutoCorrect.undo(connection, applied))
            editor.setText("new the ")
            editor.setSelection(8)
            assertFalse(AutoCorrect.undo(connection, applied))
            assertEquals("new the ", editor.text.toString())
        }
    }

    @Test fun correctionUsesAbsoluteCursorWhenEditorReturnsPartialExtractedText() {
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext)
            editor.setText("Please say teh")
            editor.setSelection(editor.length())
            val connection = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
                override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) =
                    super.getExtractedText(request, flags)?.apply {
                        startOffset = 7
                        selectionStart -= 7
                        selectionEnd -= 7
                        text = text.subSequence(7, text.length)
                    }
            }
            val applied = requireNotNull(AutoCorrect.apply(connection, " "))
            assertEquals("Please say the ", editor.text.toString())
            assertTrue(AutoCorrect.undo(connection, applied))
            assertEquals("Please say teh ", editor.text.toString())
        }
    }
}
