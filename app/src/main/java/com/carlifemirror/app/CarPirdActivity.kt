package com.carlifemirror.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Landscape launcher shown on the phone while its display is mirrored to the car.
 * It intentionally stays in the foreground instead of sending the user to Android Home.
 */
class CarPirdActivity : Activity() {
    private val featuredApps = listOf(
        FeaturedApp("YouTube", "com.google.android.youtube", 0xFF292929.toInt()),
        FeaturedApp("Google Maps", "com.google.android.apps.maps", 0xFF173B32.toInt()),
        FeaturedApp("Waze", "com.waze", 0xFF173B55.toInt()),
    )

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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        setContentView(R.layout.activity_carpird)
        findViewById<TextView>(R.id.carpirdSettings).setOnClickListener { openMain(settings = true) }
        findViewById<TextView>(R.id.carpirdExit).setOnClickListener { openMain(settings = false) }
        renderFeatured()
        renderInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        MirrorState.addListener(mirrorListener)
        if (!MirrorState.value.projectionReady) finish()
    }

    override fun onPause() {
        MirrorState.removeListener(mirrorListener)
        super.onPause()
    }

    private fun openMain(settings: Boolean) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (settings) intent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
        startActivity(intent)
    }

    private fun renderFeatured() {
        val container = findViewById<LinearLayout>(R.id.featuredApps)
        featuredApps.forEach { app ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(app.color, dp(20))
                isClickable = true
                isFocusable = true
                setPadding(dp(10), dp(12), dp(10), dp(12))
                setOnClickListener { launchPackage(app.packageName, app.label) }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                try {
                    setImageDrawable(packageManager.getApplicationIcon(app.packageName))
                } catch (_: Exception) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }
            val label = TextView(this).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            card.addView(icon)
            card.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            container.addView(card, LinearLayout.LayoutParams(0, dp(142), 1f).apply {
                marginStart = dp(7)
                marginEnd = dp(7)
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun renderInstalledApps() {
        val container = findViewById<LinearLayout>(R.id.installedApps)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps: List<ResolveInfo> = packageManager.queryIntentActivities(
            launcherIntent, PackageManager.MATCH_ALL
        ).filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val featuredPackages = featuredApps.map { it.packageName }.toSet()
        val remaining = apps.filterNot { it.activityInfo.packageName in featuredPackages }
        val columns = 5
        var row: LinearLayout? = null
        remaining.forEachIndexed { index, info ->
            if (index % columns == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                container.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                })
            }
            row?.addView(appTile(info), LinearLayout.LayoutParams(0, dp(104), 1f))
        }
        val remainder = remaining.size % columns
        if (remainder != 0) repeat(columns - remainder) {
            row?.addView(View(this), LinearLayout.LayoutParams(0, dp(104), 1f))
        }
        if (remaining.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Your installed apps will appear here"
                setTextColor(0xFF9AA8BC.toInt())
                textSize = 14f
                setPadding(0, dp(14), 0, dp(14))
            })
        }
    }

    private fun appTile(info: ResolveInfo): View {
        val packageName = info.activityInfo.packageName
        val labelText = info.loadLabel(packageManager).toString()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener { launchPackage(packageName, labelText) }
            addView(ImageView(this@CarPirdActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                try {
                    setImageDrawable(info.loadIcon(packageManager))
                } catch (_: Exception) {
                    setImageResource(android.R.drawable.sym_def_app_icon)
                }
            })
            addView(TextView(this@CarPirdActivity).apply {
                text = labelText
                setTextColor(0xFFE8EEF7.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun launchPackage(packageName: String, label: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                Toast.makeText(this, "$label is not available", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(launch)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open $label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class FeaturedApp(val label: String, val packageName: String, val color: Int)
}
