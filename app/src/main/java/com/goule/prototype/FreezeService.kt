package com.goule.prototype

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.LinearLayout
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
        // Removing the UI task must preserve the system-bound accessibility session.
        reconcile(foreground)
        store.flush()
        super.onTaskRemoved(rootIntent)
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
            new.blockedUntil != old.blockedUntil
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
            val delay = if (deadline != null && overlay == null) 1_000L else if (deadline != null) deadline - now else
                rule.remainingUseMs(now).coerceIn(1, 1_000)
            handler.postDelayed(tick, delay)
        }
    }

    private fun showOverlay(rule: Rule, deadline: Long, now: Long) {
        val key = rule.packageName to deadline
        if (key == overlayRule) return
        hideOverlay()
        val paper = Color.rgb(255, 240, 220)
        val backdrop = Color.rgb(32, 17, 37)
        val outline = Color.rgb(50, 29, 43)
        val view = object : ScrollView(this) {
            override fun getAccessibilityClassName(): CharSequence = OVERLAY_EVENT_CLASS
        }.apply {
            isFillViewport = true
            isClickable = true
            setBackgroundColor(backdrop)
            setOnApplyWindowInsetsListener { root, insets ->
                val bottom = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
                root.setPadding(0, 0, 0, bottom)
                insets
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
        }
        view.addView(content)
        val comic = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(comic, LinearLayout.LayoutParams(-1, -2, 1f))
        val height = resources.configuration.screenHeightDp
        val shortScreen = height < 700
        comic.addView(ImageView(this).apply {
            setImageResource(R.drawable.discipline_elder)
            contentDescription = "戒律长老怒视前方，指向屏幕大声喝止"
            adjustViewBounds = !shortScreen
            scaleType = if (shortScreen) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(-1, if (shortScreen) dp((height * .30f).toInt()) else -2))
        val speech = FrameLayout(this).apply { clipChildren = false }
        val dialogue = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(17))
            background = surface(paper, 5).apply { setStroke(dp(3), outline) }
        }
        speech.addView(dialogue, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        speech.addView(View(this).apply {
            background = surface(paper, 0)
            rotation = 45f
        }, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.START).apply {
            marginStart = dp((resources.configuration.screenWidthDp * .34f).toInt())
            topMargin = dp(3)
        })
        val dialogueWidth = resources.configuration.screenWidthDp - 56
        val headlineSize = (dialogueWidth * .19f).coerceIn(40f, 64f)
        val appLineSize = (dialogueWidth * .095f).coerceIn(20f, 33f)
        dialogue.addView(copyText("够了！", headlineSize, Color.rgb(172, 48, 32), true).apply {
            isAccessibilityHeading = true
            letterSpacing = -.055f
        })
        val prefix = "别刷 "
        val warning = SpannableString("$prefix${rule.label} 了！").apply {
            setSpan(ForegroundColorSpan(0xff70238a.toInt()), prefix.length,
                prefix.length + rule.label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        dialogue.addView(copyText(warning, appLineSize, outline, true).apply {
            letterSpacing = -.055f
            setLineSpacing(0f, 1.13f)
        })
        dialogue.addView(copyText("再刷下去，你还飞不飞升了？", 18f, outline, true).apply {
            setPadding(0, dp(8), 0, 0)
        })
        comic.addView(speech, LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = -dp(18)
        })
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        content.addView(footer)
        footer.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(copyText("解禁时刻", 14f, Color.rgb(232, 190, 176), true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(copyText(deadlineLabel(deadline, now), 24f, paper, true))
        })
        footer.addView(Button(this).apply {
            setText(R.string.close_target)
            setTextColor(outline)
            isAllCaps = false
            stateListAnimator = null
            minHeight = dp(44)
            backgroundTintList = null
            background = surface(paper, 9)
            setOnClickListener {
                if (performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                    clearForeground()
                    Toast.makeText(this@FreezeService, R.string.close_target_hint, Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@FreezeService, R.string.recents_failed, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        footer.addView(Button(this).apply {
            setText(R.string.go_home)
            setTextColor(paper)
            isAllCaps = false
            stateListAnimator = null
            minHeight = dp(44)
            backgroundTintList = null
            background = surface(backdrop, 9, Color.rgb(143, 107, 94))
            setOnClickListener {
                if (performGlobalAction(GLOBAL_ACTION_HOME)) clearForeground()
                else Toast.makeText(this@FreezeService, R.string.home_failed, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            setFitInsetsIgnoringVisibility(true)
        }
        try {
            windows.addView(view, params)
            overlay = view
            overlayRule = key
            if (ValueAnimator.areAnimatorsEnabled()) {
                speech.translationY = -dp(12).toFloat()
                speech.animate().translationY(0f).setDuration(180).start()
            }
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
        private const val OVERLAY_EVENT_CLASS = "com.goule.prototype.FreezeOverlay"
        internal var connected = false
            private set
    }
}
