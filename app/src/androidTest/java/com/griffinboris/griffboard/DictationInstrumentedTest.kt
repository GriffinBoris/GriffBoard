package com.griffinboris.griffboard

import android.Manifest
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.griffinboris.griffboard.dictation.DictationAccessibilityService
import com.griffinboris.griffboard.dictation.DictationPreferences
import com.griffinboris.griffboard.dictation.DictationShortcuts
import com.griffinboris.griffboard.models.ModelStore
import com.griffinboris.griffboard.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DictationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    @Test fun shortcutsDefaultOffAndCanBeEnabledIndependently() {
        val preferences = DictationPreferences(context)
        val saved = listOf(preferences.floating, preferences.tile, preferences.notification, preferences.button)
        try {
            context.getSharedPreferences("dictation", 0).edit().clear().commit()
            assertFalse(preferences.floating || preferences.tile || preferences.notification || preferences.button)
            assertFalse(preferences.allows(null))
            assertFalse(preferences.allows("unknown"))
            preferences.button = true
            DictationShortcuts.sync(context)
            assertTrue(preferences.allows("button"))
            assertFalse(preferences.allows("tile"))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, componentState("VoiceShortcut"))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, componentState("DictationTileService"))
            preferences.tile = true
            preferences.button = false
            DictationShortcuts.sync(context)
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, componentState("VoiceShortcut"))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, componentState("DictationTileService"))
            assertFalse(preferences.floating || preferences.notification)
            shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            preferences.notification = true
            DictationShortcuts.sync(context)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 202 })
            preferences.notification = false
            DictationShortcuts.sync(context)
            await { context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 202 } }
        } finally {
            preferences.floating = saved[0]; preferences.tile = saved[1]
            preferences.notification = saved[2]; preferences.button = saved[3]
            instrumentation.runOnMainSync { DictationShortcuts.sync(context) }
        }
    }

    @Test fun accessibilityInsertsWithoutChangingKeyboardAndRejectsOldEditorConnection() = withService {
        val originalKeyboard = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var first: EditText
            lateinit var second: EditText
            scenario.onActivity { activity ->
                first = EditText(activity).apply { id = 1001; setText("Hello "); setSelection(length()) }
                second = EditText(activity).apply { id = 1002 }
                activity.setContentView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(first); addView(second)
                })
                first.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(first, 0)
            }
            await { DictationAccessibilityService.active?.inputMethod?.currentInputConnection != null }
            val connection = DictationAccessibilityService.active!!.inputMethod!!.currentInputConnection!!
            scenario.onActivity { first.onCreateInputConnection(EditorInfo())!!.setComposingText("world", 1) }
            instrumentation.runOnMainSync { connection.commitText("!", 1, null) }
            await { first.text.toString() == "Hello world!" }
            scenario.onActivity { second.requestFocus() }
            await { DictationAccessibilityService.active!!.inputMethod!!.currentInputEditorInfo?.fieldId == 1002 }
            instrumentation.runOnMainSync { connection.commitText("stale", 1, null) }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals("Hello world!", first.text.toString())
                assertEquals("", second.text.toString())
            }
            assertEquals(originalKeyboard, Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        }
    }

    @Test fun panelRecordingCancelsOnFieldChangeAndCanRestartThenClose() = withService {
        assumeNotNull(ModelStore(context).selected())
        shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
        val preferences = DictationPreferences(context)
        val original = preferences.button
        preferences.button = true
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                lateinit var first: EditText
                lateinit var second: EditText
                scenario.onActivity { activity ->
                    first = EditText(activity)
                    second = EditText(activity)
                    activity.setContentView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(first); addView(second)
                    })
                    first.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java).showSoftInput(first, 0)
                }
                await { DictationAccessibilityService.active?.inputMethod?.currentInputConnection != null }
                scenario.onActivity { it.startActivity(DictationShortcuts.launchIntent(it, "button")) }
                await { node("Record") != null }
                await { first.hasWindowFocus() }
                saveScreenshot("dictation-ready.png")
                click("Record")
                await { node("Stop") != null }
                saveScreenshot("dictation-recording.png")
                scenario.onActivity { second.requestFocus() }
                await { node("Record") != null }
                assertNotNull(node("The text field changed. Tap Record to start again."))
                click("Record")
                await { node("Stop") != null }
                click("Close")
                await { context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 201 } }
                scenario.onActivity {
                    assertEquals("", first.text.toString())
                    assertEquals("", second.text.toString())
                }
            }
        } finally { preferences.button = original }
    }

    @Test fun allFourShortcutsOpenTheSamePanel() = withService {
        shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
        shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        val preferences = DictationPreferences(context)
        val saved = listOf(preferences.floating, preferences.notification, preferences.button, preferences.tile)
        val previousTiles = shell("settings get secure sysui_qs_tiles").trim()
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                preferences.floating = true
                preferences.notification = true
                preferences.button = true
                preferences.tile = true
                instrumentation.runOnMainSync { DictationShortcuts.sync(context) }
                val microphone = "Open GriffBoard Voice. Drag to move."
                await { node(microphone) != null }
                click(microphone)
                await { node("Record") != null }
                click("Close")
                await { node(microphone) != null }
                preferences.floating = false
                instrumentation.runOnMainSync { DictationShortcuts.sync(context) }
                await { node(microphone) == null }
                shell("cmd statusbar expand-notifications")
                val notification = "Tap to open dictation without changing keyboards"
                await { node(notification) != null }
                click(notification)
                await { node("Record") != null }
                click("Close")
                val tile = "${context.packageName}/.dictation.DictationTileService"
                shell("cmd statusbar add-tile $tile")
                shell("cmd statusbar expand-settings")
                shell("cmd statusbar click-tile $tile")
                await { node("Record") != null }
                click("Close")
                scenario.onActivity {
                    it.startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context.packageName,
                        "${context.packageName}.dictation.VoiceShortcut")))
                }
                await { node("Record") != null }
                click("Close")
            }
        } finally {
            shell("cmd statusbar collapse")
            preferences.floating = saved[0]; preferences.notification = saved[1]; preferences.button = saved[2]
            preferences.tile = saved[3]
            instrumentation.runOnMainSync { DictationShortcuts.sync(context) }
            if (previousTiles != "null") shell("settings put secure sysui_qs_tiles '$previousTiles'")
        }
    }

    private fun withService(action: () -> Unit) {
        val setting = "enabled_accessibility_services"
        val previous = shell("settings get secure $setting").trim()
        val service = "${context.packageName}/.dictation.DictationAccessibilityService"
        val enabled = if (previous == "null" || previous.isEmpty()) service else "$previous:$service"
        shell("settings put secure $setting $enabled")
        try {
            await { DictationAccessibilityService.active != null }
            action()
        } finally {
            instrumentation.runOnMainSync { DictationAccessibilityService.active?.onInterrupt() }
            if (previous == "null") shell("settings delete secure $setting")
            else shell("settings put secure $setting '$previous'")
        }
    }

    private fun componentState(name: String) = context.packageManager.getComponentEnabledSetting(
        ComponentName(context.packageName, "${context.packageName}.dictation.$name"))
    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun node(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                find(child)?.let { return it }
            }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { it.root?.let(::find) }
    }
    private fun click(text: String) {
        var target = node(text)!!
        while (!target.isClickable && target.parent != null) target = target.parent
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun saveScreenshot(name: String) {
        val bitmap = automation.takeScreenshot()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
        assertTrue("Condition did not become true before timeout", condition())
    }
}
