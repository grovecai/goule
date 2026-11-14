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
import java.io.FileDescriptor
import java.io.PrintWriter

class FreezeService : AccessibilityService() {
    private val store by lazy { RuleStore.get(this) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private var tickDueUptime: Long? = null
    private val tick = Runnable {
        val late = tickDueUptime?.let { SystemClock.uptimeMillis() - it } ?: 0
        if (late > 1_000) observe("tick lateByMs=$late")
        reconcile(foreground, "tick")
    }
    private val diagnostics by lazy { TimingDiagnostics.get(this) }
    private var lastSnapshotElapsed = 0L
    private var heartbeatDue = 0L
    private val heartbeat = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            snapshot("heartbeat lateByMs=${(now - heartbeatDue).coerceAtLeast(0)}")
            heartbeatDue = now + 30_000
            handler.postDelayed(this, 30_000)
        }
    }
    private val recents = RecentsTransition()
    private val leaveAfterLauncherEvent = Runnable {
        recents.reset()
        reconcile(null, "launcher settle")
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
    private val observations = ArrayDeque<String>()
    private var lastEventAt = 0L
    private var eventCount = 0L
    private var sampleCount = 0L
    private var rejectedSamples = 0L
    private var lastSampleAwake = 0L
    private var lastSampleElapsed = 0L

    /** Retains target identities and system window metadata; never text or window content. */
    private fun observe(message: String) {
        if (observations.size == 32) observations.removeFirst()
        observations.addLast("${SystemClock.uptimeMillis()}: $message")
        diagnostics.record(message)
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("connected=$connected consent=${store.consent} interactive=${power.isInteractive} locked=${keyguard.isKeyguardLocked}")
        writer.println("uptime=${SystemClock.uptimeMillis()} foreground=$foreground observingSince=$observedAtUptime overlay=$overlayRule")
        writer.println("events=$eventCount lastEventAt=$lastEventAt samples=$sampleCount rejected=$rejectedSamples lastAwake=$lastSampleAwake lastElapsed=$lastSampleElapsed")
        val now = store.now()
        for (rule in store.rules) writer.println("target=${rule.packageName} eligible=${rule.packageName in eligible} usedMs=${rule.usedMs} remainingMs=${rule.remainingUseMs(now)} cycleStart=${rule.cycleStart} blockedUntil=${rule.blockedUntil}")
        writer.println("Recent observations (uptime ms):")
        observations.forEach(writer::println)
        if ("--history" in args) diagnostics.dump(writer)
    }

    private fun snapshot(reason: String) {
        val now = store.now()
        diagnostics.record("state reason=$reason connected=$connected consent=${store.consent} interactive=${power.isInteractive} locked=${keyguard.isKeyguardLocked} foreground=$foreground observing=$observedAtUptime tickDue=$tickDueUptime events=$eventCount lastEvent=$lastEventAt samples=$sampleCount rejected=$rejectedSamples lastAwake=$lastSampleAwake lastElapsed=$lastSampleElapsed")
        for (rule in store.rules) diagnostics.record("rule target=${rule.packageName} eligible=${rule.packageName in eligible} usedMs=${rule.usedMs} remainingMs=${rule.remainingUseMs(now)} use=${rule.useMinutes} rest=${rule.blockMinutes} activeUse=${rule.activeUseMinutes} activeRest=${rule.activeBlockMinutes} cycleStart=${rule.cycleStart} cycleEnd=${rule.cycleEnd} accountedUntil=${rule.accountedUntil} blockedUntil=${rule.blockedUntil} ruleNow=$now")
        lastSnapshotElapsed = SystemClock.elapsedRealtime()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = clearForeground("screen ${intent.action}")
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            observe("package eligibility changed action=${intent.action}")
            reloadEligible()
            if (foreground !in eligible) clearForeground("package eligibility")
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
        observe("service connected")
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
            clearForeground("rule configuration")
        }
        clearForeground("service connected")
        handler.removeCallbacks(heartbeat)
        heartbeatDue = SystemClock.elapsedRealtime() + 30_000
        handler.postDelayed(heartbeat, 30_000)
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
        eventCount++
        lastEventAt = SystemClock.uptimeMillis()
        val category = when (val name = event.packageName?.toString()) {
            null -> "no-package"
            in eligible -> "target=$name class=${event.className} fullScreen=${event.isFullScreen}"
            packageName -> "self"
            in launchers -> "launcher"
            "com.android.systemui" -> "system-ui class=${event.className} fullScreen=${event.isFullScreen}"
            else -> "other"
        }
        observe("event $category type=${event.eventType} windowId=${event.windowId} eventTime=${event.eventTime} age=${lastEventAt - event.eventTime} watermark=$ignoreEventsBefore changes=${event.contentChangeTypes}")
        if (!store.consent || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventTime < ignoreEventsBefore
        ) {
            observe("event ignored consent=${store.consent} beforeWatermark=${event.eventTime < ignoreEventsBefore}")
            return
        }
        val name = event.packageName?.toString()
        // The overlay reports its own window change without taking input focus.
        if (name == packageName && event.className?.toString() == OVERLAY_EVENT_CLASS) {
            observe("own overlay event ignored")
            return
        }
        ignoreEventsBefore = event.eventTime
        // A floating SystemUI dialog can open/close without a new underlying-app event.
        // Keep its target and deadline, but still reconcile lock/screen state and expiry.
        if (isTransientSystemDialog(name, event.className?.toString(), event.isFullScreen)) {
            observe("floating system dialog; retain target")
            reconcile(foreground, "floating system dialog")
            return
        }
        handler.removeCallbacks(leaveAfterLauncherEvent)
        if (name != null && name in launchers) {
            val changes = event.contentChangeTypes
            val decision = recents.launcherEvent(
                name, event.eventTime,
                changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED != 0,
                changes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0,
            )
            observe("launcher decision=$decision")
            when (decision) {
                RecentsTransition.Decision.PASS -> reconcile(null, "launcher pass")
                RecentsTransition.Decision.DEFER ->
                    handler.postDelayed(leaveAfterLauncherEvent, RecentsTransition.SETTLE_MS)
                RecentsTransition.Decision.IGNORE -> Unit
            }
            return
        }
        val target = name?.takeIf { it in eligible }
        reconcile(target, "window $category")
        // Recents' trailing launcher events also arrive while an app is still usable.
        if (target != null) recents.targetResumed(event.eventTime) else recents.reset()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "UI task removed; saving usage while keeping the bound service")
        // Removing the UI task must preserve the system-bound accessibility session.
        reconcile(foreground, "task removed")
        store.flush()
        super.onTaskRemoved(rootIntent)
    }

    override fun onInterrupt() = clearForeground("service interrupted")
    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Accessibility service unbound")
        disconnect("service unbound")
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        disconnect("service destroyed")
        super.onDestroy()
    }

    private fun clearForeground(reason: String) {
        observe("clear foreground reason=$reason interactive=${power.isInteractive} locked=${keyguard.isKeyguardLocked}")
        handler.removeCallbacks(leaveAfterLauncherEvent)
        recents.reset()
        ignoreEventsBefore = SystemClock.uptimeMillis()
        observedAtUptime = null
        reconcile(null, reason)
    }

    private fun reconcile(next: String?, reason: String) {
        handler.removeCallbacks(tick)
        tickDueUptime = null
        val previous = foreground
        val now = store.now()
        val uptime = SystemClock.uptimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val interactive = connected && store.consent && power.isInteractive && !keyguard.isKeyguardLocked
        val delta = if (interactive) observedAtUptime?.let {
            sampleCount++
            lastSampleAwake = uptime - it
            lastSampleElapsed = elapsed - observedAtElapsed
            observedUsage(it, observedAtElapsed, uptime, elapsed)
        } ?: 0 else 0
        if (observedAtUptime != null && interactive && delta == 0L && lastSampleAwake > 0) {
            rejectedSamples++
            observe("sample rejected awake=$lastSampleAwake elapsed=$lastSampleElapsed")
        }
        val updated = store.rules.map { rule ->
            if (rule.packageName == previous) rule.record(delta, now) else rule.refresh(now)
        }
        val transitioned = updated.zip(store.rules).any { (new, old) ->
            new.blockedFrom != old.blockedFrom ||
                new.blockedUntil?.takeIf { it > now } != old.blockedUntil?.takeIf { it > now }
        }
        val target = if (interactive) next else null
        val saved = store.change(updated, force = transitioned || (previous != null && previous != target))
        if (previous != target || !saved) observe("reconcile $previous -> $target reason=$reason delta=$delta saved=$saved interactive=$interactive")
        val rule = store.rules.find { it.packageName == target }
        foreground = rule?.packageName
        observedAtUptime = uptime.takeIf { rule != null && !rule.isBlocked(now) && saved }
        observedAtElapsed = elapsed
        val deadline = rule?.blockedUntil?.takeIf { it > now }
        if (rule != null && deadline != null) showOverlay(rule, deadline, now) else hideOverlay()

        if (rule != null) {
            val delay = if (deadline != null && overlay == null) 1_000L else rule.nextCheckDelayMs(now)
            tickDueUptime = uptime + delay
            handler.postDelayed(tick, delay)
        }
        if (previous != foreground || transitioned || !saved || elapsed - lastSnapshotElapsed >= 30_000) {
            snapshot("reconcile $reason saved=$saved")
        }
    }

    private fun showOverlay(rule: Rule, deadline: Long, now: Long) {
        val key = rule.packageName to deadline
        if (key == overlayRule) return
        hideOverlay()
        val view = FreezeOverlayView(this, rule.label, deadlineLabel(deadline, now),
            onCloseTarget = {
                if (performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                    clearForeground("overlay open recents")
                    Toast.makeText(this, R.string.close_target_hint, Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, R.string.recents_failed, Toast.LENGTH_SHORT).show()
            },
            onGoHome = {
                if (performGlobalAction(GLOBAL_ACTION_HOME)) clearForeground("overlay home")
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
            observe("overlay attached target=${rule.packageName} deadline=$deadline")
        } catch (error: WindowManager.BadTokenException) {
            observe("overlay attach failed kind=${error.javaClass.simpleName}")
            Log.w(TAG, "Could not attach freeze overlay", error)
        }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        observe("overlay removed target=$overlayRule")
        overlay = null
        overlayRule = null
        windows.removeViewImmediate(view)
    }

    private fun disconnect(reason: String) {
        observe(reason)
        connected = false
        handler.removeCallbacks(heartbeat)
        clearForeground(reason)
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
