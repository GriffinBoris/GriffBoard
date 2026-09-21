package com.griffinboris.griffboard

import com.griffinboris.griffboard.voice.AudioSignal
import com.griffinboris.griffboard.voice.VoiceSession
import com.griffinboris.griffboard.keyboard.EditorActions
import org.junit.Assert.*
import org.junit.Test

class VoiceSessionTest {
    @Test fun closingOrChangingEditorsRejectsPreviousResult() {
        val session = VoiceSession()
        val token = session.begin()
        assertTrue(session.accepts(token))
        session.invalidate()
        assertFalse(session.accepts(token))
        assertTrue(session.accepts(session.begin()))
    }
    @Test fun startingAnotherRecordingRejectsPreviousResult() {
        val session = VoiceSession()
        val old = session.begin()
        val current = session.begin()
        assertFalse(session.accepts(old))
        assertTrue(session.accepts(current))
    }
    @Test fun rejectsSilenceAndAccidentalMicrophoneTaps() {
        assertFalse(AudioSignal.hasSpeech(FloatArray(16_000)))
        assertFalse(AudioSignal.hasSpeech(FloatArray(100) { 1f }))
        assertTrue(AudioSignal.hasSpeech(FloatArray(16_000) { 0.1f }))
    }
    @Test fun passwordDetectionIncludesVisibleWebAndNumericPasswords() {
        assertTrue(EditorActions.isPassword(0x81))
        assertTrue(EditorActions.isPassword(0x91))
        assertTrue(EditorActions.isPassword(0xE1))
        assertTrue(EditorActions.isPassword(0x12))
        assertFalse(EditorActions.isPassword(0x21))
        assertFalse(EditorActions.isPassword(0x2))
    }
}
