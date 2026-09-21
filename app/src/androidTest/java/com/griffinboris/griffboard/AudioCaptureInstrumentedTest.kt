package com.griffinboris.griffboard

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.settings.SettingsActivity
import com.griffinboris.griffboard.voice.AudioCapture
import com.griffinboris.griffboard.voice.AudioSignal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioCaptureInstrumentedTest {
    @Test fun recordingPassesThirtySecondsAndReleasesMicrophoneAfterStopAndCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
        ActivityScenario.launch(SettingsActivity::class.java).use {
            runBlocking {
                withTimeout(50_000) {
                    val recorder = AudioCapture()
                    var elapsed = 0
                    val audio = recorder.record { progress ->
                        assertTrue(progress.seconds >= elapsed)
                        assertTrue(progress.waveform.all { it in -1f..1f })
                        elapsed = progress.seconds
                        if (elapsed >= 31) recorder.stop()
                    }
                    assertTrue(audio.size >= 31 * AudioSignal.SAMPLE_RATE)
                    val started = CompletableDeferred<Unit>()
                    val cancelled = async {
                        AudioCapture().record { if (it.seconds >= 1) started.complete(Unit) }
                    }
                    started.await()
                    cancelled.cancelAndJoin()
                    val retry = AudioCapture()
                    assertTrue(retry.record { if (it.seconds >= 1) retry.stop() }.isNotEmpty())
                }
            }
        }
    }
}
