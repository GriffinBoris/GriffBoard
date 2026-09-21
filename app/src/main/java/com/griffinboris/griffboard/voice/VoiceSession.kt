package com.griffinboris.griffboard.voice

/** A result is only allowed back into the editor session that started recording. */
class VoiceSession {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun invalidate() { generation++ }
    fun accepts(token: Long) = generation == token
}
