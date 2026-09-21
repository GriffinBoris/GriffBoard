package com.griffinboris.griffboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AudioCapture : VoiceRecorder {
    @Volatile private var stopped = false

    @SuppressLint("MissingPermission")
    override suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        if (stopped) return@withContext FloatArray(0)
        val bufferSize = AudioRecord.getMinBufferSize(AudioSignal.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(bufferSize > 0) { "This microphone does not support 16 kHz audio." }
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, AudioSignal.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(bufferSize, 8192))
        try {
            check(input.state == AudioRecord.STATE_INITIALIZED) { "Microphone is unavailable." }
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start." }
            val samples = FloatArray(AudioSignal.SAMPLE_RATE * AudioSignal.MAX_SECONDS)
            val buffer = ShortArray(1600)
            var total = 0
            while (!stopped && total < samples.size) {
                val count = input.read(buffer, 0, minOf(buffer.size, samples.size - total))
                if (count < 0) throw IOException("Microphone disconnected. Please try again.")
                for (index in 0 until count) samples[total++] = buffer[index] / 32768f
            }
            samples.copyOf(total)
        } finally {
            try {
                if (input.recordingState == AudioRecord.RECORDSTATE_RECORDING) input.stop()
            } finally { input.release() }
        }
    }
    override fun stop() { stopped = true }
}
