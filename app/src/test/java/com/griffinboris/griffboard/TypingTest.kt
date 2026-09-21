package com.griffinboris.griffboard

import android.text.InputType
import com.griffinboris.griffboard.keyboard.EditorActions
import com.griffinboris.griffboard.keyboard.WordSuggestions
import com.griffinboris.griffboard.voice.AudioSignal
import org.junit.Assert.*
import org.junit.Test

class TypingTest {
    @Test fun waveformPreservesSignedPeaksAndPartialBuckets() {
        val samples = ShortArray(105)
        samples[12] = Short.MIN_VALUE
        samples[70] = 16384
        samples[103] = 8192
        assertArrayEquals(floatArrayOf(-1f, 0.5f, 0f, 0.25f), AudioSignal.waveform(samples, 105), 0f)
        assertArrayEquals(floatArrayOf(), AudioSignal.waveform(samples, 0), 0f)
    }
    @Test fun sentenceCapitalizationHandlesPunctuationQuotesAndNewlines() {
        listOf("", "hello.", "hello! ", "what? ", "he said \"hi.\" ", "hello\n  ").forEach {
            assertTrue(it, WordSuggestions.sentenceStart(it))
        }
        listOf("hello", "hello, ", "3.14", "hello; ").forEach { assertFalse(it, WordSuggestions.sentenceStart(it)) }
    }
    @Test fun suggestionsCompleteCorrectAndRespectCapitalization() {
        val suggestions = WordSuggestions(listOf("hello", "help", "held", "the", "world", "word"))
        assertEquals(listOf("Hello", "Help", "Held"), suggestions.suggest("Hel"))
        assertEquals(listOf("HELLO", "HELP", "HELD"), suggestions.suggest("HEL"))
        assertTrue(suggestions.suggest("teh").contains("the"))
        assertTrue(suggestions.suggest("worls").contains("world"))
        assertEquals(listOf("you", "you'll", "you've"), suggestions.suggest("Thank "))
        assertEquals("can't", WordSuggestions.currentWord("I can't"))
    }
    @Test fun suggestionsAndCapitalizationExcludeSensitiveAndStructuredFields() {
        val text = InputType.TYPE_CLASS_TEXT
        listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI).forEach {
            assertFalse(EditorActions.suggestionsAllowed(text or it))
            assertFalse(EditorActions.prose(text or it))
        }
        assertFalse(EditorActions.suggestionsAllowed(InputType.TYPE_CLASS_NUMBER))
        assertFalse(EditorActions.suggestionsAllowed(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertTrue(EditorActions.suggestionsAllowed(text))
    }
}
