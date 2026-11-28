package com.volumeslider.app

import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

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
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val percent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
            tile.subtitle = "Media $percent%"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { showPanel() }
        } else {
            showPanel()
        }
    }

    /**
     * Shows the sliders in a dialog on top of the current app. No new screen is opened,
     * so a playing reel, video or song keeps playing while you change the volume.
     */
    private fun showPanel() {
        try {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(
                VolumePanel(dialog.context, onClose = { dialog.dismiss() }, onNeedDndAccess = { })
            )
            val window = dialog.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            showDialog(dialog)
        } catch (e: Exception) {
            // The dialog could not be shown: use the normal screen instead (a playing video pauses)
            openPanelScreen()
        }
    }

    @Suppress("DEPRECATION")
    private fun openPanelScreen() {
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
