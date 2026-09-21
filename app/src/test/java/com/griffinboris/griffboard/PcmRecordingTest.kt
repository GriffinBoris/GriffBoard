package com.griffinboris.griffboard

import com.griffinboris.griffboard.voice.AudioSignal
import com.griffinboris.griffboard.voice.PcmRecording
import org.junit.Assert.*
import org.junit.Test

class PcmRecordingTest {
    @Test fun retainsAudioPastThirtySecondsIncludingTheFinalPartialRead() {
        val recording = PcmRecording()
        val buffer = ShortArray(1600) { 16384 }
        repeat(650) { recording.append(buffer, buffer.size) }
        buffer[0] = Short.MIN_VALUE
        recording.append(buffer, 1)
        buffer.fill(0)
        val audio = recording.toFloatArray()
        assertEquals(AudioSignal.SAMPLE_RATE * 65 + 1, recording.sampleCount)
        assertEquals(recording.sampleCount, audio.size)
        assertEquals(0.5f, audio.first(), 0f)
        assertEquals(0.5f, audio[audio.lastIndex - 1], 0f)
        assertEquals(-1f, audio.last(), 0f)
    }
}
