package com.griffinboris.griffboard.dictation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.griffinboris.griffboard.R

object DictationShortcuts {
    const val RECORDING_NOTIFICATION = 201
    private const val SHORTCUT_NOTIFICATION = 202

    fun launchIntent(context: Context, source: String) = Intent(context, VoiceLaunchActivity::class.java)
        .putExtra("source", source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    fun sync(context: Context) {
        val preferences = DictationPreferences(context)
        val supported = Build.VERSION.SDK_INT >= 33
        for ((name, enabled) in listOf("dictation.VoiceShortcut" to preferences.button,
            "dictation.DictationTileService" to preferences.tile)) {
            context.packageManager.setComponentEnabledSetting(ComponentName(context.packageName, "${context.packageName}.$name"),
                if (supported && enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("voice-shortcut", "Voice shortcut", NotificationManager.IMPORTANCE_LOW))
        if (supported && preferences.notification &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val launch = PendingIntent.getActivity(context, SHORTCUT_NOTIFICATION, launchIntent(context, "notification"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(SHORTCUT_NOTIFICATION, Notification.Builder(context, "voice-shortcut")
                .setSmallIcon(R.drawable.ic_microphone).setContentTitle("GriffBoard Voice")
                .setContentText("Tap to open dictation without changing keyboards")
                .setContentIntent(launch).setOngoing(true).setOnlyAlertOnce(true).build())
        } else manager.cancel(SHORTCUT_NOTIFICATION)
        if (supported) DictationAccessibilityService.active?.refreshFloatingButton()
    }

    fun recordingNotification(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("voice-recording", "Voice recording", NotificationManager.IMPORTANCE_LOW))
        val close = PendingIntent.getService(context, RECORDING_NOTIFICATION,
            Intent(context, DictationAccessibilityService::class.java).setAction("close"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, "voice-recording").setSmallIcon(R.drawable.ic_microphone)
            .setContentTitle("GriffBoard dictation is open").setContentText("Use the floating panel to record. Tap here to close.")
            .setContentIntent(close).setOngoing(true).setOnlyAlertOnce(true).build()
    }
}
