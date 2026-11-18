package com.carlifemirror.app.video

import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import android.widget.Toast
import com.carlifemirror.app.MainActivity
import com.carlifemirror.app.R
import com.carlifemirror.app.touch.MirrorAccessibilityService
import com.carlifemirror.app.util.Logx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private var lastClockUpdate = 0L

    // Some head units stop receiving frames when a secondary display is
    // completely static. Keep the Presentation surface invalidated at the
    // requested video cadence so MediaCodec continues producing fresh frames.
    private val tick = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastClockUpdate >= 30_000L || lastClockUpdate == 0L) {
                clock?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                date?.text = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(Date())
                lastClockUpdate = now
            }
            root?.postInvalidateOnAnimation()
            handler.postDelayed(this, 33L)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_carpird)
        root = findViewById(android.R.id.content)
        clock = findViewById(R.id.carpirdClock)
        date = findViewById(R.id.carpirdDate)
        renderTiles()
        findViewById<View>(R.id.carpirdHome).setOnClickListener { renderTiles() }
        findViewById<View>(R.id.carpirdBack).setOnClickListener {
            val service = MirrorAccessibilityService.instance
            val handled = service?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            ) == true
            if (!handled) {
                Toast.makeText(hostContext, "Back navigation is unavailable; use the car display's system Back control.", Toast.LENGTH_LONG).show()
            }
        }
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        super.onStop()
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
            val view = LinearLayout(getContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { launch(tile.second, tile.first) }
            }
            val colors = tileColors(index)
            val frame = LinearLayout(getContext()).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    colors,
                ).apply {
                    cornerRadius = dp(19).toFloat()
                    setStroke(dp(1), 0x66FFFFFF)
                }
            }
            val drawable = resolveTileIcon(tile.second)
            if (drawable != null) {
                frame.addView(ImageView(getContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(62), dp(62))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                })
            } else {
                frame.addView(TextView(getContext()).apply {
                    text = tile.third
                    gravity = Gravity.CENTER
                    textSize = if (tile.second == "TV") 25f else 30f
                    setTextColor(0xFFFFFFFF.toInt())
                })
            }
            view.addView(frame, LinearLayout.LayoutParams(dp(86), dp(86)))
            view.addView(TextView(getContext()).apply {
                text = tile.first
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(0xFFF3F7FF.toInt())
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            grid.addView(view, GridLayout.LayoutParams(
                GridLayout.spec(index / 4, 1f),
                GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(7), dp(4), dp(7), dp(4))
            })
        }
    }

    private fun tileColors(index: Int): IntArray = when (index) {
        0 -> intArrayOf(0xFF8B5CF6.toInt(), 0xFF4338CA.toInt())
        1 -> intArrayOf(0xFFFF5555.toInt(), 0xFFB91C1C.toInt())
        2 -> intArrayOf(0xFF22D3EE.toInt(), 0xFF0E7490.toInt())
        3 -> intArrayOf(0xFF4ADE80.toInt(), 0xFF15803D.toInt())
        4 -> intArrayOf(0xFF4ADE80.toInt(), 0xFF166534.toInt())
        5 -> intArrayOf(0xFFC084FC.toInt(), 0xFF6B21A8.toInt())
        6 -> intArrayOf(0xFF94A3B8.toInt(), 0xFF334155.toInt())
        else -> intArrayOf(0xFFFBBF24.toInt(), 0xFFB45309.toInt())
    }

    private fun resolveTileIcon(target: String): Drawable? {
        val pm = hostContext.packageManager
        if (target.startsWith("com.")) {
            try {
                return pm.getApplicationIcon(target)
            } catch (_: Exception) {
                return null
            }
        }
        val intent = when {
            target == "TV" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            target.startsWith("http") -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
            target == "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            target == "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
            else -> null
        } ?: return null
        return try {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
    }

    private fun launch(target: String, label: String) {
        val intent = try {
            when {
                target == "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                target == "phone_screen" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                target.startsWith("http") -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
                target == "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                target == "TV" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                target.startsWith("com.") -> hostContext.packageManager.getLaunchIntentForPackage(target)
                else -> null
            }
        } catch (e: Exception) {
            Logx.w("CarPird: $label intent creation failed: ${e.message}")
            null
        }
        if (intent == null) {
            Toast.makeText(hostContext, "$label app is not available", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(hostContext, "This Android version cannot open apps on the separate car display.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(targetDisplay.displayId)
            hostContext.startActivity(intent, options.toBundle())
            Logx.i("CarPird: launching $label on secondary display ${targetDisplay.displayId}")
        } catch (e: Exception) {
            Logx.e("CarPird: could not launch $label on secondary display", e)
            Toast.makeText(hostContext, "$label cannot be opened on the car display on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * getContext().resources.displayMetrics.density).toInt()

    fun dispatchTouch(x: Float, y: Float, action: Int, pointerId: Int = 0): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return dispatchTouchOnMain(x, y, action)
        val latch = CountDownLatch(1)
        var handled = false
        val posted = handler.post {
            try { handled = dispatchTouchOnMain(x, y, action) } finally { latch.countDown() }
        }
        if (!posted) return false
        return try {
            latch.await(500, TimeUnit.MILLISECONDS) && handled
        } catch (_: InterruptedException) {
            false
        }
    }

    private fun dispatchTouchOnMain(x: Float, y: Float, action: Int): Boolean {
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
