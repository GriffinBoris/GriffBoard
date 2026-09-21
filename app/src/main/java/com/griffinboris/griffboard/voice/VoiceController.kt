package com.griffinboris.griffboard.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

interface VoiceRecorder {
    suspend fun record(): FloatArray
    fun stop()
}

interface VoiceTranscriber {
    suspend fun transcribe(path: String, audio: FloatArray, language: String): String
    fun cancel()
}

class VoiceController(
    private val scope: CoroutineScope,
    private val recorderFactory: () -> VoiceRecorder,
    private val transcriber: VoiceTranscriber,
    private val onState: (State) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    enum class State { IDLE, RECORDING, TRANSCRIBING, CANCELLING }
    var state = State.IDLE
        private set
    private var job: Job? = null
    private var recorder: VoiceRecorder? = null

    fun start(path: String, language: String): Boolean {
        if (job?.isCompleted == false) return false
        val capture = recorderFactory()
        recorder = capture
        update(State.RECORDING)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val audio = capture.record()
                if (!AudioSignal.hasSpeech(audio)) {
                    onError("No speech heard. Try again closer to the microphone.")
                    return@launch
                }
                update(State.TRANSCRIBING)
                val text = transcriber.transcribe(path, audio, language).trim()
                if (text.isEmpty()) onError("No speech recognized. Please try again.")
                else onResult(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                onError(error.message ?: "Microphone disconnected. Please try again.")
            } catch (error: IllegalStateException) {
                onError(error.message ?: "Voice typing is unavailable. Please try again.")
            } catch (_: SecurityException) {
                onError("Allow microphone access in GriffBoard settings.")
            } finally {
                capture.stop()
                recorder = null
                job = null
                update(State.IDLE)
            }
        }
        return true
    }

    fun stopRecording() { recorder?.stop() }
    fun cancel() {
        if (job?.isCompleted != false) return
        update(State.CANCELLING)
        recorder?.stop()
        transcriber.cancel()
        job?.cancel()
    }
    private fun update(value: State) { state = value; onState(value) }
}
