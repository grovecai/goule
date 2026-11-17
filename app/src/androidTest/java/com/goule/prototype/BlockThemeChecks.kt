package com.goule.prototype

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
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
                    if (appearance.theme.id == "mother") {
                        val art = d.views(view).filterIsInstance<ImageView>().single()
                        check(art.drawable.intrinsicWidth == 1536 && art.drawable.intrinsicHeight == 1024)
                        val bounds = RectF(0f, 0f, 1536f, 1024f)
                        art.imageMatrix.mapRect(bounds)
                        check(art.scaleType == ImageView.ScaleType.FIT_CENTER && bounds.left >= -1f && bounds.top >= -1f &&
                            bounds.right <= art.width + 1f && bounds.bottom <= art.height + 1f) {
                            "Mother theme speech balloons are cropped at ${width}x$height"
                        }
                        if (width == 360) {
                            check(d.views(view).filterIsInstance<TextView>().single {
                                it.text.toString() == appearance.theme.headline
                            }.lineCount == 1)
                            for (button in d.views(view).filterIsInstance<Button>()) {
                                val actionBounds = Rect(0, 0, button.width, button.height)
                                view.offsetDescendantRectToMyCoords(button, actionBounds)
                                check(actionBounds.bottom <= h) { "Mother theme action needs scrolling at normal font size" }
                            }
                        }
                    }
                    d.views(view).filterIsInstance<Button>().forEach { it.performClick() }
                    if (width == 360 || appearance.theme.id == "mother") {
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        val suffix = if (width == 360) "" else "-landscape"
                        File(d.context.getExternalFilesDir(null), "theme-${appearance.theme.id}$suffix.png").outputStream().use {
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
