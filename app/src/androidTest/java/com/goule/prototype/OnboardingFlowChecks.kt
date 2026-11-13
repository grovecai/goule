package com.goule.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Checks navigation and completion without changing system accessibility grants. */
internal object OnboardingFlowChecks {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.goule.uiverify")
        fun <T> onMain(action: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(action) }
            return checkNotNull(result).getOrThrow()
        }
        fun launch() = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).also { instrumentation.waitForIdleSync() }
        fun close(activity: Activity) {
            onMain { activity.finish() }
            instrumentation.waitForIdleSync()
        }
        var activity: Activity? = null
        try {
            onMain {
                check(RuleStore.get(context).configure(emptyList()))
                context.getSharedPreferences("rules", 0).edit().clear().commit()
                context.prototypeSettings().edit().clear().commit()
            }
            val first = launch().also { activity = it }
            onMain {
                click(first, "立下第一条戒律")
                check(hasText(first, "第 1 步，共 3 步"))
                check(!hasText(first, "风险行为拦截白名单")) { "First step exposes a shortcut to step three" }
                check(!text(first, "下一步：允许后台运行").isEnabled)
                RuleStore.get(context).approve()
                check(!RuleStore.get(context).welcomed) { "Consent incorrectly completed onboarding" }
            }
            close(first)
            val afterConsent = launch().also { activity = it }
            onMain {
                check(hasText(afterConsent, "第 1 步，共 3 步")) { "Restart after consent skipped onboarding" }
                connectedForNavigation(afterConsent)
                click(afterConsent, "下一步：允许后台运行")
                check(hasText(afterConsent, "第 2 步，共 3 步"))
                check(!hasText(afterConsent, "继续添加心魔")) { "Background step bypasses the whitelist guide" }
                click(afterConsent, "下一步：风险行为拦截白名单")
                check(hasText(afterConsent, "第 3 步，共 3 步"))
                check(!RuleStore.get(context).welcomed)
                click(afterConsent, "← 返回")
                check(hasText(afterConsent, "第 2 步，共 3 步"))
                click(afterConsent, "下一步：风险行为拦截白名单")
            }
            close(afterConsent)
            val finalStep = launch().also { activity = it }
            onMain {
                check(hasText(finalStep, "第 3 步，共 3 步")) { "Restart did not resume the final step" }
                check(!text(finalStep, "继续添加心魔").isEnabled) { "Disconnected service allowed completion" }
                connectedForNavigation(finalStep)
                click(finalStep, "继续添加心魔")
                check(hasText(finalStep, "添加心魔"))
                check(RuleStore.get(context).welcomed && RuleStore.get(context).onboardingStep == null)
            }
            close(finalStep)
            val completed = launch().also { activity = it }
            onMain {
                check(!hasText(completed, "第 1 步，共 3 步")) { "Completed user was forced through onboarding" }
                // The tab label is a child of the clickable navigation item.
                (text(completed, "设置").parent as View).performClick()
                val actions = views(root(completed)).filterIsInstance<TextView>().filter { it.text.toString() == "去设置" }
                check(actions.size == 3)
                check(actions[2].performClick())
                check(hasText(completed, "风险行为拦截白名单") && !hasText(completed, "第 3 步，共 3 步"))
                click(completed, "返回")
                check(hasText(completed, "戒律保护")) { "Independent guide did not return to settings" }
            }
        } finally {
            activity?.let { close(it) }
        }
    }

    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
    } else emptyList()
    private fun hasText(activity: Activity, label: String) =
        views(root(activity)).filterIsInstance<TextView>().any { it.text.toString() == label }
    private fun text(activity: Activity, label: String) =
        views(root(activity)).filterIsInstance<TextView>().first { it.text.toString() == label }
    private fun click(activity: Activity, label: String) {
        val view = text(activity, label)
        check(view.isEnabled && view.performClick()) { "Cannot click $label" }
    }

    private fun connectedForNavigation(activity: Activity) {
        // Only the navigation fixture uses a synthetic connection; real grants are tested separately.
        MainActivity::class.java.getDeclaredField("appState").apply { isAccessible = true }
            .set(activity, ServiceState.ON)
        MainActivity::class.java.getDeclaredMethod("render", Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
        }.invoke(activity, false)
    }
}
