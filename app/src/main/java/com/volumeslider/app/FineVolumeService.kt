package com.volumeslider.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.os.IBinder

/** Keeps the audio effect alive in the background (Android needs a visible notification for that). */
class FineVolumeService : Service() {

    companion object {
        @Volatile var instance: FineVolumeService? = null
        private const val CHANNEL_ID = "fine_volume"
        private const val NOTIFICATION_ID = 1
    }

    private var effect: DynamicsProcessing? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fine volume", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go to the foreground right away, or Android stops the service
        startForeground(NOTIFICATION_ID, buildNotification())
        applyCut()
        return START_NOT_STICKY
    }

    /** Applies FineVolume.cutDb to the effect (the effect is created on first use). */
    fun applyCut() {
        try {
            val current = effect ?: FineVolume.createEffect().also { effect = it }
            current.setInputGainAllChannelsTo(-FineVolume.cutDb)
        } catch (e: Exception) {
            // This phone refused the effect: go back to the normal steps
            FineVolume.setEnabled(this, false)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_volume)
            .setContentTitle("Fine volume is on")
            .setContentText("Tap to open Volume. Other sounds may be slightly lower.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        effect?.release()
        effect = null
        FineVolume.cutDb = 0f
        FineVolume.cutIndex = -1
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
