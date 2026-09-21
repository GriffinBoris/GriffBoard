package com.griffinboris.griffboard.voice

object NativeWhisper {
    init { System.loadLibrary("griffboard") }
    external fun create(): Long
    external fun cancel(handle: Long)
    external fun release(handle: Long)
    external fun transcribe(handle: Long, path: String, samples: FloatArray, language: String, threads: Int): ByteArray
}
