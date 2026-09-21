package com.griffinboris.griffboard.dictation

import android.annotation.SuppressLint

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DictationTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (DictationPreferences(this@DictationTileService).tile) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated") // PendingIntent overload is unavailable on Android 13.
    override fun onClick() {
        if (!DictationPreferences(this).tile) return
        unlockAndRun {
            val intent = DictationShortcuts.launchIntent(this, "tile")
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else startActivityAndCollapse(intent)
        }
    }
}
