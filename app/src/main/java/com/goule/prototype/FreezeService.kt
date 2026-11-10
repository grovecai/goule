package com.goule.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreezeService : AccessibilityService() {
    private val preferences by lazy { prototypeSettings() }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { reconcile() }
    private var session = PrototypeSession()
    private var target: String? = null
    private var targetVisible = false
    private var ignoreEventsBefore = 0L
    private var overlay: View? = null
    private var listening = false

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TARGET_KEY || key == CONSENT_KEY) reloadTarget()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // After either screen transition, require fresh evidence of the foreground.
            clearForeground()
        }
    }

    override fun onServiceConnected() {
        if (!listening) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            preferences.registerOnSharedPreferenceChangeListener(settingsListener)
            listening = true
        }
        connected = true
        reloadTarget()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val selected = target ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventTime < ignoreEventsBefore
        ) return

        // The overlay itself reports a window change even though it cannot take input focus.
        if (event.packageName?.toString() == packageName &&
            event.className?.toString() == OVERLAY_EVENT_CLASS
        ) return

        ignoreEventsBefore = event.eventTime
        targetVisible = selected == event.packageName?.toString()
        reconcile()
    }

    override fun onInterrupt() = clearForeground()

    override fun onUnbind(intent: Intent): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun reloadTarget() {
        val selected = preferences.getString(TARGET_KEY, null)
        target = selected?.takeIf {
            preferences.getBoolean(CONSENT_KEY, false) &&
                eligibleTargets().any { app -> app.packageName == selected }
        }
        session = PrototypeSession()
        clearForeground()
    }

    private fun clearForeground() {
        ignoreEventsBefore = SystemClock.uptimeMillis()
        targetVisible = false
        reconcile()
    }

    private fun reconcile() {
        handler.removeCallbacks(tick)
        val now = SystemClock.elapsedRealtime()
        if (!power.isInteractive || keyguard.isKeyguardLocked) targetVisible = false
        val deadline = session.observe(targetVisible, now)
        val frozenUntil = session.frozenUntil
        if (targetVisible && frozenUntil != null) {
            showOverlay(frozenUntil - now)
        } else {
            hideOverlay()
        }
        if (deadline != null) {
            handler.postDelayed(tick, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        }
    }

    private fun showOverlay(remainingMs: Long) {
        if (overlay != null) return

        val locale = Locale.getDefault()
        val skeleton = if (DateFormat.is24HourFormat(this)) "Hms" else "hms"
        val formatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        val recovery = formatter.format(Date(System.currentTimeMillis() + remainingMs))
        val padding = (24 * resources.displayMetrics.density).toInt()
        val view = object : LinearLayout(this) {
            override fun getAccessibilityClassName(): CharSequence = OVERLAY_EVENT_CLASS
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.WHITE)
            isClickable = true
            addView(TextView(this@FreezeService).apply {
                setText(R.string.freeze_title)
                textSize = 24f
            })
            addView(TextView(this@FreezeService).apply {
                text = getString(R.string.freeze_until, recovery)
            })
            addView(Button(this@FreezeService).apply {
                setText(R.string.go_home)
                setOnClickListener {
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                        clearForeground()
                    } else {
                        Toast.makeText(this@FreezeService, R.string.home_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        )
        try {
            windows.addView(view, params)
            overlay = view
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Could not attach freeze overlay", error)
        }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        windows.removeViewImmediate(view)
    }

    private fun disconnect() {
        connected = false
        handler.removeCallbacks(tick)
        hideOverlay()
        target = null
        targetVisible = false
        session = PrototypeSession()
        if (listening) {
            unregisterReceiver(screenReceiver)
            preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
            listening = false
        }
    }

    companion object {
        private const val TAG = "GouleFreeze"
        private const val OVERLAY_EVENT_CLASS = "com.goule.prototype.FreezeOverlay"
        internal var connected = false
            private set
    }
}
