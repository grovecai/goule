package com.goule.prototype

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** Replays the OPPO return sequence through the production service in the disposable app. */
internal object ForegroundTimingChecks {
    fun run(context: Context, onMain: (() -> Unit) -> Unit) {
        check(context.packageName == "com.goule.uiverify")
        val service = FreezeService()
        lateinit var store: RuleStore
        val target = "fixture.timing"
        val launcher = checkNotNull(context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        )).activityInfo.packageName
        var attached = false
        try {
            onMain {
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
                    isAccessible = true
                }.invoke(service, context)
                store = RuleStore.get(context)
                store.approve()
                check(store.configure(listOf(Rule(target, "计时回归", 10, 120, store.now()))))
                attached = true
                FreezeService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }
                    .invoke(service)
                // A synthetic configured package keeps the test independent of installed third-party apps.
                FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                    .set(service, setOf(target))
                event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                event(service, target)
            }
            SystemClock.sleep(600)
            onMain { event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) }
            SystemClock.sleep(1_400)
            onMain {
                val used = store.rules.single().usedMs
                check(used >= 1_000) { "Unblocked target stopped counting after recents closed: usedMs=$used" }
                event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
            }
            val stopped = store.rules.single().usedMs
            SystemClock.sleep(1_200)
            onMain {
                check(store.rules.single().usedMs == stopped) { "Leaving for recents kept counting" }
            }
        } finally {
            if (attached) onMain { service.onDestroy() }
        }
    }

    private fun event(service: FreezeService, name: String, changes: Int = 0) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = name
            eventTime = SystemClock.uptimeMillis()
            contentChangeTypes = changes
        }
        service.onAccessibilityEvent(event)
        event.recycle()
    }
}
