package com.goule.prototype

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import java.io.FileDescriptor
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Proxy

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
                // This is an event replay, not a system-bound AccessibilityService. Capture window
                // attachment locally so its real show/hide/key logic can be checked without a token.
                val actualWindows = context.getSystemService(WindowManager::class.java)
                val replayWindows = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
                    arrayOf(WindowManager::class.java)) { _, method, args ->
                    when (method.name) {
                        "addView", "removeView", "removeViewImmediate", "updateViewLayout" -> null
                        else -> method.invoke(actualWindows, *(args ?: emptyArray()))
                    }
                } as WindowManager
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
                    isAccessible = true
                }.invoke(service, context)
                FreezeService::class.java.getDeclaredField("windows\$delegate").apply { isAccessible = true }
                    .set(service, lazyOf(replayWindows))
                // Pin the generic stop-path checks; the captured sequence explicitly injects OPPO below.
                FreezeService::class.java.getDeclaredField("systemWindows").apply { isAccessible = true }
                    .set(service, SystemWindowPolicy("Google"))
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
            replayColorOsContainer(service, store, target, onMain)
            onMain {
                val now = store.now()
                check(store.configure(listOf(Rule(target, "禁用回归", 10, 120, now,
                    cycleStart = now, usedMs = 10 * MINUTE, blockedFrom = now))))
                FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                    .set(service, setOf(target))
                val appearance = BlockAppearanceStore.get(context)
                check(appearance.setMode(BlockThemeMode.RANDOM))
                check(appearance.setPool(setOf("kitchen")))
                event(service, target)
                checkOverlayTheme(service, "kitchen")
            }
            replayRecentsOrdering(service, store, target, launcher, onMain)
            val blockedBeforeDeparture = store.rules.single()
            onMain {
                event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                event(service, target, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
                    className = "fixture.CustomRelativeLayout")
                checkForeground(service, null)
                checkNoOverlay(service)
            }
            repeat(3) {
                onMain {
                    FreezeService::class.java.getDeclaredMethod("onRecentsOpened").apply {
                        isAccessible = true
                    }.invoke(service)
                    // Old activity callbacks during the global-action animation must not reattach.
                    event(service, target, className = "fixture.MainActivity", fullScreen = true)
                    checkForeground(service, null)
                    checkNoOverlay(service)
                    event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                    // Captured Douyin card-entry sequence. This is departure, not a resume.
                    event(service, target, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
                        className = "fixture.CustomRelativeLayout")
                    event(service, launcher)
                    checkForeground(service, null)
                    checkNoOverlay(service)
                }
                SystemClock.sleep(1_700)
                onMain {
                    checkForeground(service, null)
                    checkNoOverlay(service)
                    // A later actual return must still retain the launcher pane's closing state.
                    if (it % 2 == 0) event(service, target, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                    event(service, target, className = "fixture.MainActivity", fullScreen = true)
                    checkForeground(service, target)
                    checkOverlayTheme(service, "kitchen")
                    event(service, launcher)
                    // A late old pane disappearance must not cancel pending launcher reconciliation.
                    event(service, target, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
                }
                SystemClock.sleep(450)
                onMain {
                    event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
                    event(service, launcher)
                    checkForeground(service, target)
                    checkOverlayTheme(service, "kitchen")
                    check(store.rules.single() == blockedBeforeDeparture)
                }
            }
            // Captured ColorOS sequence: target, unmarked launcher, own overlay,
            // then pane disappearance ~450 ms later. The old 120 ms fallback removed it.
            val blockedBeforeRecents = store.rules.single()
            repeat(3) {
                onMain {
                    event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                    event(service, target)
                    event(service, launcher)
                }
                SystemClock.sleep(450)
                onMain {
                    checkForeground(service, target)
                    checkOverlayTheme(service, "kitchen")
                    event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
                    event(service, launcher)
                }
                SystemClock.sleep(200)
                onMain {
                    checkForeground(service, target)
                    checkOverlayTheme(service, "kitchen")
                    check(store.rules.single() == blockedBeforeRecents) { "Recents changed blocked usage/deadline" }
                }
            }
            onMain {
                event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                event(service, target)
                event(service, launcher)
                event(service, target, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
            }
            SystemClock.sleep(1_700)
            onMain {
                checkForeground(service, null)
                event(service, target)
                // A preference change or repeated tick must not replace an attached theme/deadline.
                val appearance = BlockAppearanceStore.get(context)
                check(appearance.setFixed("exam"))
                val blocked = store.rules.single()
                repeat(4) {
                    dialog(service)
                    checkOverlayTheme(service, "kitchen")
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
                checkOverlayTheme(service, "exam")
                val receiver = FreezeService::class.java.getDeclaredField("screenReceiver").apply {
                    isAccessible = true
                }.get(service) as android.content.BroadcastReceiver
                receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
                dialog(service)
                checkForeground(service, null)
                check(store.rules.single() == blocked)
            }
            checkOverlayLifecycle(service, store, target, launcher, onMain)
        } finally {
            if (attached) onMain {
                service.onDestroy()
                BlockAppearanceStore.get(context).setFixed("elder")
            }
        }
    }

    private fun replayRecentsOrdering(service: FreezeService, store: RuleStore, target: String,
        launcher: String, onMain: (() -> Unit) -> Unit,
    ) {
        val blocked = store.rules.single()
        onMain {
            // 15:19:44: pane appeared, unrelated window, launcher updates, actual target.
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
            event(service, "fixture.intervening")
            checkForeground(service, null)
            checkNoOverlay(service)
            event(service, launcher)
            event(service, target, className = "fixture.DetailFeedActivity", fullScreen = true)
            checkOverlayTheme(service, "kitchen")
        }
        SystemClock.sleep(450)
        onMain {
            event(service, launcher)
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
            event(service, launcher)
            checkForeground(service, target)
            checkOverlayTheme(service, "kitchen")
            check(store.rules.single() == blocked)
            // A real app/settings switch after resuming must still clear everything.
            event(service, "com.android.settings")
            checkForeground(service, null)
            checkNoOverlay(service)
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
            checkForeground(service, null)
            checkNoOverlay(service)

            // 15:21:16 / Douyin 14:55:18: the pane closes BEFORE the target event.
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
            val closed = SystemClock.uptimeMillis()
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED, time = closed)
            checkForeground(service, null)
            checkNoOverlay(service)
            event(service, target, className = "fixture.DetailFeedActivity", fullScreen = true, time = closed + 20)
            event(service, launcher, time = closed + 25)
            checkForeground(service, target)
            checkOverlayTheme(service, "kitchen")
            check(store.rules.single() == blocked)
        }
        SystemClock.sleep(200)
        onMain {
            event(service, launcher)
            checkForeground(service, null)
            checkNoOverlay(service)
            event(service, launcher, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
            event(service, "fixture.intervening")
            checkNoOverlay(service)
        }
        SystemClock.sleep(1_700)
        onMain {
            // Old interrupted transition metadata must not protect a later unrelated launch.
            event(service, target)
            event(service, launcher)
            checkForeground(service, null)
            checkNoOverlay(service)
            check(store.rules.single() == blocked)
        }
    }

    private fun checkOverlayLifecycle(service: FreezeService, store: RuleStore, target: String,
        launcher: String, onMain: (() -> Unit) -> Unit,
    ) {
        val otherTarget = "fixture.other.timing"
        onMain {
            val now = store.now()
            check(store.configure(listOf(
                Rule(target, "即将用满", 1, 15, now - 2 * MINUTE,
                    cycleStart = now, usedMs = MINUTE - 300),
            )))
            FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                .set(service, setOf(target))
            event(service, target)
            checkNoOverlay(service)
        }
        SystemClock.sleep(650)
        onMain {
            checkOverlayTheme(service, "exam")
            check(store.rules.single().usedMs == MINUTE)
            check(store.rules.single().isBlocked(store.now()))
        }
        onMain {
            val now = store.now()
            check(store.configure(listOf(
                Rule(target, "禁用 A", 1, 15, now - 2 * MINUTE,
                    cycleStart = now, usedMs = MINUTE, blockedFrom = now),
                Rule(otherTarget, "可刷 B", 1, 15, now - 2 * MINUTE),
            )))
            FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                .set(service, setOf(target, otherTarget))
            event(service, target)
            checkOverlayTheme(service, "exam")
            val blocked = store.rules.first()
            // Unconfigured apps, settings and Home must each remove the blocked overlay.
            for (exit in listOf("fixture.unconfigured", "com.android.settings", launcher)) {
                event(service, exit)
                checkForeground(service, null)
                checkNoOverlay(service)
                event(service, target)
                checkOverlayTheme(service, "exam")
                check(store.rules.first() == blocked)
            }
            event(service, otherTarget)
            checkForeground(service, otherTarget)
            checkNoOverlay(service)
            check(store.rules.first() == blocked)
            // Both directions must select the correct rule rather than retain another app's overlay.
            event(service, target)
            checkOverlayTheme(service, "exam")
            event(service, otherTarget)
            checkNoOverlay(service)
            val nowExpiry = store.now()
            check(store.configure(listOf(
                Rule(target, "到期 A", 1, 15, nowExpiry - 20 * MINUTE,
                    cycleStart = nowExpiry - 16 * MINUTE + 800, usedMs = MINUTE,
                    blockedFrom = nowExpiry - MINUTE),
                Rule(otherTarget, "仍禁用 B", 1, 15, nowExpiry - 2 * MINUTE,
                    cycleStart = nowExpiry, usedMs = MINUTE, blockedFrom = nowExpiry),
            )))
            FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                .set(service, setOf(target, otherTarget))
            event(service, target)
            checkOverlayTheme(service, "exam")
        }
        SystemClock.sleep(1_100)
        onMain {
            checkForeground(service, target)
            checkNoOverlay(service)
            check(!store.rules.first().isBlocked(store.now()))
            event(service, otherTarget)
            checkOverlayTheme(service, "exam")
            check(store.rules.last().isBlocked(store.now()))
            service.onInterrupt()
            checkForeground(service, null)
            checkNoOverlay(service)
            // Disconnected/cleared state cannot be revived by a pane disappearing.
            event(service, otherTarget, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
            checkForeground(service, null)
            checkNoOverlay(service)
        }
    }

    private fun checkNoOverlay(service: FreezeService) {
        check(FreezeService::class.java.getDeclaredField("overlay").apply { isAccessible = true }
            .get(service) == null) { "Recents was covered by an overlay" }
    }

    private fun checkOverlayTheme(service: FreezeService, id: String) {
        val view = FreezeService::class.java.getDeclaredField("overlay").apply { isAccessible = true }
            .get(service) as FreezeOverlayView
        val appearance = FreezeOverlayView::class.java.getDeclaredField("appearance").apply { isAccessible = true }
            .get(view) as BlockAppearance
        check(appearance.theme.id == id) { "Service overlay did not keep the expected theme $id" }
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

    /** 2026-11-16: shade -> target -> volume -> SeedlingCard, with no target recovery event. */
    private fun replayColorOsContainer(service: FreezeService, store: RuleStore,
        target: String, onMain: (() -> Unit) -> Unit,
    ) {
        val policyField = FreezeService::class.java.getDeclaredField("systemWindows").apply { isAccessible = true }
        val originalPolicy = policyField.get(service)
        val other = "fixture.independent"
        fun configure(blocked: Boolean = false) {
            val now = store.now()
            check(store.configure(listOf(
                Rule(target, "ColorOS 回归", 5, 115, now, cycleStart = now,
                    usedMs = if (blocked) 5 * MINUTE else 0, blockedFrom = now.takeIf { blocked }),
                Rule(other, "独立额度", 5, 115, now),
            )))
            FreezeService::class.java.getDeclaredField("eligible").apply { isAccessible = true }
                .set(service, setOf(target, other))
        }
        fun container(changes: Int = 0, fullScreen: Boolean = false) = event(service,
            "com.android.systemui", changes, "android.widget.FrameLayout", fullScreen)
        try {
            onMain {
                policyField.set(service, SystemWindowPolicy("OPPO"))
                configure()
                event(service, target)
                container(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                checkForeground(service, null)
                container()
                container(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED)
                checkForeground(service, null)
            }
            val paused = store.rules.first().usedMs
            SystemClock.sleep(1_200)
            onMain {
                check(store.rules.first().usedMs == paused) { "Shade container update resumed timing" }
                event(service, target)
                dialog(service)
            }
            val resumed = store.rules.first().usedMs
            SystemClock.sleep(300)
            onMain {
                container()
                checkForeground(service, target)
            }
            SystemClock.sleep(1_300)
            onMain {
                // No new target event: keep sampling through the passive ColorOS root update.
                container()
                checkForeground(service, target)
                check(store.rules.first().usedMs >= resumed + 1_000) { "SeedlingCard stopped sampling" }
                check(store.rules.last().usedMs == 0L) { "Other app quota changed" }
                container(fullScreen = true)
                checkForeground(service, null)
                container()
                checkForeground(service, null)
                event(service, target)
                event(service, "com.android.settings", className = "android.widget.FrameLayout")
                container()
                checkForeground(service, null)
                event(service, target)
                service.onInterrupt()
                container()
                checkForeground(service, null)

                configure(blocked = true)
                event(service, target)
                val blocked = store.rules.first()
                check(blocked.blockedUntil != null)
                repeat(3) { container(); checkForeground(service, target) }
                check(store.rules.first() == blocked) { "Passive container altered blocked quota/deadline" }
                check(store.rules.last().usedMs == 0L)
                container(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED)
                container()
                checkForeground(service, null)
                check(store.rules.first() == blocked)
                check("passive ColorOS system container" in dump(service, history = true))
            }
        } finally {
            onMain { policyField.set(service, originalPolicy) }
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
