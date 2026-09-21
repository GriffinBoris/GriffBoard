package com.griffinboris.griffboard.voice

class PcmRecording {
    private val chunks = mutableListOf<ShortArray>()
    var sampleCount = 0
        private set

    fun append(buffer: ShortArray, count: Int) {
        chunks.add(buffer.copyOf(count))
        sampleCount += count
    }

    fun toFloatArray(): FloatArray {
        val audio = FloatArray(sampleCount)
        var offset = 0
        for (chunk in chunks) {
            for (sample in chunk) audio[offset++] = sample / 32768f
        }
        return audio
    }
}
