package com.volumeslider.app

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import kotlin.math.ceil

/**
 * Fine (1%) media volume.
 *
 * Android only has about 15 media steps. To get the levels in between, we set the next
 * step up and cut the small difference digitally with an audio effect (see FineVolumeService).
 */
object FineVolume {

    private const val PREFS = "volume_prefs"
    private const val KEY_FINE = "fine_mode"
    private const val DEFAULT_STEP_DB = 3f

    /** Digital cut (in dB) that is active right now, and the system step it belongs to. */
    @Volatile var cutDb = 0f
    @Volatile var cutIndex = -1

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FINE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FINE, enabled).apply()
        if (!enabled) stopService(context)
    }

    /** Does this phone allow the audio effect? */
    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            createEffect().release()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A gain-only effect on the global output mix (audio session 0). */
    fun createEffect(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2,
            false, 0,
            false, 0,
            false, 0,
            false
        ).build()
        val effect = DynamicsProcessing(0, 0, config)
        if (effect.setEnabled(true) != AudioEffect.SUCCESS) {
            effect.release()
            throw IllegalStateException("Could not enable the audio effect")
        }
        return effect
    }

    /** Wish in percent -> (system step to set, dB to cut). [stepSize] gives the dB size of the step below an index. */
    fun plan(percent: Int, max: Int, stepSize: (Int) -> Float): Pair<Int, Float> {
        val position = percent.coerceIn(0, 100) * max / 100f
        val index = ceil(position).toInt().coerceIn(0, max)
        val gap = index - position
        val cut = if (gap > 0.01f) gap * stepSize(index) else 0f
        return Pair(index, cut)
    }

    /** The other way round: system step + cut -> percent. */
    fun percentOf(index: Int, cutDb: Float, max: Int, stepSize: Float): Int {
        var position = index.toFloat()
        if (cutDb > 0f) position -= (cutDb / stepSize).coerceIn(0f, 1f)
        return Math.round(position * 100f / max)
    }

    /** Sets the media volume to [percent] (0..100). */
    fun setMediaPercent(context: Context, percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val (index, cut) = plan(percent, max) { stepDb(audio, it) }

        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != index) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
        cutDb = cut
        cutIndex = index

        val service = FineVolumeService.instance
        if (service != null) {
            service.applyCut()
        } else if (cut > 0f) {
            try {
                context.startForegroundService(Intent(context, FineVolumeService::class.java))
            } catch (e: Exception) {
                cutDb = 0f
                cutIndex = -1
            }
        }
    }

    /** Current media volume in percent, including the fine part. */
    fun mediaPercent(context: Context): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val cut = if (index == cutIndex) cutDb else 0f
        val step = if (cut > 0f) stepDb(audio, index) else 1f
        return percentOf(index, cut, max, step)
    }

    /** If something else changed the system step, the old cut no longer fits: drop it. */
    fun sync(context: Context) {
        if (cutDb <= 0f) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != cutIndex) stopService(context)
    }

    /** Stops the background service when no cut is needed any more. */
    fun releaseIfIdle(context: Context) {
        if (cutDb <= 0f) stopService(context)
    }

    fun stopService(context: Context) {
        cutDb = 0f
        cutIndex = -1
        context.stopService(Intent(context, FineVolumeService::class.java))
    }

    /** Size in dB of the system step just below [index] (from the phone's own volume curve). */
    private fun stepDb(audio: AudioManager, index: Int): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return DEFAULT_STEP_DB
        return try {
            val device = outputDeviceType(audio)
            val upper = audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, device)
            val lower = if (index > 1) {
                audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index - 1, device)
            } else {
                upper - 12f
            }
            val step = upper - lower
            if (step.isNaN() || step <= 0f) DEFAULT_STEP_DB else step.coerceIn(0.5f, 12f)
        } catch (e: Exception) {
            DEFAULT_STEP_DB
        }
    }

    private fun outputDeviceType(audio: AudioManager): Int {
        val types = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return when {
            types.contains(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) -> AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            types.contains(AudioDeviceInfo.TYPE_WIRED_HEADPHONES) -> AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            types.contains(AudioDeviceInfo.TYPE_WIRED_HEADSET) -> AudioDeviceInfo.TYPE_WIRED_HEADSET
            types.contains(AudioDeviceInfo.TYPE_USB_HEADSET) -> AudioDeviceInfo.TYPE_USB_HEADSET
            else -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }
}
