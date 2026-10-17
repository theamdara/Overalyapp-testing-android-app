package com.example.overlayblock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.overlayblock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val swatchColors = mutableListOf<Int>()

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startOverlay()
            } else {
                binding.statusText.setText(R.string.grant_overlay_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleButton.setOnClickListener {
            if (isOverlayRunning()) {
                stopOverlay()
            } else {
                ensureOverlayPermissionThenStart()
            }
        }

        // Preset swatches
        val presets = listOf(
            Color.BLACK to getString(R.string.color_black),
            Color.rgb(33, 33, 33) to getString(R.string.color_dark_gray),
            Color.rgb(0, 105, 148) to getString(R.string.color_blue),
            Color.rgb(199, 47, 47) to getString(R.string.color_red),
            Color.rgb(46, 124, 50) to getString(R.string.color_green),
            Color.rgb(255, 255, 255) to getString(R.string.color_white)
        )
        val size = dp(48)
        for ((color, label) in presets) {
            swatchColors.add(color)
            val swatch = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(color)
                    setStroke(dp(2), if (color == Color.WHITE) Color.LTGRAY else Color.TRANSPARENT)
                }
                contentDescription = label
                isClickable = true
                setOnClickListener { pickColor(color) }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = dp(10)
            binding.swatchRow.addView(swatch, lp)
        }

        binding.customColorButton.setOnClickListener {
            pickColor(ColorPickerDialog.show(this, currentColor(), ::pickColor))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun currentColor(): Int =
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
            .getInt(OverlayService.KEY_COLOR, OverlayService.DEFAULT_COLOR)

    private fun pickColor(color: Int) {
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
            .edit().putInt(OverlayService.KEY_COLOR, color).apply()
        if (isOverlayRunning()) {
            val intent = Intent(this, OverlayService::class.java)
                .putExtra(OverlayService.EXTRA_COLOR, color)
            startService(intent)
        }
        updateSwatchSelection(color)
    }

    private fun updateSwatchSelection(selected: Int) {
        for (i in 0 until binding.swatchRow.childCount) {
            val child = binding.swatchRow.getChildAt(i)
            val bg = child.background as? android.graphics.drawable.GradientDrawable ?: continue
            val isSelected = i < swatchColors.size && swatchColors[i] == selected
            bg.setStroke(dp(if (isSelected) 4 else 2), if (isSelected) Color.YELLOW else Color.TRANSPARENT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun isOverlayRunning(): Boolean = OverlayService.isRunning

    private fun updateUi() {
        val running = isOverlayRunning()
        binding.toggleButton.setText(if (running) R.string.stop_overlay else R.string.start_overlay)
        binding.statusText.setText(
            when {
                !Settings.canDrawOverlays(this) -> R.string.grant_overlay_permission
                running -> R.string.status_running
                else -> R.string.status_stopped
            }
        )
        updateSwatchSelection(currentColor())
    }

    private fun ensureOverlayPermissionThenStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
        } else {
            binding.statusText.setText(R.string.status_granting)
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private var overlayMode = false

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        enterOverlayMode()
        updateUi()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        exitOverlayMode()
        updateUi()
    }

    /** Make our window transparent + touch-transparent so it can sit on top of
     *  other apps without blocking them, keeping the overlay window visible. */
    private fun enterOverlayMode() {
        if (overlayMode) return
        overlayMode = true
        setTheme(R.style.Theme_OverlayBlock_Transparent)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
        // Re-launch ourselves on top of whatever is currently in the foreground.
        val self = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(self)
    }

    private fun exitOverlayMode() {
        if (!overlayMode) return
        overlayMode = false
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        setTheme(R.style.Theme_OverlayBlock)
    }
}
