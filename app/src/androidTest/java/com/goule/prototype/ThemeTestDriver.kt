package com.goule.prototype

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.atomic.AtomicReference

internal class ThemeTestDriver(val instrumentation: Instrumentation) {
    val context = instrumentation.targetContext.also { check(it.packageName == "com.goule.uiverify") }
    val store = BlockAppearanceStore.get(context)
    fun <T> main(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
    fun idle() = instrumentation.waitForIdleSync()
    fun launch(): Activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { idle() }
    fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    fun texts(activity: Activity) = views(root(activity)).filterIsInstance<TextView>()
    fun has(activity: Activity, text: String) = texts(activity).any { it.text.toString() == text }
    fun click(activity: Activity, text: String) {
        main { check(texts(activity).first { it.text.toString() == text }.performClick()) { "Cannot click $text" } }
        idle()
    }
    fun field(activity: Activity, label: String, value: String) {
        main { views(root(activity)).filterIsInstance<EditText>().single { it.contentDescription == label }.setText(value) }
    }
    fun dropdown(activity: Activity, label: String) = views(root(activity)).filterIsInstance<Spinner>().single { it.contentDescription == label }
    fun mode(activity: Activity, mode: BlockThemeMode) {
        main { dropdown(activity, "显示方式").setSelection(mode.ordinal) }; idle()
    }
    fun select(activity: Activity, id: String) {
        main { dropdown(activity, if (store.mode == BlockThemeMode.FIXED) "选择主题" else "管理主题")
            .setSelection(store.availableThemes.indexOfFirst { it.id == id }.also { check(it >= 0) }) }; idle()
    }
    fun go(activity: Activity, page: String) {
        main {
            val type = MainActivity::class.java.declaredClasses.first { it.simpleName == "Page" }
            val value = checkNotNull(type.enumConstants).first { (it as Enum<*>).name == page }
            MainActivity::class.java.getDeclaredMethod("go", type).apply { isAccessible = true }.invoke(activity, value)
        }; idle()
    }
    fun dialog(activity: Activity) = MainActivity::class.java.getDeclaredField("themeDialog")
        .apply { isAccessible = true }.get(activity) as AlertDialog
    fun confirm(activity: Activity, yes: Boolean) {
        main { dialog(activity).getButton(if (yes) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick() }; idle()
    }
    fun prepare(kind: String) {
        main { store.importImage(Uri.parse("content://com.goule.uiverify.images/$kind")) }
        val until = SystemClock.uptimeMillis() + 15_000
        while (main { store.importing } && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
        check(!main { store.importing }) { "Image import did not complete" }; idle()
    }
    fun fresh(context: Context = this.context): BlockAppearanceStore = BlockAppearanceStore::class.java
        .getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(context)
    fun recreate(activity: Activity): Activity {
        val reference = AtomicReference<Activity?>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(current: Activity) { if (current is MainActivity && current !== activity) reference.set(current) }
            override fun onActivityCreated(current: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(current: Activity) = Unit
            override fun onActivityPaused(current: Activity) = Unit
            override fun onActivityStopped(current: Activity) = Unit
            override fun onActivitySaveInstanceState(current: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(current: Activity) = Unit
        }
        main { activity.application.registerActivityLifecycleCallbacks(callbacks); activity.recreate() }
        try {
            val until = SystemClock.uptimeMillis() + 10_000
            while (reference.get() == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
            idle()
            return checkNotNull(reference.get()) { "Activity did not resume after recreation" }
        } finally { main { activity.application.unregisterActivityLifecycleCallbacks(callbacks) } }
    }
}
