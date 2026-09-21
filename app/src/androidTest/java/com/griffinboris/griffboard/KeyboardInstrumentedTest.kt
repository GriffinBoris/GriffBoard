package com.griffinboris.griffboard

import android.view.ContextThemeWrapper
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.keyboard.EditorActions
import com.griffinboris.griffboard.voice.NativeWhisper
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class KeyboardInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun backspaceDeletesOneUnicodeCodePointAndSelection() {
        instrumentation.runOnMainSync {
            val editor = EditText(ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_GriffBoard))
            val connection = object : BaseInputConnection(editor, true) {
                override fun getEditable() = editor.text
            }
            editor.setText("A😀")
            editor.setSelection(editor.length())
            EditorActions.backspace(connection)
            assertEquals("A", editor.text.toString())
            editor.setText("keep remove")
            editor.setSelection(5, 11)
            EditorActions.backspace(connection)
            assertEquals("keep ", editor.text.toString())
        }
    }

    @Test fun enterUsesEditorActionAndMultilineOverrides() {
        val info = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEND }
        assertEquals("Send", EditorActions.enterLabel(info))
        info.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals("↵", EditorActions.enterLabel(info))
    }

    @Test fun nativeRuntimeCanCancelBeforeModelLoading() {
        val handle = NativeWhisper.create()
        try {
            NativeWhisper.cancel(handle)
            assertTrue(NativeWhisper.transcribe(handle, "/missing-model", FloatArray(16_000), "en", 2).isEmpty())
        } finally { NativeWhisper.release(handle) }
    }

    @Test fun nativeRuntimeRejectsMissingModelWithoutCrashing() {
        val handle = NativeWhisper.create()
        try {
            assertThrows(IllegalStateException::class.java) {
                NativeWhisper.transcribe(handle, "/missing-model", FloatArray(16_000), "en", 2)
            }
        } finally { NativeWhisper.release(handle) }
    }

    @Test fun transcribesKnownSpeechWithDownloadedTinyModel() {
        val model = File(instrumentation.targetContext.noBackupFilesDir, "models/ggml-tiny.en-q5_1.bin")
        assumeTrue("Install Tiny English before running the optional inference test", model.isFile)
        val wav = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        while (true) {
            val tag = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val length = buffer.int
            if (tag == "data") {
                val audio = FloatArray(length / 2) { buffer.short / 32768f }
                val handle = NativeWhisper.create()
                try {
                    val text = NativeWhisper.transcribe(handle, model.absolutePath, audio, "en", 4).toString(Charsets.UTF_8).lowercase()
                    assertTrue("Expected JFK speech in transcript", text.contains("ask not") && text.contains("country"))
                } finally { NativeWhisper.release(handle) }
                return
            }
            buffer.position(buffer.position() + length + length % 2)
        }
    }
}
