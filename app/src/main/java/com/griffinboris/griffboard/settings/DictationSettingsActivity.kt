package com.griffinboris.griffboard.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.griffinboris.griffboard.R
import com.griffinboris.griffboard.dictation.DictationAccessibilityService
import com.griffinboris.griffboard.dictation.DictationPreferences
import com.griffinboris.griffboard.dictation.DictationShortcuts
import com.griffinboris.griffboard.dictation.DictationTileService

@SuppressLint("SetTextI18n")
class DictationSettingsActivity : AppCompatActivity() {
    private val preferences by lazy { DictationPreferences(this) }
    private val ui by lazy { SettingsViews(this) }
    private val switches = mutableListOf<Pair<MaterialSwitch, () -> Boolean>>()
    private lateinit var status: TextView
    private var refreshing = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        preferences.notification = allowed
        DictationShortcuts.sync(this)
        refresh()
        if (!allowed) Toast.makeText(this, "Notification shortcut is off. Notifications can be allowed in app settings.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = ui.column().apply { setPadding(dp(22), dp(18), dp(22), dp(28)) }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.addView(ui.label("Voice shortcuts", 28f, true))
        content.addView(ui.label("Keep Samsung Keyboard—or any keyboard—selected. Each shortcut below is optional and opens the same recording panel.", 16f))
        status = ui.label("", 14f, true)
        content.addView(status)
        if (Build.VERSION.SDK_INT < 33) {
            status.text = "Voice shortcuts require Android 13 or newer. Voice typing in GriffBoard's keyboard still works."
            return
        }
        content.addView(ui.button("Accessibility setup", true) {
            MaterialAlertDialogBuilder(this).setTitle("Enable GriffBoard Voice")
                .setMessage("This optional service shows the floating panel and inserts transcripts into the active text field. It receives editor and app-change events so changing fields cancels dictation. It does not read screen contents or keep typing history.\n\nIn Android Accessibility settings, open Downloaded apps or Installed apps, then enable GriffBoard Voice.")
                .setPositiveButton("Open Accessibility") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
        })
        content.addView(ui.section("Choose your shortcuts"))
        val controls = ui.column()
        controls.addView(toggle("Floating microphone", { preferences.floating }) { preferences.floating = it })
        controls.addView(ui.label("Drag the microphone to move it. Tap it to open the waveform panel.", 13f))
        controls.addView(toggle("Quick Settings tile", { preferences.tile }) { preferences.tile = it })
        controls.addView(ui.button("Add Quick Settings tile", true) {
            if (!preferences.tile) {
                Toast.makeText(this, "Turn on Quick Settings tile first.", Toast.LENGTH_SHORT).show()
            } else {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this, DictationTileService::class.java), "GriffBoard Voice",
                    Icon.createWithResource(this, R.drawable.ic_microphone), mainExecutor) { result ->
                    val added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    Toast.makeText(this, if (added) "Tile is ready in Quick Settings." else "You can add the tile later from Quick Settings.", Toast.LENGTH_SHORT).show()
                }
            }
        })
        controls.addView(toggle("Notification shortcut", { preferences.notification }) { enabled ->
            if (enabled && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else preferences.notification = enabled
        })
        controls.addView(ui.label("Keep an Open dictation shortcut in the notification shade. The recording status notification is separate and required while the panel is open.", 13f))
        controls.addView(toggle("Side-button / app shortcut", { preferences.button }) { preferences.button = it })
        controls.addView(ui.label("Adds GriffBoard Voice to your apps so Samsung can assign it to a button. Turning this off disables that launcher shortcut.", 13f))
        controls.addView(ui.button("Set up a custom button", true) {
            MaterialAlertDialogBuilder(this).setTitle("Samsung side button")
                .setMessage("Turn on Side-button / app shortcut above.\n\nOpen Settings → Advanced features → Side button → Double press → Open app, then choose GriffBoard Voice.\n\nYour phone's available buttons and menus may differ. GriffBoard opens the recording panel; it does not intercept hardware buttons.")
                .setPositiveButton("Open device settings") { _, _ -> startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .setNegativeButton("Close", null).show()
        })
        content.addView(ui.card(controls))
        content.addView(ui.section("Using the panel"))
        content.addView(ui.label("Open a text field, launch a shortcut, then tap Record. Tap Stop to transcribe. A transcript is sent only to the original field; Copy is available for apps without a supported editor. Close the panel to discard its transcript. The panel closes after two minutes of inactivity.", 14f))
        content.addView(ui.label("Microphone permission is requested on first use. Models and voice language are shared with GriffBoard's keyboard. No audio is captured just by enabling a shortcut.", 14f))
    }

    private fun toggle(label: String, value: () -> Boolean, changed: (Boolean) -> Unit) = MaterialSwitch(this).apply {
        text = label
        minHeight = dp(56)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        isChecked = value()
        switches.add(this to value)
        setOnCheckedChangeListener { _, enabled ->
            if (!refreshing) {
                changed(enabled)
                DictationShortcuts.sync(this@DictationSettingsActivity)
                refresh()
            }
        }
    }

    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() {
        if (Build.VERSION.SDK_INT < 33) return
        refreshing = true
        switches.forEach { (view, value) -> view.isChecked = value() }
        refreshing = false
        status.text = if (DictationAccessibilityService.active != null) "Voice service is ready. Enable any shortcuts you want."
            else "Enable Accessibility to use these shortcuts. Your default keyboard will stay selected."
        DictationShortcuts.sync(this)
    }
}
