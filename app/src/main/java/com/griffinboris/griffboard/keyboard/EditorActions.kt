package com.griffinboris.griffboard.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

object EditorActions {
    fun prose(type: Int): Boolean {
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT || isPassword(type)) return false
        return type and InputType.TYPE_MASK_VARIATION !in setOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
    }

    fun suggestionsAllowed(type: Int) = prose(type) && type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0

    fun isPassword(type: Int): Boolean {
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun enterLabel(info: EditorInfo): String = when (action(info)) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "↵"
    }

    private fun action(info: EditorInfo): Int = if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
        EditorInfo.IME_ACTION_NONE
    } else info.imeOptions and EditorInfo.IME_MASK_ACTION

    fun enter(connection: InputConnection, info: EditorInfo) {
        val action = action(info)
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action)
        } else connection.commitText("\n", 1)
    }

    fun backspace(connection: InputConnection) {
        if (!connection.getSelectedText(0).isNullOrEmpty()) connection.commitText("", 1)
        else if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    fun insertSuggestion(connection: InputConnection, before: String, value: String) {
        val after = connection.getTextAfterCursor(256, 0)?.toString().orEmpty()
        val suffix = after.takeWhile { it.isLetter() || it == '\'' }
        val remaining = after.drop(suffix.length)
        val space = if (remaining.firstOrNull()?.isWhitespace() == true || remaining.firstOrNull() in listOf('.', ',', '!', '?', ';', ':')) "" else " "
        connection.beginBatchEdit()
        try {
            connection.deleteSurroundingText(WordSuggestions.currentWord(before).length, suffix.length)
            connection.commitText(value + space, 1)
        } finally { connection.endBatchEdit() }
    }
}
