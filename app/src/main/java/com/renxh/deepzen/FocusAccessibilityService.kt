package com.renxh.deepzen

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.content.Intent

class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != TYPE_WINDOW_STATE_CHANGED) return
        if (!isFocusActive()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val intent = Intent(this, GuardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!isFocusActive()) return super.onKeyEvent(event)
        if (event == null) return super.onKeyEvent(null)
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyEvent(event)
        }
    }

    override fun onInterrupt() {
    }

    private fun isFocusActive(): Boolean {
        val prefs = getSharedPreferences(GuardActivity.PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(GuardActivity.KEY_FOCUS_ACTIVE, false)
    }
}
