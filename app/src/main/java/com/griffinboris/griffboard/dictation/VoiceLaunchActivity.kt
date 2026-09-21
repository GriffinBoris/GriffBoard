package com.griffinboris.griffboard.dictation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.griffinboris.griffboard.settings.DictationSettingsActivity
import com.griffinboris.griffboard.settings.SettingsViews

@SuppressLint("CustomSplashScreen") // Visible permission/foreground-service handoff, not an app splash screen.
class VoiceLaunchActivity : AppCompatActivity() {
    private var requested = false
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) openPanel() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(SettingsViews(this).label("Opening voice dictation…"))
    }

    override fun onResume() {
        super.onResume()
        if (requested) return
        requested = true
        val source = if (intent.action == Intent.ACTION_MAIN) "button" else intent.getStringExtra("source")
        if (Build.VERSION.SDK_INT < 33 || !DictationPreferences(this).allows(source)) { finish(); return }
        if (DictationAccessibilityService.active == null) {
            MaterialAlertDialogBuilder(this).setTitle("Set up voice dictation")
                .setMessage("Enable GriffBoard Voice in Accessibility settings to use the floating panel and insert text without changing keyboards.")
                .setPositiveButton("Set up") { _, _ -> startActivity(Intent(this, DictationSettingsActivity::class.java)); finish() }
                .setNegativeButton("Cancel") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        } else openPanel()
    }

    private fun openPanel() {
        // Keep this activity visible until the microphone foreground service has started.
        val ready = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) { finish() }
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, DictationAccessibilityService::class.java)
                .setAction("open").putExtra("ready", ready))
        } catch (_: IllegalStateException) {
            Toast.makeText(this, "Voice could not start. Unlock your phone and try the shortcut again.", Toast.LENGTH_LONG).show()
            finish()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Check microphone and Accessibility access in GriffBoard settings.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
