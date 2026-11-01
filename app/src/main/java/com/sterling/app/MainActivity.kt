package com.sterling.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(56), dp(24), dp(24)) }
        val root = ScrollView(this).apply {
            isFillViewport = true; addView(col)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0B0E1F.toInt(), 0xFF1B1F4A.toInt()))
        }
        fun label(s: String, sp: Float, c: Int) = TextView(this).apply { text = s; textSize = sp; setTextColor(c); gravity = Gravity.CENTER }
        col.addView(label("Sterling", 40f, Color.WHITE))
        col.addView(label("Starts listening the moment you start it.", 15f, 0xFF9AA3D6.toInt()).apply { setPadding(0, 0, 0, dp(28)) })
        fun btn(s: String, primary: Boolean = false, f: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 28 * d; setColor(if (primary) 0xFF6C8CFF.toInt() else 0x22FFFFFF) }
            setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
        }
        val pkg = Uri.parse("package:$packageName")
        col.addView(btn("1. Mic, contacts, files, Bluetooth") {
            val p = mutableListOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) p += listOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.POST_NOTIFICATIONS)
            else p += android.Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= 31) p += listOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
            requestPermissions(p.toTypedArray(), 1)
        })
        col.addView(btn("2. Show orb over other apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)) })
        col.addView(btn("3. All files access") {
            if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)) })
        col.addView(btn("4. Allow changing settings") { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)) })
        col.addView(btn("5. Usage access (activity report)") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        col.addView(btn("7. Accessibility (lets Sterling tap & toggle)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        col.addView(btn("6. Don't let Android kill Sterling") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        col.addView(btn("Start Sterling", true) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != 0) Toast.makeText(this, "Do step 1 first", Toast.LENGTH_SHORT).show()
            else if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "Do step 2 first", Toast.LENGTH_SHORT).show()
            else { startForegroundService(Intent(this, SterlingService::class.java)); finish() }
        })
        col.addView(btn("Stop Sterling") { stopService(Intent(this, SterlingService::class.java)) })
        setContentView(root)
    }
}
