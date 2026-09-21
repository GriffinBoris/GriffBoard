package com.griffinboris.griffboard.keyboard

import java.util.Locale

class WordSuggestions(private val words: List<String>) {
    fun suggest(beforeCursor: String): List<String> {
        val prefix = currentWord(beforeCursor)
        val lower = prefix.lowercase(Locale.ROOT)
        val candidates = if (prefix.isEmpty()) {
            val previous = currentWord(beforeCursor.trimEnd()).lowercase(Locale.ROOT)
            when (previous) {
                "thank" -> listOf("you", "you'll", "you've")
                "how" -> listOf("are", "do", "can")
                "i" -> listOf("am", "have", "will")
                "you", "we", "they" -> listOf("are", "have", "can")
                "he", "she", "it" -> listOf("is", "was", "can")
                "good" -> listOf("morning", "night", "luck")
                "see" -> listOf("you", "the", "what")
                "going" -> listOf("to", "on", "back")
                "want", "need" -> listOf("to", "a", "the")
                "would", "could" -> listOf("be", "you", "have")
                else -> listOf("I", "the", "you")
            }
        } else {
            val completions = words.asSequence().filter { it.startsWith(lower) }.take(3).toList()
            if (completions.size == 3 || lower.length < 3) completions
            else (completions + words.asSequence().filter { it !in completions && oneEditAway(lower, it) }.take(3 - completions.size)).toList()
        }
        val capitalize = prefix.firstOrNull()?.isUpperCase() == true || (prefix.isEmpty() && sentenceStart(beforeCursor))
        return candidates.map {
            when {
                prefix.length > 1 && prefix.all(Char::isUpperCase) -> it.uppercase(Locale.ROOT)
                it == "i" || it.startsWith("i'") -> it.replaceFirstChar(Char::uppercaseChar)
                capitalize -> it.replaceFirstChar(Char::uppercaseChar)
                else -> it
            }
        }
    }

    private fun oneEditAway(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        val first = left.indices.firstOrNull { it >= right.length || left[it] != right[it] } ?: left.length
        if (left.length == right.length) {
            if (left.drop(first + 1) == right.drop(first + 1)) return true
            return first + 1 < left.length && left[first] == right[first + 1] && left[first + 1] == right[first] &&
                left.drop(first + 2) == right.drop(first + 2)
        }
        return if (left.length > right.length) left.drop(first + 1) == right.drop(first)
        else left.drop(first) == right.drop(first + 1)
    }

    companion object {
        fun currentWord(beforeCursor: String) = beforeCursor.takeLastWhile { it.isLetter() || it == '\'' }
        fun sentenceStart(beforeCursor: String): Boolean {
            if (beforeCursor.substringAfterLast('\n').isBlank()) return true
            return beforeCursor.trimEnd().trimEnd('"', '\'', ')', '”', '’').lastOrNull() in listOf('.', '!', '?')
        }
    }
}
