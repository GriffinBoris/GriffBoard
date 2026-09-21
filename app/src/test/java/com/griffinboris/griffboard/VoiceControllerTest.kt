package com.griffinboris.griffboard

import com.griffinboris.griffboard.voice.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceControllerTest {
    private class Recorder : VoiceRecorder {
        val audio = CompletableDeferred<FloatArray>()
        var stopped = false
        var released = false
        override suspend fun record(): FloatArray = try { audio.await() } finally { released = true }
        override fun stop() { stopped = true }
    }
    private class Transcriber : VoiceTranscriber {
        val result = CompletableDeferred<String>()
        var cancelled = false
        override suspend fun transcribe(path: String, audio: FloatArray, language: String) = result.await()
        override fun cancel() { cancelled = true }
    }
    private val speech = FloatArray(16_000) { 0.2f }

    @Test fun stopTranscribesAndReleasesRecording() = runTest {
        val recorder = Recorder()
        val transcriber = Transcriber()
        val results = mutableListOf<String>()
        val controller = VoiceController(this, { recorder }, transcriber, {}, results::add, { fail(it) })
        assertTrue(controller.start("model", "en"))
        assertFalse(controller.start("model", "en"))
        controller.stopRecording()
        assertTrue(recorder.stopped)
        recorder.audio.complete(speech)
        runCurrent()
        assertEquals(VoiceController.State.TRANSCRIBING, controller.state)
        transcriber.result.complete(" Hello. ")
        advanceUntilIdle()
        assertEquals(listOf("Hello."), results)
        assertTrue(recorder.released)
        assertEquals(VoiceController.State.IDLE, controller.state)
    }
    @Test fun cancelImmediatelyCleansUpAndAllowsRetry() = runTest {
        val recorders = mutableListOf<Recorder>()
        val controller = VoiceController(this, { Recorder().also(recorders::add) }, Transcriber(), {}, { fail("Late result") }, { fail(it) })
        controller.start("model", "en")
        controller.cancel()
        advanceUntilIdle()
        assertTrue(recorders.first().released)
        assertTrue(recorders.first().stopped)
        assertEquals(VoiceController.State.IDLE, controller.state)
        assertTrue(controller.start("model", "en"))
        controller.cancel()
        advanceUntilIdle()
    }
    @Test fun cancelInferenceNeverInsertsLateText() = runTest {
        val recorder = Recorder()
        val transcriber = Transcriber()
        val controller = VoiceController(this, { recorder }, transcriber, {}, { fail("Late result") }, { fail(it) })
        controller.start("model", "en")
        recorder.audio.complete(speech)
        runCurrent()
        controller.cancel()
        transcriber.result.complete("Should not be inserted")
        advanceUntilIdle()
        assertTrue(transcriber.cancelled)
        assertEquals(VoiceController.State.IDLE, controller.state)
    }
    @Test fun cannotStartAnotherRecordingUntilCancellationReleasesTheMicrophone() = runTest {
        val released = CompletableDeferred<Unit>()
        val recorder = object : VoiceRecorder {
            override suspend fun record(): FloatArray {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { released.await() } }
            }
            override fun stop() = Unit
        }
        val controller = VoiceController(this, { recorder }, Transcriber(), {}, { fail("Late result") }, { fail(it) })
        controller.start("model", "en")
        controller.cancel()
        runCurrent()
        assertFalse(controller.start("model", "en"))
        released.complete(Unit)
        advanceUntilIdle()
        assertEquals(VoiceController.State.IDLE, controller.state)
    }
    @Test fun failedMicrophoneStartIsVisibleAndRetryable() = runTest {
        val recorder = Recorder()
        recorder.audio.completeExceptionally(IllegalStateException("Microphone is busy"))
        val errors = mutableListOf<String>()
        val controller = VoiceController(this, { recorder }, Transcriber(), {}, { fail("Unexpected text") }, errors::add)
        assertTrue(controller.start("model", "en"))
        advanceUntilIdle()
        assertEquals(listOf("Microphone is busy"), errors)
        assertTrue(recorder.released)
        assertEquals(VoiceController.State.IDLE, controller.state)
        assertTrue(controller.start("model", "en"))
    }
    @Test fun silenceNeverReachesTranscription() = runTest {
        val recorder = Recorder()
        recorder.audio.complete(FloatArray(16_000))
        val errors = mutableListOf<String>()
        val controller = VoiceController(this, { recorder }, Transcriber(), {}, { fail("Unexpected text") }, errors::add)
        controller.start("model", "en")
        advanceUntilIdle()
        assertEquals(1, errors.size)
        assertEquals(VoiceController.State.IDLE, controller.state)
    }
}
