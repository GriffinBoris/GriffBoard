package com.griffinboris.griffboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.keyboard.KeyboardView
import com.griffinboris.griffboard.keyboard.VoiceWaveform
import com.griffinboris.griffboard.settings.SettingsActivity
import com.griffinboris.griffboard.voice.RecordingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceFeedbackInstrumentedTest {
    @Test fun transcriptionPulsesTheRecordedBarsAndRecordingRestoresTheirHeight() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var waveform: VoiceWaveform
            scenario.onActivity { activity ->
                waveform = VoiceWaveform(activity, android.graphics.Color.BLUE)
                activity.setContentView(waveform)
                waveform.addSamples(FloatArray(512) { if (it % 2 == 0) -0.2f else 0.2f })
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            lateinit var original: Bitmap
            scenario.onActivity {
                original = Bitmap.createBitmap(waveform.width, waveform.height, Bitmap.Config.ARGB_8888)
                waveform.draw(Canvas(original))
                waveform.setTranscribing(true)
            }
            Thread.sleep(350)
            scenario.onActivity {
                val pulsing = Bitmap.createBitmap(waveform.width, waveform.height, Bitmap.Config.ARGB_8888)
                waveform.draw(Canvas(pulsing))
                assertTrue("Transcription must visibly animate the recorded waveform", !original.sameAs(pulsing))
                waveform.setTranscribing(false)
                val restored = Bitmap.createBitmap(waveform.width, waveform.height, Bitmap.Config.ARGB_8888)
                waveform.draw(Canvas(restored))
                assertTrue("Leaving transcription must restore the captured waveform", original.sameAs(restored))
                original.recycle()
                pulsing.recycle()
                restored.recycle()
            }
        }
    }

    @Test fun elapsedTimeAndWaveformsKeepStopAndCancelAccessible() {
        var microphoneTaps = 0
        var undoTaps = 0
        var acceptTaps = 0
        var dismissTaps = 0
        val listener = object : KeyboardView.Listener {
            override fun text(value: String) = Unit
            override fun backspace() = Unit
            override fun enter() = Unit
            override fun microphone() { microphoneTaps++ }
            override fun settings() = Unit
            override fun switchKeyboard() = Unit
            override fun undoCorrection() { undoTaps++ }
            override fun acceptCorrection() { acceptTaps++ }
            override fun dismissCorrection() { dismissTaps++ }
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var keyboard: KeyboardView
            scenario.onActivity { activity ->
                keyboard = KeyboardView(activity, listener)
                activity.setContentView(FrameLayout(activity).apply {
                    addView(keyboard, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                })
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val idleHeight = keyboard.height
            scenario.onActivity {
                keyboard.voiceStatus("Recording · 0s", true, false, true)
                repeat(256) { keyboard.recordingProgress(RecordingProgress(73, floatArrayOf(-(it % 8) / 25f, (it % 6) / 25f))) }
            }
            onView(withText("Recording · 73s")).check(matches(isDisplayed()))
            assertEquals("Recording must keep the keyboard height", idleHeight, keyboard.height)
            onView(withContentDescription("Recording waveform. Tap to stop and transcribe")).perform(click())
            assertEquals(1, microphoneTaps)
            saveScreenshot("voice-recording-preview.png")
            scenario.onActivity { keyboard.voiceStatus("Transcribing on device…", false, true, true) }
            onView(withText("Transcribing on device…")).check(matches(isDisplayed()))
            onView(withContentDescription("Recorded waveform. Tap to cancel transcription")).perform(click())
            assertEquals(2, microphoneTaps)
            saveScreenshot("voice-transcribing-preview.png")
            scenario.onActivity { keyboard.voiceStatus("Ready", false, false, true) }
            onView(withContentDescription("Start voice typing")).check(matches(isDisplayed()))
            scenario.onActivity {
                keyboard.suggestions(listOf("hello", "help", "held"))
                keyboard.voiceStatus("No speech detected. Try again.", false, false, true, priority = true)
            }
            onView(allOf(withText("No speech detected. Try again."), isDisplayed())).check(matches(isDisplayed()))
            onView(withText("hello")).check(matches(withEffectiveVisibility(Visibility.GONE)))
            scenario.onActivity { keyboard.voiceStatus("Ready", false, false, true) }
            onView(withText("hello")).check(matches(isDisplayed()))
            scenario.onActivity { keyboard.correction("teh") }
            onView(withContentDescription("Undo correction. Restore teh")).perform(click())
            assertEquals(1, undoTaps)
            scenario.onActivity { keyboard.correction(null) }
            onView(withText("hello")).check(matches(isDisplayed()))
            scenario.onActivity { keyboard.previewCorrection("teh", "the") }
            onView(withText("the")).check(matches(isDisplayed())).perform(click())
            assertEquals(1, acceptTaps)
            onView(withContentDescription("Keep teh. Dismiss correction")).perform(click())
            assertEquals(1, dismissTaps)
            assertEquals(1, undoTaps)
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
