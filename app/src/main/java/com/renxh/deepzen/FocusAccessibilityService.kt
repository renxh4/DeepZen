package com.renxh.deepzen

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

class FocusAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_START_FOCUS = "com.renxh.deepzen.action.START_FOCUS"
        const val ACTION_STOP_FOCUS = "com.renxh.deepzen.action.STOP_FOCUS"
        private const val DEFAULT_DURATION_SECONDS = 5 * 60
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var timeTextView: TextView? = null
    private var isFocusing = false
    private var remainingSeconds = 0
    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isFocusing) return
            remainingSeconds -= 1
            if (remainingSeconds <= 0) {
                stopFocus()
                return
            }
            updateTimeText()
            handler.postDelayed(this, 1000)
        }
    }

    private val focusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START_FOCUS -> {
                    val seconds = intent.getIntExtra("seconds", DEFAULT_DURATION_SECONDS)
                    startFocus(seconds)
                }
                ACTION_STOP_FOCUS -> {
                    stopFocus()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ACTION_START_FOCUS)
            addAction(ACTION_STOP_FOCUS)
        }
        registerReceiver(focusReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isFocusing) return
        showOverlay()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!isFocusing) return super.onKeyEvent(event)
        if (event == null) return super.onKeyEvent(null)
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)
        showOverlay()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyEvent(event)
        }
    }

    override fun onInterrupt() {
        stopFocus()
    }

    override fun onDestroy() {
        unregisterReceiver(focusReceiver)
        stopFocus()
        windowManager = null
        super.onDestroy()
    }

    private fun startFocus(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        isFocusing = true
        remainingSeconds = totalSeconds
        showOverlay()
        updateTimeText()
        handler.removeCallbacks(timerRunnable)
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun stopFocus() {
        isFocusing = false
        handler.removeCallbacks(timerRunnable)
        removeOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        val view = FrameLayout(this)
        view.setBackgroundColor(Color.BLACK)
        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val timeView = TextView(this)
        timeView.textSize = 40f
        timeView.setTextColor(Color.WHITE)
        timeView.gravity = Gravity.CENTER
        timeTextView = timeView
        view.addView(
            timeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val exitView = TextView(this)
        exitView.text = "紧急退出"
        exitView.textSize = 16f
        exitView.setTextColor(Color.WHITE)
        exitView.gravity = Gravity.CENTER
        exitView.setPadding(0, 0, 0, 80)
        exitView.setOnClickListener {
            stopFocus()
        }
        val exitParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        view.addView(exitView, exitParams)
        wm.addView(view, params)
        overlayView = view
    }

    private fun removeOverlay() {
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            wm.removeView(view)
            overlayView = null
            timeTextView = null
        }
    }

    private fun updateTimeText() {
        timeTextView?.text = formatSeconds(remainingSeconds)
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
