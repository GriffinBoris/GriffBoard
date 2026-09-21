package com.griffinboris.griffboard

import android.text.InputType
import com.griffinboris.griffboard.keyboard.EditorActions
import com.griffinboris.griffboard.keyboard.AutoCorrect
import com.griffinboris.griffboard.keyboard.WordSuggestions
import com.griffinboris.griffboard.voice.AudioSignal
import org.junit.Assert.*
import org.junit.Test

class TypingTest {
    @Test fun autoCorrectOnlyAcceptsKnownTyposAndPreservesSentenceCase() {
        assertEquals("the", AutoCorrect.replacement("say teh"))
        assertEquals("The", AutoCorrect.replacement("Teh"))
        assertEquals("Because", AutoCorrect.replacement("Hi. Becuase"))
        assertEquals("don't", AutoCorrect.replacement("I dont"))
        assertEquals("a lot", AutoCorrect.replacement("thanks alot"))
        listOf("form", "from", "hel", "Griffin", "TEH", "tEh", "Say Teh", "foo.teh", "foo_teh", "a@teh", "1teh").forEach {
            assertNull(it, AutoCorrect.replacement(it))
        }
    }
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
        val suggestions = WordSuggestions(mapOf("hello" to 6L, "help" to 5L, "held" to 4L,
            "the" to 3L, "world" to 2L, "word" to 1L))
        assertEquals(listOf("Hello", "Help", "Held"), suggestions.suggest("Hel"))
        assertEquals(listOf("HELLO", "HELP", "HELD"), suggestions.suggest("HEL"))
        assertTrue(suggestions.suggest("teh").contains("the"))
        assertTrue(suggestions.suggest("worls").contains("world"))
        assertEquals(listOf("you", "you'll", "you've"), suggestions.suggest("Thank "))
        assertEquals("can't", WordSuggestions.currentWord("I can't"))
    }
    @Test fun dictionaryCorrectionHandlesSingleEditsAndPreservesCase() {
        val dictionary = WordSuggestions(mapOf("world" to 10_000L, "word" to 500L, "words" to 1_000L))
        listOf("wrold", "worl", "woorld", "worls").forEach {
            assertEquals(it, "world", AutoCorrect.replacement(it, dictionary))
            assertEquals("world", dictionary.suggest(it).first())
        }
        assertEquals("World", AutoCorrect.replacement("Wrold", dictionary))
        listOf("world", "word", "words", "WROLD", "wRold", "say Wrold", "a@wrold", "foo_wrold").forEach {
            assertNull(it, AutoCorrect.replacement(it, dictionary))
        }
    }
    @Test fun dictionaryCorrectionLeavesAmbiguousRareAndShortWordsAlone() {
        val dictionary = WordSuggestions(mapOf("hello" to 400_000L, "help" to 600_000L,
            "hell" to 300_000L, "zebra" to 500L))
        listOf("hellp", "zbera", "hel", "zzzzzz", "can't", "gríffin").forEach {
            assertNull(it, AutoCorrect.replacement(it, dictionary))
        }
        assertEquals(3, dictionary.suggest("hellp").size)
        assertEquals("the", AutoCorrect.replacement("teh", dictionary))
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
