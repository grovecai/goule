package com.goule.prototype

import android.app.Activity
import android.app.Instrumentation
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal object BlockThemeChecks {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        val store = BlockAppearanceStore.get(context)
        fun <T> onMain(action: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(action) }
            return checkNotNull(result).getOrThrow()
        }
        fun importImage(kind: String) {
            onMain { store.importImage(Uri.parse("content://com.goule.uiverify.images/$kind")) }
            val until = SystemClock.uptimeMillis() + 15_000
            while (onMain { store.importing } && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
            check(!onMain { store.importing }) { "Image import did not finish" }
            instrumentation.waitForIdleSync()
        }
        onMain {
            check(store.deleteImage())
            check(store.setFixed("elder"))
        }
        importImage("valid")
        val first = onMain { store.resolve() }
        check(first.theme == BlockThemes.custom && first.image?.width == 1440 && first.image.height == 720)
        for (kind in listOf("corrupt", "denied", "oversize")) {
            importImage(kind)
            val kept = onMain { store.resolve() }
            check(kept.image === first.image && store.mode == BlockThemeMode.CUSTOM) { "Invalid $kind replaced image or mode" }
        }
        importImage("replace")
        val replaced = onMain { store.resolve() }
        check(replaced.image !== first.image && replaced.image?.width == 90 && replaced.image.height == 300)
        check(Color.blue(checkNotNull(replaced.image).getPixel(40, 100)) > 200)
        // A fresh store reads persisted normalized bytes and preferences, not the import URI or memory cache.
        val fresh = BlockAppearanceStore::class.java.getDeclaredConstructor(android.content.Context::class.java).apply {
            isAccessible = true
        }.newInstance(context)
        onMain { check(fresh.resolve().image?.height == 300 && fresh.mode == BlockThemeMode.CUSTOM) }

        onMain {
            val appearances = BlockThemes.all.map { store.resolve(it) } + replaced
            for (appearance in appearances) {
                FreezeOverlayLayoutChecks.run(context, appearance, writeScreenshots = false)
                var exits = 0
                val view = FreezeOverlayView(context, "测试 App", "固定截止 15:00", { exits++ }, { exits++ }, appearance)
                for ((width, height) in listOf(360 to 640, 640 to 320, 360 to 640)) {
                    val w = context.dp(width)
                    val h = context.dp(height)
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, w, h)
                    check(texts(view).any { it.text.toString() == appearance.theme.headline })
                    check(texts(view).any { it.text.toString() == "固定截止 15:00" })
                    texts(view).filterIsInstance<android.widget.Button>().forEach { it.performClick() }
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    if (width == 360) File(context.getExternalFilesDir(null), "theme-${appearance.theme.id}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                check(exits == 6) { "Theme changed exit callbacks" }
            }
            check(store.setMode(BlockThemeMode.RANDOM))
            check(store.setPool(setOf("kitchen")))
            repeat(10) { check(store.resolve().theme.id == "kitchen") }
            check(!store.setPool(emptySet()))
        }

        var activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        try {
            onMain {
                navigate(activity, "SETTINGS")
                click(activity, "选择禁用页主题")
                check(texts(root(activity)).any { it.text.toString() == "禁用页主题" })
                // Removing the last selection must recover the checkbox and retain the pool.
                val last = texts(root(activity)).filterIsInstance<CheckBox>().single { it.isChecked }
                last.performClick()
                check(last.isChecked && store.pool == setOf("kitchen"))
                click(activity, "预览 · 锅要糊了")
            }
            instrumentation.waitForIdleSync()
            onMain {
                check(texts(root(activity)).any { it.text.toString() == "锅要糊了！" })
                val saved = Bundle()
                MainActivity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply {
                    isAccessible = true
                }.invoke(activity, saved)
                check(saved.getString("page") == "THEME_PREVIEW" && saved.getString("previewTheme") == "kitchen")
            }
            val recreated = AtomicReference<MainActivity?>()
            val application = activity.application
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(current: Activity) {
                    if (current is MainActivity && current !== activity) recreated.set(current)
                }
                override fun onActivityCreated(current: Activity, state: Bundle?) = Unit
                override fun onActivityStarted(current: Activity) = Unit
                override fun onActivityPaused(current: Activity) = Unit
                override fun onActivityStopped(current: Activity) = Unit
                override fun onActivitySaveInstanceState(current: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(current: Activity) = Unit
            }
            onMain { application.registerActivityLifecycleCallbacks(callbacks); activity.recreate() }
            try {
                val until = SystemClock.uptimeMillis() + 10_000
                while (recreated.get() == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
                activity = checkNotNull(recreated.get()) { "Preview did not resume after recreation" }
                instrumentation.waitForIdleSync()
                onMain {
                    check(texts(root(activity)).any { it.text.toString() == "锅要糊了！" })
                    click(activity, "回洞府")
                    check(texts(root(activity)).any { it.text.toString() == "禁用页主题" })
                }
            } finally { onMain { application.unregisterActivityLifecycleCallbacks(callbacks) } }
            val cancellation = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("image/*")
            },
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            onMain { click(activity, "更换图片") }
            instrumentation.waitForIdleSync()
            check(cancellation.hits == 1)
            instrumentation.removeMonitor(cancellation)
            onMain {
                check(store.mode == BlockThemeMode.RANDOM && store.resolve(BlockThemes.custom).image === replaced.image)
                click(activity, "○ 固定主题")
                click(activity, "返回设置")
                check(texts(root(activity)).any { it.text.toString() == "戒律保护" })
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            onMain {
                check(store.mode == BlockThemeMode.FIXED && store.pool == setOf("kitchen"))
                check(store.setMode(BlockThemeMode.CUSTOM))
                check(store.deleteImage())
                check(store.mode == BlockThemeMode.FIXED && store.resolve().theme == BlockThemes.default)
                // A missing/corrupt private file is also a recoverable presentation failure.
                context.getSharedPreferences("block_appearance", 0).edit().putString("mode", "CUSTOM").commit()
                File(context.noBackupFilesDir, "block-image.webp").writeText("corrupt")
                check(store.resolve().theme == BlockThemes.default)
                check(store.deleteImage())
            }
        } finally { onMain { activity.finish() } }
    }

    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun texts(view: View): List<TextView> = (if (view is TextView) listOf(view) else emptyList()) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
    private fun click(activity: Activity, label: String) {
        check(texts(root(activity)).first { it.text.toString() == label }.performClick()) { "Cannot click $label" }
    }
    private fun navigate(activity: Activity, name: String) {
        val pageClass = MainActivity::class.java.declaredClasses.first { it.simpleName == "Page" }
        val page = checkNotNull(pageClass.enumConstants).first { (it as Enum<*>).name == name }
        MainActivity::class.java.getDeclaredMethod("go", pageClass).apply { isAccessible = true }.invoke(activity, page)
    }
}
