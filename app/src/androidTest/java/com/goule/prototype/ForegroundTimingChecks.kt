package com.goule.prototype

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.io.FileDescriptor
import java.io.PrintWriter
import java.io.StringWriter

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
                dialog(service)
                checkForeground(service, null)
                event(service, target)
            }
            val beforeDialogs = store.rules.single().usedMs
            repeat(3) {
                onMain { dialog(service) }
                SystemClock.sleep(400)
            }
            onMain {
                dialog(service)
                checkForeground(service, target)
                check(store.rules.single().usedMs >= beforeDialogs + 1_000) {
                    "Volume dialog stopped usage sampling"
                }
                event(service, "com.android.systemui", className = "android.widget.FrameLayout", fullScreen = true)
                checkForeground(service, null)
            }
            val afterShade = store.rules.single().usedMs
            SystemClock.sleep(1_200)
            onMain {
                dialog(service)
                check(store.rules.single().usedMs == afterShade) { "Shade exit kept counting" }
                checkForeground(service, null)

            }
            investigateStopPaths(context, service, store, target, onMain)
            onMain {
                val now = store.now()
                check(store.configure(listOf(Rule(target, "禁用回归", 10, 120, now,
                    cycleStart = now, usedMs = 10 * MINUTE, blockedFrom = now))))
                FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                    .set(service, setOf(target))
                event(service, target)
                val blocked = store.rules.single()
                repeat(4) {
                    dialog(service)
                    checkForeground(service, target)
                    check(store.rules.single() == blocked) { "Volume dialog changed blocked usage/deadline" }
                }
                event(service, "com.android.settings", className = "android.app.Dialog")
                checkForeground(service, null)
                dialog(service)
                checkForeground(service, null)
                check(store.rules.single() == blocked)

                // Screen changes clear the foreground before late dialog dismissal events arrive.
                event(service, target)
                val receiver = FreezeService::class.java.getDeclaredField("screenReceiver").apply {
                    isAccessible = true
                }.get(service) as android.content.BroadcastReceiver
                receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
                dialog(service)
                checkForeground(service, null)
                check(store.rules.single() == blocked)
            }
        } finally {
            if (attached) onMain { service.onDestroy() }
        }
    }

    /** These are candidate mechanisms, not a claim that ColorOS emitted them in the lost trace. */
    private fun investigateStopPaths(context: Context, service: FreezeService, store: RuleStore,
        target: String, onMain: (() -> Unit) -> Unit,
    ) {
        onMain { event(service, target, className = "fixture.PortraitActivity") }
        val beforeRotation = store.rules.single().usedMs
        SystemClock.sleep(600)
        onMain { event(service, target, className = "fixture.ShortSeriesLandActivity", fullScreen = true) }
        SystemClock.sleep(600)
        onMain {
            // Same-package rotation and stale non-target events must not stop an active target.
            event(service, "fixture.other", time = SystemClock.uptimeMillis() - 10_000)
            event(service, target, className = "fixture.ShortSeriesLandActivity", fullScreen = true)
            checkForeground(service, target)
            check(store.rules.single().usedMs >= beforeRotation + 1_000) { "Rotation lost usage" }
            // A non-fullscreen unknown SystemUI window currently clears foreground too.
            event(service, "com.android.systemui", className = "android.widget.FrameLayout")
            checkForeground(service, null)
        }
        val afterUnknownWindow = store.rules.single().usedMs
        SystemClock.sleep(1_200)
        onMain {
            check(store.rules.single().usedMs == afterUnknownWindow) {
                "Candidate stop path did not stay stopped without another target event"
            }
            event(service, target)
        }
        SystemClock.sleep(600)
        onMain {
            event(service, target)
            check(store.rules.single().usedMs >= afterUnknownWindow + 500) { "Target event did not resume timing" }
            event(service, null)
            checkForeground(service, null)
            event(service, target)
            service.onInterrupt()
            checkForeground(service, null)
            event(service, target)
            val receiver = FreezeService::class.java.getDeclaredField("screenReceiver").apply {
                isAccessible = true
            }.get(service) as android.content.BroadcastReceiver
            receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
            event(service, target, time = SystemClock.uptimeMillis() - 10_000)
            checkForeground(service, null)
            event(service, target)
            checkForeground(service, target)
        }
        onMain {
            val beforeStall = store.rules.single().usedMs
            // Simulate a delayed main-thread callback: it must reject this unconfirmed interval.
            SystemClock.sleep(2_200)
            event(service, target)
            check(store.rules.single().usedMs == beforeStall) { "Long callback stall was backfilled" }
            repeat(40) { event(service, target, className = "fixture.LandscapeActivity") }
            val history = dump(service, history = true)
            check("sample rejected" in history && "reason=service interrupted" in history &&
                "class=fixture.PortraitActivity" in history && "class=fixture.ShortSeriesLandActivity" in history &&
                "Persistent timing diagnostics" in history && "process start version=" in history) {
                "Persistent trace could not explain transitions beyond the 32-event memory buffer: $history"
            }
        }
    }

    private fun dialog(service: FreezeService) {
        event(service, "com.android.systemui", className = "android.app.Dialog")
        // Exact class/fullScreen pair captured on OPPO PLJ110 / ColorOS V16.1.0.
        event(service, "com.android.systemui",
            className = "com.oplus.systemui.volume.view.OplusVolumeDialogView\$CustomDialog", fullScreen = true)
    }

    private fun checkForeground(service: FreezeService, expected: String?) {
        val text = dump(service)
        check("foreground=$expected observingSince=" in text) {
            "Unexpected foreground after window transition: $text"
        }
    }

    private fun dump(service: FreezeService, history: Boolean = false): String {
        val output = StringWriter()
        FreezeService::class.java.getDeclaredMethod("dump", FileDescriptor::class.java,
            PrintWriter::class.java, Array<String>::class.java).apply { isAccessible = true }
            .invoke(service, FileDescriptor.out, PrintWriter(output), if (history) arrayOf("--history") else emptyArray<String>())
        return output.toString()
    }

    private fun event(service: FreezeService, name: String?, changes: Int = 0,
        className: String? = null, fullScreen: Boolean = false, time: Long = SystemClock.uptimeMillis(),
    ) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = name
            eventTime = time
            contentChangeTypes = changes
            this.className = className
            isFullScreen = fullScreen
        }
        service.onAccessibilityEvent(event)
        event.recycle()
    }
}
