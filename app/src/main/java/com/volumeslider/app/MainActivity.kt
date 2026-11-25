package com.volumeslider.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Launcher screen: shows how to add the tile and lets you test the panel. */
class MainActivity : Activity() {

    private val grey = 0xFFAEB7C5.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(24), dp(48), dp(24), dp(24))

        column.addView(text("Volume", 34f, Color.WHITE, bold = true))
        column.addView(text("A volume slider for your Quick Settings panel.", 16f, grey, top = 4))
        column.addView(text("How to add it", 20f, Color.WHITE, bold = true, top = 32))
        column.addView(
            text(
                "1. Swipe down twice to open Quick Settings.\n" +
                    "2. Tap the pencil (Edit) icon.\n" +
                    "3. Drag the \"Volume slider\" tile to the top.\n" +
                    "4. Tap the tile any time to open the volume panel.",
                16f, grey, top = 8
            )
        )

        column.addView(button("Open volume panel") {
            startActivity(Intent(this, VolumePanelActivity::class.java))
        })
        column.addView(button("Allow Do Not Disturb access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        column.addView(
            text("Do Not Disturb access is only needed to mute the ringtone completely.", 13f, grey, top = 8)
        )

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF111827.toInt())
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(0, dp(top), 0, 0)
        return tv
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(16)
        b.layoutParams = params
        return b
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
