package com.carlifemirror.app.video

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.carlifemirror.app.MainActivity
import com.carlifemirror.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CarPird UI rendered directly on the app-owned secondary display.
 * It intentionally does not depend on MediaProjection.
 */
class CarPirdPresentation(
    private val hostContext: Context,
    private val targetDisplay: android.view.Display,
) : Presentation(hostContext, targetDisplay) {

    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var clock: TextView? = null
    private var date: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            clock?.let {
                it.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            date?.let {
                it.text = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(Date())
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_carpird)
        root = findViewById(android.R.id.content)
        clock = findViewById(R.id.carpirdClock)
        date = findViewById(R.id.carpirdDate)
        renderTiles()
        findViewById<View>(R.id.carpirdHome).setOnClickListener { renderClock() }
        findViewById<View>(R.id.carpirdBack).setOnClickListener {
            hostContext.startActivity(Intent(hostContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    private fun renderClock() {
        val now = Date()
        clock?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date?.text = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(now)
    }

    private fun renderTiles() {
        val grid = findViewById<GridLayout>(R.id.carpirdAppGrid)
        grid.removeAllViews()
        val tiles = listOf(
            Triple("TV", "TV", "TV"),
            Triple("YouTube", "com.google.android.youtube", "▶"),
            Triple("Browser", "https://www.google.com", "◎"),
            Triple("Waze", "com.waze", "◉"),
            Triple("Maps", "com.google.android.apps.maps", "⌖"),
            Triple("Music", "music", "♫"),
            Triple("Settings", "settings", "⚙"),
            Triple("Phone screen", "phone_screen", "▣"),
        )
        tiles.forEachIndexed { index, tile ->
            val view = LinearLayout(hostContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { launch(tile.second, tile.first) }
            }
            val frame = LinearLayout(hostContext).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFFF3F7FC.toInt(), 0xFF39485A.toInt())
                ).apply { cornerRadius = 19f }
            }
            val icon = ImageView(hostContext).apply {
                layoutParams = LinearLayout.LayoutParams(66, 66)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val d = when {
                    tile.second.startsWith("com.") -> try { hostContext.packageManager.getApplicationIcon(tile.second) } catch (_: Exception) { null }
                    else -> null
                }
                if (d != null) setImageDrawable(d) else {
                    val fallback = TextView(hostContext).apply {
                        text = tile.third
                        gravity = Gravity.CENTER
                        textSize = 30f
                        setTextColor(0xFFFFFFFF.toInt())
                    }
                    frame.addView(fallback)
                }
            }
            if (icon.drawable != null) frame.addView(icon)
            view.addView(frame, LinearLayout.LayoutParams(86, 86))
            view.addView(TextView(hostContext).apply {
                text = tile.first
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(0xFFF3F7FF.toInt())
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
            grid.addView(view, GridLayout.LayoutParams(
                GridLayout.spec(index / 4, 1f),
                GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(7, 4, 7, 4)
            })
        }
    }

    private fun launch(target: String, label: String) {
        try {
            val intent = when {
                target == "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                target == "phone_screen" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                target.startsWith("http") -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
                target == "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                target == "TV" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                target.startsWith("com.") -> hostContext.packageManager.getLaunchIntentForPackage(target)
                else -> null
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                hostContext.startActivity(intent)
            }
        } catch (_: Exception) {
            // Keep the dashboard alive if an optional target app is unavailable.
        }
    }

    fun dispatchTouch(x: Float, y: Float, action: Int, pointerId: Int = 0): Boolean {
        val target = root ?: return false
        val eventTime = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0)
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)
        val handled = target.dispatchTouchEvent(event)
        event.recycle()
        return handled
    }

    fun mapPoint(x: Int, y: Int, videoW: Int, videoH: Int): PointF =
        PointF(
            x.toFloat() * targetDisplay.width / videoW.coerceAtLeast(1),
            y.toFloat() * targetDisplay.height / videoH.coerceAtLeast(1),
        )
}
