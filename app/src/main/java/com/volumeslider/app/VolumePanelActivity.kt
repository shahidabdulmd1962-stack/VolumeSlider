package com.volumeslider.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Normal screen version of the panel (used by the "Open volume panel" button). */
class VolumePanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            VolumePanel(
                this,
                onClose = { finish() },
                onNeedDndAccess = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            )
        )
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}

/**
 * The volume card (four vertical sliders) on the right edge of a transparent full-screen layer.
 * The Quick Settings tile shows it inside a dialog, so the video or music underneath keeps playing.
 */
class VolumePanel(
    context: Context,
    private val onClose: () -> Unit,
    private val onNeedDndAccess: () -> Unit
) : FrameLayout(context) {

    private class Channel(val name: String, val stream: Int, val color: Int)

    private val channels = listOf(
        Channel("Media", AudioManager.STREAM_MUSIC, 0xFF6AA8FF.toInt()),
        Channel("Ring", AudioManager.STREAM_RING, 0xFFFFB454.toInt()),
        Channel("Alarm", AudioManager.STREAM_ALARM, 0xFFFF8A80.toInt()),
        Channel("Call", AudioManager.STREAM_VOICE_CALL, 0xFF5FD39A.toInt())
    )

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val closeTask = Runnable { onClose() }
    private var askedForDnd = false

    init {
        // Tapping outside the card closes the panel
        setOnClickListener { onClose() }

        // The card that holds the sliders
        val background = GradientDrawable()
        background.setColor(0xF2202733.toInt())
        background.setCornerRadius(dp(28).toFloat())

        val card = LinearLayout(context)
        card.orientation = LinearLayout.HORIZONTAL
        card.setPadding(dp(10), dp(16), dp(10), dp(12))
        card.setBackground(background)
        card.isClickable = true
        for (channel in channels) {
            card.addView(createColumn(channel))
        }

        // Right side, vertically centred
        val params = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        )
        params.marginEnd = dp(10)
        addView(card, params)
    }

    private fun createColumn(channel: Channel): LinearLayout {
        val steps = audio.getStreamMaxVolume(channel.stream).coerceAtLeast(1)

        val percent = TextView(context)
        percent.setTextColor(Color.WHITE)
        percent.textSize = 13f
        percent.typeface = Typeface.DEFAULT_BOLD
        percent.gravity = Gravity.CENTER

        val slider = VerticalSlider(context)
        slider.max = steps
        slider.fillColor = channel.color
        slider.value = audio.getStreamVolume(channel.stream)
        percent.text = percentText(slider.value, steps)

        slider.onChange = { level ->
            setVolume(channel.stream, level)
            val actual = audio.getStreamVolume(channel.stream)
            slider.value = actual
            percent.text = percentText(actual, steps)
        }

        val label = TextView(context)
        label.text = channel.name
        label.setTextColor(0xFFAEB7C5.toInt())
        label.textSize = 12f
        label.gravity = Gravity.CENTER

        val sliderHeight = minOf(dp(210), (resources.displayMetrics.heightPixels * 0.42f).toInt())
        val sliderParams = LinearLayout.LayoutParams(dp(40), sliderHeight)
        sliderParams.topMargin = dp(8)
        sliderParams.bottomMargin = dp(10)

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(5), 0, dp(5), 0)
        column.addView(percent)
        column.addView(slider, sliderParams)
        column.addView(label)
        return column
    }

    private fun setVolume(stream: Int, level: Int) {
        try {
            audio.setStreamVolume(stream, level, 0)
        } catch (e: SecurityException) {
            // Muting the ringtone needs "Do Not Disturb access"
            if (!askedForDnd) {
                askedForDnd = true
                Toast.makeText(
                    context,
                    "Allow Do Not Disturb access in the Volume app to mute the ringtone",
                    Toast.LENGTH_LONG
                ).show()
                onNeedDndAccess()
            }
        }
    }

    private fun percentText(value: Int, steps: Int) = "${value * 100 / steps}%"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // Close the panel after 5 seconds without touch
    private fun scheduleClose() {
        handler.removeCallbacks(closeTask)
        handler.postDelayed(closeTask, 5000)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleClose()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(closeTask)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scheduleClose()
        return super.dispatchTouchEvent(ev)
    }
}
