package com.volumeslider.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Rounded vertical slider that moves in whole steps (0..max), like the system volume panel. */
class VerticalSlider(context: Context) : View(context) {

    var max: Int = 15
    var fillColor: Int = 0xFF6AA8FF.toInt()
    var value: Int = 0
        set(v) {
            field = v.coerceIn(0, max)
            invalidate()
        }

    /** Called only when the user moves the slider by touch. */
    var onChange: ((Int) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF343C4D.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (value > 0) {
            fillPaint.color = fillColor
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawRect(0f, h - h * value / max, w, h, fillPaint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = 1f - (event.y / height).coerceIn(0f, 1f)
                val newValue = Math.round(fraction * max)
                if (newValue != value) {
                    value = newValue
                    onChange?.invoke(value)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }
}
