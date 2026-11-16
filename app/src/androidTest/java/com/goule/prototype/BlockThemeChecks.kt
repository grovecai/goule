package com.goule.prototype

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import java.io.File

internal object BlockThemeChecks {
    fun run(instrumentation: Instrumentation) {
        val d = ThemeTestDriver(instrumentation)
        val store = d.store
        d.main { store.discardDraft(); check(store.setFixed("elder")) }
        d.prepare("valid")
        val draftImage = d.main { store.draftImage() }
        check(draftImage?.width == 1440 && draftImage.height == 720)
        val first = d.main { checkNotNull(store.savePersonal(ThemeDraft(name = "第一张主题"))) }
        val saved = d.main { store.resolve(first) }
        check(saved.theme.id == first.id && saved.image?.width == 1440)
        d.main {
            check(store.setFixed(first.id) && store.resolve().theme.id == first.id)
            check(store.setMode(BlockThemeMode.RANDOM) && store.setPool(setOf(first.id)))
            check(store.resolve().image === saved.image)
        }
        for (kind in listOf("corrupt", "denied", "oversize")) {
            d.prepare(kind)
            d.main {
                check(!store.hasDraftImage)
                check(store.resolve(first).image === saved.image && store.find(first.id) == first)
            }
        }
        d.prepare("replace")
        d.main { store.discardDraft(); check(store.resolve(first).image === saved.image) }
        d.prepare("replace")
        val edited = d.main { checkNotNull(store.savePersonal(ThemeDraft.from(first).copy(
            headline = "去完成今天的计划", closeLabel = "先收起手机吧", homeLabel = "现在开始学习"))) }
        d.main {
            check(edited.id == first.id && edited.image != first.image)
            check(store.resolve(edited).image?.height == 300)
            check(d.fresh().find(first.id) == edited)
            val old = first.image as ThemeImage.Local
            check(!File(d.context.noBackupFilesDir, "block-themes/${old.fileName}").exists())
        }
        d.prepare("valid")
        val second = d.main { checkNotNull(store.savePersonal(ThemeDraft(name = "第二张主题"))) }
        d.main {
            check(store.personalThemes.size == 2 && store.pool.containsAll(setOf(first.id, second.id)))
            check(store.setMode(BlockThemeMode.RANDOM) && store.setPool(setOf(second.id)))
            check(store.resolve().theme.id == second.id)
            check(store.setFixed(first.id) && store.resolve().theme == edited)
            for (appearance in BlockThemes.all.map { store.resolve(it) } + store.resolve(edited)) {
                FreezeOverlayLayoutChecks.run(d.context, appearance, writeScreenshots = false)
                var exits = 0
                val view = FreezeOverlayView(d.context, "测试 App", "固定截止 15:00", { exits++ }, { exits++ }, appearance)
                for ((width, height) in listOf(360 to 640, 640 to 320, 360 to 640)) {
                    val w = d.context.dp(width); val h = d.context.dp(height)
                    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, w, h)
                    val labels = d.views(view).filterIsInstance<Button>().map { it.text.toString() }
                    check(labels == listOf(appearance.theme.closeLabel, appearance.theme.homeLabel))
                    d.views(view).filterIsInstance<Button>().forEach { it.performClick() }
                    if (width == 360) {
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        File(d.context.getExternalFilesDir(null), "theme-${appearance.theme.id}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                check(exits == 6)
            }
        }
    }
}
