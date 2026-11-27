package com.volumeslider.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The "Volume slider" tile in the Quick Settings panel. */
class VolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_volume)
        tile.label = getString(R.string.tile_label)
        tile.state = Tile.STATE_ACTIVE

        // Small text under the tile name (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FineVolume.sync(this)
            tile.subtitle = "Media ${FineVolume.mediaPercent(this)}%"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { openPanel() }
        } else {
            openPanel()
        }
    }

    @Suppress("DEPRECATION")
    private fun openPanel() {
        val intent = Intent(this, VolumePanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires a PendingIntent here
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
