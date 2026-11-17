package com.goule.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Process
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.ImageView
import java.io.File

internal object ServiceHealthMemoryChecks {
    fun run(instrumentation: Instrumentation) {
        val d = ThemeTestDriver(instrumentation)
        val context = d.context
        val health = ServiceHealthStore.get(context)
        val report = StringBuilder()
        d.main { TimingDiagnostics.get(context) }
        val historyEnd = SystemClock.uptimeMillis() + 5_000
        while (!d.main { health.evidence().historyReady } && SystemClock.uptimeMillis() < historyEnd) SystemClock.sleep(50)
        check(d.main { health.evidence().historyReady }) { "System exit history never completed" }
        report.append("System exit history read completed; previous reason=${d.main { health.evidence().exit?.reason }}\n")
        d.main {
            for (theme in d.store.personalThemes.filter { it.name in setOf("内存验证主题", "后台返回草稿") }) {
                check(d.store.deleteTheme(theme.id))
            }
            val now = System.currentTimeMillis()
            health.connected(now - 20_000)
            health.observed(now - 10_000)
            val fresh = ServiceHealthStore::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(context)
            check(fresh.evidence().observation == health.evidence().observation) { "Observation did not persist" }
            health.historyLoaded(ProcessExit(now - 5_000, Process.myPid(), 13, "o-kill(4013)\n" + "系统原始说明".repeat(80)))
            RuleStore.get(context).activate()
            RuleStore.get(context).approve()
            RuleStore.get(context).welcome()
            check(RuleStore.get(context).configure(listOf(Rule("fixture.memory", "内存验证", 5, 115, now))))
        }
        var activity = d.launch()
        try {
            d.go(activity, "PERMISSION")
            d.main {
                check(d.has(activity, "中断记录"))
                val text = d.texts(activity).map { it.text.toString() }
                check(text.any { it.startsWith("最近确认连接：") })
                check(text.any { it.startsWith("服务所在进程退出：") })
                check(text.any { it == "系统记录：系统结束了应用进程，具体策略未确定" })
                check(text.single { it.startsWith("系统原始说明：") }.length <= 247)
                check(d.has(activity, "重新开启无障碍"))
                for (view in d.texts(activity).filter { it.text.startsWith("系统原始说明：") }) {
                    check(view.layout != null && view.layout.lineCount > 1)
                    for (line in 0 until view.layout.lineCount) {
                        check(view.layout.getLineRight(line) <= view.layout.width + 1f) { "Exit description exceeds card width" }
                    }
                }
                d.texts(activity).first { it.text.toString() == "中断记录" }
                    .requestRectangleOnScreen(android.graphics.Rect(0, 0, 1, 1), true)
            }
            d.idle()
            d.main {
                screenshot(d, activity, "service-interruption-record.png")
                val card = d.texts(activity).first { it.text.toString() == "中断记录" }.parent as ViewGroup
                val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
                card.draw(Canvas(bitmap))
                File(context.getExternalFilesDir(null), "service-interruption-card.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }; bitmap.recycle()
            }
            activity = d.recreate(activity)
            d.main { check(d.has(activity, "中断记录")) }
            d.go(activity, "WELCOME")
            d.main { screenshot(d, activity, "memory-welcome.png") }
            d.go(activity, "RULES")
            d.main { screenshot(d, activity, "memory-rules.png") }
            val retained = d.main { BackdropImages.retainedBytes() }
            check(retained > 0)
            val rawBytes = listOf(R.drawable.welcome_composed, R.drawable.rules_sword).sumOf {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
                BitmapFactory.decodeResource(context.resources, it, bounds)
                bounds.outWidth.toLong() * bounds.outHeight * 4
            }
            report.append("Original welcome+rules decoded pixels=$rawBytes bytes\nForeground shared cache=$retained bytes\n")
            d.main {
                val a = BackdropImages.load(context.resources, R.drawable.rules_sword, 359, 1)
                val b = BackdropImages.load(context.resources, R.drawable.rules_sword, 359, 1)
                check(a === b && a.width == 360 && a.height == 800)
                report.append("359px-wide rules decode=${a.allocationByteCount} bytes (${a.width}x${a.height})\n")
                BackdropImages.clear()
                check(!a.isRecycled)
            }
            backgroundAndReturn(d, activity) {
                check(BackdropImages.retainedBytes() == 0)
                check(d.root(activity).let { it as ViewGroup }.getChildAt(0).background == null)
                report.append("Stopped activity backdrop cache=0 bytes\n")
            }
            d.main { screenshot(d, activity, "memory-rules-restored.png") }

            d.prepare("valid")
            val theme = d.main { checkNotNull(d.store.savePersonal(ThemeDraft(name = "内存验证主题"))) }
            d.go(activity, "THEMES")
            val activeImage = d.main { checkNotNull(d.store.resolve(theme).image) }
            val before = d.main { d.store.retainedImageBytes() }
            check(before > 0)
            val overlay = d.main { FreezeOverlayView(context, "验证目标", "固定截止 15:00", {}, {}, d.store.resolve(theme)) }
            d.main {
                (activity.application as GouleApplication).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                check(d.store.retainedImageBytes() == 0 && !activeImage.isRecycled)
                val bitmap = Bitmap.createBitmap(context.dp(360), context.dp(640), Bitmap.Config.ARGB_8888)
                overlay.measure(android.view.View.MeasureSpec.makeMeasureSpec(bitmap.width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(bitmap.height, android.view.View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, bitmap.width, bitmap.height)
                overlay.draw(Canvas(bitmap)); bitmap.recycle()
                check(!activeImage.isRecycled) { "Trim invalidated an active overlay" }
                check(d.store.resolve(theme).image !== activeImage) { "Personal image did not reload after trim" }
            }
            report.append("Personal cache before trim=$before bytes; after trim=0; active overlay still drawable\n")
            d.click(activity, "预览 · 内存验证主题")
            backgroundAndReturn(d, activity) {
                check(d.store.retainedImageBytes() == 0)
                check(d.views(d.root(activity)).filterIsInstance<ImageView>().isEmpty())
            }
            d.main {
                check(d.has(activity, theme.headline) && d.has(activity, theme.homeLabel))
                check(d.views(d.root(activity)).filterIsInstance<ImageView>().isNotEmpty())
            }
            d.click(activity, theme.homeLabel)
            d.click(activity, "新增主题")
            d.field(activity, "主题名（最多20字）", "后台返回草稿")
            d.prepare("valid")
            backgroundAndReturn(d, activity) { check(d.store.hasDraftImage && d.store.retainedImageBytes() == 0) }
            d.main {
                check(d.has(activity, "后台返回草稿") && d.store.hasDraftImage)
                check(d.views(d.root(activity)).filterIsInstance<ImageView>().isNotEmpty())
            }
            d.click(activity, "保存主题")
            d.main { check(d.store.personalThemes.any { it.name == "后台返回草稿" }) }
            File(context.getExternalFilesDir(null), "service-health-memory.txt").writeText(report.toString())
        } finally {
            d.main { activity.finish(); d.store.discardDraft() }
        }
    }

    private fun backgroundAndReturn(d: ThemeTestDriver, activity: Activity, stopped: () -> Unit) {
        d.main { check(activity.moveTaskToBack(true)) }
        waitFor(d) { (d.root(activity) as ViewGroup).getChildAt(0).background == null }
        d.main { stopped() }
        d.context.startActivity(Intent(d.context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitFor(d) { activity.hasWindowFocus() && (d.root(activity) as ViewGroup).getChildAt(0).background != null }
    }

    private fun waitFor(d: ThemeTestDriver, ready: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!d.main(ready) && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        d.idle()
        check(d.main(ready)) { "Activity did not complete background/foreground transition" }
    }

    private fun screenshot(d: ThemeTestDriver, activity: Activity, name: String) {
        val root = d.root(activity)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(d.context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
