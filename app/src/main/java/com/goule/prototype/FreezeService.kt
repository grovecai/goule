package com.goule.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FreezeService : AccessibilityService() {
    private val store by lazy { RuleStore.get(this) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { reconcile(foreground) }
    private val recents = RecentsTransition()
    private val leaveAfterLauncherEvent = Runnable {
        recents.reset()
        reconcile(null)
    }
    private val launchers by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }
    private var foreground: String? = null
    private var eligible = emptySet<String>()
    private var observedAtUptime: Long? = null
    private var observedAtElapsed = 0L
    private var ignoreEventsBefore = 0L
    private var overlay: View? = null
    private var overlayRule: Pair<String, Long>? = null
    private var listening = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = clearForeground()
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadEligible()
            if (foreground !in eligible) clearForeground()
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
        if (!listening) {
            listen(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            })
            listen(packageReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            listening = true
        }
        connected = true
        if (store.consent) store.activate()
        reloadEligible()
        store.onConfigurationChanged = {
            reloadEligible()
            clearForeground()
        }
        clearForeground()
    }

    private fun listen(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    private fun reloadEligible() {
        val configured = store.rules.mapTo(mutableSetOf()) { it.packageName }
        eligible = eligibleTargets().map { it.packageName }.filterTo(mutableSetOf()) { it in configured }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!store.consent || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventTime < ignoreEventsBefore
        ) return
        val name = event.packageName?.toString()
        // The overlay reports its own window change without taking input focus.
        if (name == packageName && event.className?.toString() == OVERLAY_EVENT_CLASS) return
        ignoreEventsBefore = event.eventTime
        handler.removeCallbacks(leaveAfterLauncherEvent)
        if (name != null && name in launchers) {
            val changes = event.contentChangeTypes
            when (recents.launcherEvent(
                name, event.eventTime,
                changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED != 0,
                changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0,
            )) {
                RecentsTransition.Decision.PASS -> reconcile(null)
                RecentsTransition.Decision.DEFER ->
                    handler.postDelayed(leaveAfterLauncherEvent, RecentsTransition.SETTLE_MS)
                RecentsTransition.Decision.IGNORE -> Unit
            }
            return
        }
        val target = name?.takeIf { it in eligible }
        reconcile(target)
        if (target != null && overlay != null) recents.targetResumed(event.eventTime) else recents.reset()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "UI task removed; saving usage while keeping the bound service")
        // Removing the UI task must preserve the system-bound accessibility session.
        reconcile(foreground)
        store.flush()
        super.onTaskRemoved(rootIntent)
    }

    override fun onInterrupt() = clearForeground()
    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Accessibility service unbound")
        disconnect()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        disconnect()
        super.onDestroy()
    }

    private fun clearForeground() {
        handler.removeCallbacks(leaveAfterLauncherEvent)
        recents.reset()
        ignoreEventsBefore = SystemClock.uptimeMillis()
        observedAtUptime = null
        reconcile(null)
    }

    private fun reconcile(next: String?) {
        handler.removeCallbacks(tick)
        val previous = foreground
        val now = store.now()
        val uptime = SystemClock.uptimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val interactive = connected && store.consent && power.isInteractive && !keyguard.isKeyguardLocked
        val delta = if (interactive) observedAtUptime?.let {
            observedUsage(it, observedAtElapsed, uptime, elapsed)
        } ?: 0 else 0
        val updated = store.rules.map { rule ->
            if (rule.packageName == previous) rule.record(delta, now) else rule.refresh(now)
        }
        val transitioned = updated.zip(store.rules).any { (new, old) ->
            new.blockedFrom != old.blockedFrom ||
                new.blockedUntil?.takeIf { it > now } != old.blockedUntil?.takeIf { it > now }
        }
        val target = if (interactive) next else null
        val saved = store.change(updated, force = transitioned || (previous != null && previous != target))
        val rule = store.rules.find { it.packageName == target }
        foreground = rule?.packageName
        observedAtUptime = uptime.takeIf { rule != null && !rule.isBlocked(now) && saved }
        observedAtElapsed = elapsed
        val deadline = rule?.blockedUntil?.takeIf { it > now }
        if (rule != null && deadline != null) showOverlay(rule, deadline, now) else hideOverlay()

        if (rule != null) {
            val delay = if (deadline != null && overlay == null) 1_000L else rule.nextCheckDelayMs(now)
            handler.postDelayed(tick, delay)
        }
    }

    private fun showOverlay(rule: Rule, deadline: Long, now: Long) {
        val key = rule.packageName to deadline
        if (key == overlayRule) return
        hideOverlay()
        val view = FreezeOverlayView(this, rule.label, deadlineLabel(deadline, now),
            onCloseTarget = {
                if (performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                    clearForeground()
                    Toast.makeText(this, R.string.close_target_hint, Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, R.string.recents_failed, Toast.LENGTH_SHORT).show()
            },
            onGoHome = {
                if (performGlobalAction(GLOBAL_ACTION_HOME)) clearForeground()
                else Toast.makeText(this, R.string.home_failed, Toast.LENGTH_SHORT).show()
            },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            // Paint the whole display; the view keeps its content inside the safe insets.
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            windows.addView(view, params)
            overlay = view
            overlayRule = key
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Could not attach freeze overlay", error)
        }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        overlayRule = null
        windows.removeViewImmediate(view)
    }

    private fun disconnect() {
        connected = false
        clearForeground()
        store.flush()
        store.onConfigurationChanged = null
        if (listening) {
            unregisterReceiver(screenReceiver)
            unregisterReceiver(packageReceiver)
            listening = false
        }
    }

    companion object {
        private const val TAG = "GouleFreeze"
        private const val OVERLAY_EVENT_CLASS = FreezeOverlayView.EVENT_CLASS
        internal var connected = false
            private set
    }
}
