package com.griffinboris.griffboard.keyboard

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.Locale

object AutoCorrect {
    // Explicit common typos avoid guessing between valid words, names, and completions.
    private val corrections = mapOf(
        "teh" to "the", "adn" to "and", "thsi" to "this", "taht" to "that",
        "wiht" to "with", "wihch" to "which", "whcih" to "which", "hte" to "the",
        "thier" to "their", "becuase" to "because", "becasue" to "because",
        "recieve" to "receive", "recieved" to "received", "recieving" to "receiving",
        "definately" to "definitely", "definitly" to "definitely", "seperate" to "separate",
        "seperately" to "separately", "occured" to "occurred", "untill" to "until",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "wierd" to "weird",
        "alot" to "a lot", "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "couldnt" to "couldn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't",
    )

    data class Applied(val original: String, val replacement: String, val separator: String,
        val cursor: Int, val before: String, val after: String)
    data class Candidate(val original: String, val replacement: String, val cursor: Int,
        val before: String, val after: String)

    fun replacement(before: String): String? {
        val word = WordSuggestions.currentWord(before)
        val prefix = before.dropLast(word.length)
        if (word.isEmpty() || word.all(Char::isUpperCase)) return null
        if (prefix.isNotEmpty() && !prefix.last().isWhitespace() && prefix.last() !in "\"'“‘([") return null
        val lower = word.lowercase(Locale.ROOT)
        val titleCase = word == lower.replaceFirstChar(Char::uppercaseChar)
        if (word != lower && (!titleCase || !WordSuggestions.sentenceStart(prefix))) return null
        val corrected = corrections[lower] ?: return null
        return if (titleCase) corrected.replaceFirstChar(Char::uppercaseChar) else corrected
    }

    fun candidate(connection: InputConnection): Candidate? {
        val before = connection.getTextBeforeCursor(256, 0)?.toString() ?: return null
        val replacement = replacement(before) ?: return null
        val after = connection.getTextAfterCursor(256, 0)?.toString() ?: return null
        if (after.firstOrNull()?.let { it.isLetterOrDigit() || it in "'_" } == true) return null
        val cursor = cursor(connection) ?: return null
        val original = WordSuggestions.currentWord(before)
        return Candidate(original, replacement, cursor, before, after)
    }

    fun apply(connection: InputConnection, separator: String, dismissed: Candidate? = null): Applied? {
        if (separator !in listOf("", " ", ".", ",", "!", "?", ";", ":")) return null
        val candidate = candidate(connection) ?: return null
        if (candidate == dismissed) return null
        val (original, replacement, cursor, before, after) = candidate
        if (!replace(connection, cursor - original.length, cursor, replacement + separator)) return null
        return Applied(original, replacement, separator, cursor - original.length + replacement.length + separator.length,
            (before.dropLast(original.length) + replacement + separator).takeLast(256), after)
    }

    fun matches(connection: InputConnection, applied: Applied): Boolean =
        cursor(connection) == applied.cursor &&
            connection.getTextBeforeCursor(256, 0)?.toString() == applied.before &&
            connection.getTextAfterCursor(256, 0)?.toString() == applied.after

    fun undo(connection: InputConnection, applied: Applied): Boolean {
        if (!matches(connection, applied)) return false
        return replace(connection, applied.cursor - applied.replacement.length - applied.separator.length,
            applied.cursor, applied.original + applied.separator)
    }

    private fun cursor(connection: InputConnection): Int? {
        val extracted = connection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 256 }, 0) ?: return null
        if (extracted.selectionStart < 0 || extracted.selectionStart != extracted.selectionEnd) return null
        return extracted.startOffset + extracted.selectionStart
    }

    private fun replace(connection: InputConnection, start: Int, end: Int, value: String): Boolean {
        connection.beginBatchEdit()
        try {
            if (!connection.setComposingRegion(start, end)) return false
            return connection.commitText(value, 1)
        } finally {
            connection.finishComposingText()
            connection.endBatchEdit()
        }
    }
}
