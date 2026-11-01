package com.sterling.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View

class OrbView(c: Context) : View(c) {
    enum class S { IDLE, LISTENING, THINKING }
    var state = S.IDLE
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        ValueAnimator.ofFloat(0f, 6.2832f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }.start()
    }
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val base = width * 0.3f
        val (a, b) = when (state) {
            S.IDLE -> 0xFF6C8CFF.toInt() to 0xFF2B2F6B.toInt()
            S.LISTENING -> 0xFF5CFFD6.toInt() to 0xFF0B6E5C.toInt()
            S.THINKING -> 0xFFFFB86C.toInt() to 0xFF7A3E00.toInt()
        }
        val amp = if (state == S.LISTENING) 0.18f else 0.06f
        val r = base * (1f + amp * Math.sin(t.toDouble()).toFloat())
        p.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(a and 0x00FFFFFF or 0x55000000, 0x00000000), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.6f, p)
        p.shader = RadialGradient(cx - r * .3f, cy - r * .3f, r, a, b, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
    }
}
