package com.carlifemirror.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CarPird's interactive landscape launcher shown during an active CarLife session. */
class CarPirdActivity : Activity() {
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView

    private data class AppTile(
        val label: String,
        val kind: TileKind,
        val packageName: String? = null,
        val fallback: String,
        val tint: Int
    )

    private enum class TileKind { TV, YOUTUBE, BROWSER, WAZE, MAPS, MUSIC, SETTINGS, PHONE_SCREEN }

    private val tiles = listOf(
        AppTile("TV", TileKind.TV, fallback = "TV", tint = 0xFFF3F7FC.toInt()),
        AppTile("YouTube", TileKind.YOUTUBE, "com.google.android.youtube", "▶", 0xFFF3F7FC.toInt()),
        AppTile("Browser", TileKind.BROWSER, fallback = "◎", tint = 0xFFF3F7FC.toInt()),
        AppTile("Waze", TileKind.WAZE, "com.waze", "◉", 0xFF08A9E8.toInt()),
        AppTile("Maps", TileKind.MAPS, "com.google.android.apps.maps", "⌖", 0xFFF3F7FC.toInt()),
        AppTile("Music", TileKind.MUSIC, fallback = "♫", tint = 0xFF9C36D5.toInt()),
        AppTile("Settings", TileKind.SETTINGS, fallback = "⚙", tint = 0xFF39485A.toInt()),
        AppTile("Phone screen", TileKind.PHONE_SCREEN, fallback = "▣", tint = 0xFFFF8A27.toInt())
    )

    private val clockTick = object : Runnable {
        override fun run() {
            if (!isFinishing && ::clockView.isInitialized) {
                val now = Date()
                clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                dateView.text = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(now)
                clockHandler.postDelayed(this, 30_000L)
            }
        }
    }

    private val mirrorListener: (MirrorSnapshot) -> Unit = { snapshot ->
        if (!snapshot.projectionReady) runOnUiThread { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MirrorState.value.projectionReady) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        setContentView(R.layout.activity_carpird)
        clockView = findViewById(R.id.carpirdClock)
        dateView = findViewById(R.id.carpirdDate)
        renderClock()
        renderAppTiles()
        findViewById<View>(R.id.carpirdHome).setOnClickListener { renderClock() }
        findViewById<View>(R.id.carpirdBack).setOnClickListener { openMain() }
    }

    override fun onResume() {
        super.onResume()
        MirrorState.addListener(mirrorListener)
        if (!MirrorState.value.projectionReady) finish()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        MirrorState.removeListener(mirrorListener)
        super.onPause()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun renderClock() {
        if (::clockView.isInitialized) {
            val now = Date()
            clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            dateView.text = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(now)
        }
    }

    private fun renderAppTiles() {
        val grid = findViewById<GridLayout>(R.id.carpirdAppGrid)
        grid.removeAllViews()
        tiles.forEachIndexed { index, tile ->
            val tileView = createTile(tile)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 4, 1f),
                GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(7), dp(4), dp(7), dp(4))
            }
            grid.addView(tileView, params)
        }
    }

    private fun createTile(tile: AppTile): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { launchTile(tile) }
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val iconFrame = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    lighten(tile.tint, 28),
                    tile.tint,
                    darken(tile.tint, 22)
                )
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(7).toFloat()
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(57), dp(57))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val packageName = tile.packageName
            val drawable = packageName?.let {
                try { packageManager.getApplicationIcon(it) } catch (_: Exception) { null }
            }
            if (drawable != null) {
                setImageDrawable(drawable)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            } else {
                visibility = View.GONE
            }
        }
        val fallback = TextView(this).apply {
            text = tile.fallback
            gravity = Gravity.CENTER
            textSize = if (tile.kind == TileKind.TV) 25f else 34f
            typeface = if (tile.kind == TileKind.TV) Typeface.SERIF else Typeface.DEFAULT
            setTextColor(if (tile.kind == TileKind.SETTINGS) 0xFFE7EDF5.toInt() else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(66), dp(66))
            visibility = if (icon.drawable == null) View.VISIBLE else View.GONE
        }
        iconFrame.addView(icon)
        iconFrame.addView(fallback)
        column.addView(iconFrame, LinearLayout.LayoutParams(dp(86), dp(86)))
        column.addView(TextView(this).apply {
            text = tile.label
            gravity = Gravity.CENTER
            textSize = 13f
            maxLines = 1
            setTextColor(0xFFF3F7FF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return column
    }

    private fun launchTile(tile: AppTile) {
        try {
            when (tile.kind) {
                TileKind.SETTINGS -> openMain(settings = true)
                TileKind.PHONE_SCREEN -> {
                    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    startActivity(home)
                }
                TileKind.BROWSER -> {
                    val browser = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                    startActivity(browser)
                }
                TileKind.MUSIC -> launchMusic()
                TileKind.TV -> launchTv()
                TileKind.YOUTUBE, TileKind.WAZE, TileKind.MAPS -> {
                    val pkg = tile.packageName!!
                    val launch = packageManager.getLaunchIntentForPackage(pkg)
                    if (launch == null) unavailable(tile.label) else startActivity(launch)
                }
            }
        } catch (_: Exception) {
            unavailable(tile.label)
        }
    }

    private fun launchMusic() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved != null) {
            startActivity(intent)
            return
        }
        launchByLabel(listOf("music", "spotify", "player", "audio"), "Music")
    }

    @Suppress("DEPRECATION")
    private fun launchTv() {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvApp = packageManager.queryIntentActivities(leanback, PackageManager.MATCH_ALL).firstOrNull()
        if (tvApp != null) {
            startActivity(leanback.setClassName(tvApp.activityInfo.packageName, tvApp.activityInfo.name))
            return
        }
        launchByLabel(listOf("iptv", "tv", "television", "live"), "TV")
    }

    @Suppress("DEPRECATION")
    private fun launchByLabel(keywords: List<String>, label: String) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .firstOrNull { info ->
                val appLabel = info.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                keywords.any { appLabel.contains(it) }
            }
        if (match == null) {
            unavailable(label)
            return
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(match.activityInfo.packageName, match.activityInfo.name)
        startActivity(intent)
    }

    private fun unavailable(label: String) {
        Toast.makeText(this, "$label app is not installed or cannot be opened", Toast.LENGTH_LONG).show()
    }

    private fun openMain(settings: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (settings) intent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
        startActivity(intent)
    }

    private fun lighten(color: Int, amount: Int): Int = adjust(color, amount)
    private fun darken(color: Int, amount: Int): Int = adjust(color, -amount)
    private fun adjust(color: Int, amount: Int): Int {
        val r = (Color.red(color) + amount).coerceIn(0, 255)
        val g = (Color.green(color) + amount).coerceIn(0, 255)
        val b = (Color.blue(color) + amount).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
