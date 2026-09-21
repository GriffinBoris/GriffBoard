package com.griffinboris.griffboard.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperTranscriber : VoiceTranscriber {
    private var handle = 0L

    override suspend fun transcribe(path: String, audio: FloatArray, language: String): String {
        check(handle == 0L) { "Transcription is already active." }
        handle = NativeWhisper.create()
        try {
            val result = withContext(Dispatchers.IO) {
                NativeWhisper.transcribe(handle, path, audio, language,
                    Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            }
            return result.toString(Charsets.UTF_8)
        } finally {
            NativeWhisper.release(handle)
            handle = 0L
        }
    }
    override fun cancel() { if (handle != 0L) NativeWhisper.cancel(handle) }
}
