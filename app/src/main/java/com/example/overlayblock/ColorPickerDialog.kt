package com.example.overlayblock

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog

/**
 * Simple HSV color picker. Returns the chosen color to [onPicked].
 */
object ColorPickerDialog {

    fun show(activity: Activity, initial: Int, onPicked: (Int) -> Unit): Int {
        var hue = 0f
        var sat = 0f
        var val_ = 1f
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        hue = hsv[0]; sat = hsv[1]; val_ = hsv[2]

        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val container = FrameLayout(activity).apply {
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        container.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // Preview swatch
        val preview = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8f).toFloat()
                setColor(Color.HSVToColor(floatArrayOf(hue, sat, val_)))
            }
        }
        column.addView(preview, LinearLayout.LayoutParams(dp(72f), dp(48f)).apply { bottomMargin = dp(8f) })

        fun seekBar(from: Float, to: Float, step: Float, start: Float, onChange: (Float) -> Unit): SeekBar {
            val max = Math.max(1, ((to - from) / step).toInt())
            return SeekBar(activity).apply {
                this.max = max
                progress = (((start.coerceIn(from, to)) - from) / step).toInt().coerceIn(0, max)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        onChange(from + progress * step)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
        }

        fun refresh() {
            (preview.background as GradientDrawable).setColor(Color.HSVToColor(floatArrayOf(hue, sat, val_)))
        }

        column.addView(seekBar(0f, 360f, 1f, hue) { hue = it; refresh() })
        column.addView(seekBar(0f, 1f, 0.01f, sat) { sat = it; refresh() })
        column.addView(seekBar(0.05f, 1f, 0.01f, val_) { val_ = it; refresh() })

        // Hex input
        val hexInput = EditText(activity).apply {
            hint = "Hex e.g. #FF0000"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(hexOf(Color.HSVToColor(floatArrayOf(hue, sat, val_))))
            setOnEditorActionListener { v, _, _ ->
                parseHex(v.text.toString())?.let { c ->
                    val hsv2 = FloatArray(3)
                    Color.colorToHSV(c, hsv2)
                    hue = hsv2[0]; sat = hsv2[1]; val_ = hsv2[2]
                    refresh()
                }
                true
            }
        }
        column.addView(hexInput)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.pick_color))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onPicked(Color.HSVToColor(floatArrayOf(hue, sat, val_)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        return initial
    }

    private fun hexOf(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    private fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        return if (t.length == 6) t.toLongOrNull(16)?.toInt() else null
    }
}
