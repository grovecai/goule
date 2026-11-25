package com.goule.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView

/** Runs only in a disposable application ID; never writes the installed user's rule store. */
class PageRefreshInstrumentation : Instrumentation() {
    private var themesOnly = false
    private var healthMemoryOnly = false
    private var foregroundTimingOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        themesOnly = arguments?.getString("themesOnly") == "true"
        healthMemoryOnly = arguments?.getString("healthMemoryOnly") == "true"
        foregroundTimingOnly = arguments?.getString("foregroundTimingOnly") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        try {
            val context = targetContext
            check(context.packageName == "com.goule.uiverify") { "UI regression requires the isolated app ID" }
            if (themesOnly) {
                ThemeMigrationChecks.run(this)
                activity = startActivitySync(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForIdleSync()
                BlockThemeChecks.run(this)
                onMain { checkNotNull(activity).finish() }
                waitForIdleSync()
                activity = null
                ThemeManagementChecks.run(this)
                ServiceHealthMemoryChecks.run(this)
                result.putString("stream", "PASS: theme imports migration management rendering and image memory lifecycle\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (foregroundTimingOnly) {
                // Keep the disposable app visible: ColorOS may kill an instrumentation-only
                // process as cached while the replay is sleeping between window events.
                activity = startActivitySync(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForIdleSync()
                ForegroundTimingChecks.run(context) { action -> onMain { action() } }
                result.putString("stream", "PASS: foreground timing and recents departure/return replay\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (healthMemoryOnly) {
                ServiceHealthMemoryChecks.run(this)
                result.putString("stream", "PASS: service interruption evidence persists and background images release and reload safely\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            onMain { FreezeOverlayLayoutChecks.run(context) }
            val now = System.currentTimeMillis()
            val listReset = now + 12_000
            val detailReset = now + 24_000
            val rules = """[
                {"package":"fixture.one","label":"周期验证 A","use":10,"block":120,
                 "created":$now,"cycleStart":${listReset - 130 * 60_000},"used":${8 * 60_000},
                 "cycleUse":10,"cycleBlock":120},
                {"package":"fixture.two","label":"周期验证 B","use":20,"block":30,
                 "created":$now,"cycleStart":${detailReset - 50 * 60_000},"used":${19 * 60_000},
                 "cycleUse":20,"cycleBlock":30},
                {"package":"fixture.three","label":"周期验证 C","use":2,"block":15,
                 "created":$now,"cycleStart":null,"used":0}]
            """
            context.getSharedPreferences("rules", 0).edit().clear().putBoolean("welcomed", true)
                .putString("items", rules).putLong("clock", now).commit()
            activity = startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val timingActivity = checkNotNull(activity)
            val listRoot = onMain { timingActivity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) }
            val listCounter = onMain { findText(listRoot, "还可刷 2 分钟") }
            val scroll = onMain { (listRoot as ViewGroup).getChildAt(0) as ScrollView }
            onMain { scroll.scrollTo(0, (200 * context.resources.displayMetrics.density).toInt()) }
            waitForIdleSync()
            val oldY = onMain { scroll.scrollY }
            check(oldY > 0) { "Fixture must exercise a scrolled list" }
            waitUntil(listReset + 1_500)
            onMain {
                check(timingActivity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) === listRoot) {
                    "List timer recreated the entire page"
                }
                check(listCounter.isAttachedToWindow && listCounter.text.toString() == "还可刷 10 分钟") {
                    "Existing list counter did not receive the fixed-cycle reset"
                }
                check(scroll.scrollY == oldY) { "List scroll position moved on reset" }
                val show = MainActivity::class.java.getDeclaredMethod("showRule", String::class.java)
                show.isAccessible = true
                show.invoke(activity, "fixture.two")
            }
            waitForIdleSync()
            val detailRoot = onMain { timingActivity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) }
            val detailCounter = onMain { findText(detailRoot, "还可刷 1 分钟") }
            waitUntil(detailReset + 1_500)
            onMain {
                check(timingActivity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) === detailRoot) {
                    "Detail timer recreated the entire page"
                }
                check(detailCounter.isAttachedToWindow && detailCounter.text.toString() == "还可刷 20 分钟") {
                    "Existing detail counter did not receive its independent reset"
                }
            }
            ForegroundTimingChecks.run(context) { action -> onMain { action() } }
            onMain { timingActivity.finish() }
            waitForIdleSync()
            OnboardingFlowChecks.run(this)
            ThemeMigrationChecks.run(this)
            // Give import/render checks a visible host, just like a user editing a theme.
            activity = startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            BlockThemeChecks.run(this)
            onMain { checkNotNull(activity).finish() }
            waitForIdleSync()
            activity = null
            ThemeManagementChecks.run(this)
            ServiceHealthMemoryChecks.run(this)
            result.putString("stream", "PASS: list/detail fixed resets update existing views and preserve scroll\n" +
                "PASS: overlay landscape actions stay visible and clickable through resizing and large fonts\n" +
                "PASS: overlay edge background and stable system/cutout/gesture insets\n" +
                "PASS: usable target keeps counting after recents closes and stops on exit\n" +
                "PASS: floating system dialogs retain target timing/deadline and respect shade/settings/screen exits\n" +
                "PASS: timing stop paths and persistent diagnostics explain rotation/window/screen/stale/stall scenarios\n" +
                "PASS: ColorOS passive containers preserve usage and deadline while shade/settings/interrupt still pause\n" +
                "PASS: onboarding visits three steps in order and resumes before completion\n" +
                "PASS: block themes select persist preview and import images with safe failure recovery\n" +
                "PASS: theme dropdowns protect elder and persist deletions across every selection path\n" +
                "PASS: unified theme content and legacy picture migration preserve fixed and random choices\n" +
                "PASS: service interruption evidence persists and background images release and reload safely\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            activity?.let { current -> onMain { current.finish() } }
        }
    }

    private fun findText(view: View, text: String): TextView {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            runCatching { findText(view.getChildAt(i), text) }.getOrNull()?.let { return it }
        }
        error("Missing counter: $text")
    }

    private fun waitUntil(time: Long) {
        val delay = time - System.currentTimeMillis()
        if (delay > 0) SystemClock.sleep(delay)
        waitForIdleSync()
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
