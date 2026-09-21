package com.griffinboris.griffboard.voice

import kotlin.math.sqrt

object AudioSignal {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 30
    fun hasSpeech(samples: FloatArray): Boolean {
        if (samples.size < SAMPLE_RATE / 3) return false
        return sqrt(samples.sumOf { (it * it).toDouble() } / samples.size) > 0.002
    }
}
