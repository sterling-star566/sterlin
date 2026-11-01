package com.sterling.app

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Lets Sterling press Back/Home, tap things on screen, flip switches and type, when you ask. */
class SterlingAccess : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; return super.onUnbind(i) }

    companion object {
        @Volatile var instance: SterlingAccess? = null

        fun press(what: String): Boolean {
            val s = instance ?: return false
            val a = when (what) {
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                "screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                "power" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                else -> return false
            }
            return s.performGlobalAction(a)
        }

        private fun clickUp(start: AccessibilityNodeInfo?): Boolean {
            var x = start
            while (x != null) {
                if (x.isClickable && x.isEnabled) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                x = x.parent
            }
            return false
        }

        fun tap(text: String): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            for (n in root.findAccessibilityNodeInfosByText(text)) if (clickUp(n)) return true
            return false
        }

        private fun find(n: AccessibilityNodeInfo, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (test(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c, test)?.let { return it } }
            return null
        }

        /** Sets the first switch on screen to on/off (skips it if it is already right). */
        fun setSwitch(on: Boolean): String {
            val root = instance?.rootInActiveWindow ?: return "I can't see the screen"
            val sw = find(root) { it.isCheckable && (it.className?.contains("Switch") == true || it.className?.contains("Toggle") == true) }
                ?: return "I couldn't find the switch"
            if (sw.isChecked == on) return "already ${if (on) "on" else "off"}"
            return if (clickUp(sw)) (if (on) "turned on" else "turned off") else "I couldn't tap it"
        }

        fun scroll(down: Boolean): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val n = find(root) { it.isScrollable } ?: return false
            return n.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }

        fun typeText(text: String): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        }
    }
}
