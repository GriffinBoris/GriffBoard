package com.griffinboris.griffboard.voice

import kotlin.math.sqrt

object AudioSignal {
    const val SAMPLE_RATE = 16_000
    fun waveform(samples: ShortArray, count: Int): FloatArray {
        val bucketSize = 100
        val envelope = FloatArray(((count + bucketSize - 1) / bucketSize) * 2)
        for (bucket in 0 until envelope.size / 2) {
            val start = bucket * bucketSize
            var minimum = samples[start]
            var maximum = minimum
            for (index in start until minOf(start + bucketSize, count)) {
                minimum = minOf(minimum, samples[index])
                maximum = maxOf(maximum, samples[index])
            }
            envelope[bucket * 2] = minimum / 32768f
            envelope[bucket * 2 + 1] = maximum / 32768f
        }
        return envelope
    }
    fun hasSpeech(samples: FloatArray): Boolean {
        if (samples.size < SAMPLE_RATE / 3) return false
        return sqrt(samples.sumOf { (it * it).toDouble() } / samples.size) > 0.002
    }
}
