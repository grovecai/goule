package com.goule.prototype

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import java.io.File

internal object ThemeManagementChecks {
    fun run(instrumentation: Instrumentation) {
        val d = ThemeTestDriver(instrumentation); val store = d.store
        d.main {
            for (theme in store.personalThemes) check(store.deleteTheme(theme.id))
            check(store.setFixed("elder") && store.setPool(setOf("kitchen")))
        }
        val rules = RuleStore.get(d.context).rules.toList()
        var activity = d.launch()
        try {
            d.go(activity, "THEMES")
            d.main {
                check(d.dropdown(activity, "显示方式").count == 2)
                check(d.views(d.root(activity)).filterIsInstance<ImageView>().size == 1)
                check(!d.has(activity, "删除这个主题") && !store.deleteTheme("elder"))
                check(store.availableThemes.none { it.id == "baby" })
            }
            d.select(activity, "kitchen")
            d.main {
                val view = d.root(activity)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(d.context.getExternalFilesDir(null), "unified-theme-page.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }; bitmap.recycle()
            }
            d.click(activity, "删除这个主题"); d.confirm(activity, false)
            d.main { check(store.fixedId == "kitchen") }
            d.click(activity, "删除这个主题"); d.confirm(activity, true)
            d.main { check(store.fixedId == "elder" && store.pool == setOf("elder") && !store.setFixed("kitchen")) }

            d.click(activity, "新增主题")
            d.field(activity, "主题名（最多20字）", "自己的学习主题")
            d.field(activity, "喝止台词（最多16字）", "书还没看完呢")
            d.field(activity, "提醒台词（最多80字）", "先做完今天最重要的一件事。")
            d.field(activity, "第一个按钮名称（最多6字）", "先收起手机吧")
            d.field(activity, "第二个按钮名称（最多6字）", "现在开始学习")
            val cancel = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); addDataType("image/*")
            }, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            d.click(activity, "选择图片")
            check(cancel.hits == 1); instrumentation.removeMonitor(cancel)
            d.main { check(store.personalThemes.isEmpty()) }
            d.prepare("valid")
            activity = d.recreate(activity)
            d.main { check(d.has(activity, "自己的学习主题") && d.has(activity, "现在开始学习")) }
            d.click(activity, "保存主题")
            val personal = d.main { store.personalThemes.single() }
            d.main { check(store.fixedId == personal.id && store.resolve().theme == personal && personal.closeLabel == "先收起手机吧") }
            d.click(activity, "预览 · ${personal.name}")
            activity = d.recreate(activity)
            d.main {
                check(d.has(activity, personal.headline) && d.has(activity, personal.closeLabel) && d.has(activity, personal.homeLabel))
            }
            d.click(activity, personal.homeLabel)
            d.mode(activity, BlockThemeMode.RANDOM)
            d.click(activity, "选择随机主题（2 个）")
            d.main {
                val dialog = d.dialog(activity)
                val index = store.availableThemes.indexOfFirst { it.id == personal.id }
                check(dialog.listView.adapter.getItem(index) == personal.name && dialog.listView.isItemChecked(index))
                dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, 0)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(store.pool == setOf(personal.id) && store.resolve().theme.id == personal.id)
            }; d.idle()
            d.mode(activity, BlockThemeMode.FIXED); d.select(activity, personal.id)
            d.click(activity, "编辑这个主题")
            d.field(activity, "喝止台词（最多16字）", "这次先去行动")
            d.prepare("replace")
            d.click(activity, "← 返回主题设置"); d.confirm(activity, false)
            d.main { check(d.has(activity, "这次先去行动")) }
            d.click(activity, "← 返回主题设置"); d.confirm(activity, true)
            d.main { check(store.find(personal.id) == personal && store.resolve(personal).image?.width == 1440) }
            d.click(activity, "编辑这个主题")
            d.field(activity, "喝止台词（最多16字）", "这次先去行动")
            d.field(activity, "第一个按钮名称（最多6字）", "收起心魔")
            d.field(activity, "第二个按钮名称（最多6字）", "去做正事")
            d.prepare("replace"); d.click(activity, "保存主题")
            d.main {
                val edited = store.find(personal.id)
                check(edited.id == personal.id && edited.headline == "这次先去行动" && edited.homeLabel == "去做正事")
                check(store.resolve(edited).image?.height == 300 && d.fresh().find(edited.id) == edited)
                check(store.pool == setOf(personal.id))
            }
            d.click(activity, "删除这个主题"); d.confirm(activity, true)
            d.main { check(store.personalThemes.isEmpty() && store.fixedId == "elder" && store.pool == setOf("elder")) }
            d.mode(activity, BlockThemeMode.RANDOM)
            d.main { check(store.setPool(setOf("work"))) }; d.go(activity, "THEMES"); d.select(activity, "work")
            d.click(activity, "删除这个主题"); d.confirm(activity, true)
            d.main { check(store.mode == BlockThemeMode.RANDOM && store.pool == setOf("elder")) }
            d.click(activity, "选择随机主题（1 个）")
            d.main {
                val dialog = d.dialog(activity)
                dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, 0)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(dialog.isShowing && store.pool == setOf("elder"))
            }; d.confirm(activity, false)
            d.main {
                for (theme in store.availableThemes.filter { store.canDelete(it.id) }) check(store.deleteTheme(theme.id))
                check(store.availableThemes == listOf(BlockThemes.default))
                check(store.deletedIds.size == 4 && !store.deleteTheme("elder"))
                check(d.fresh().availableThemes == listOf(BlockThemes.default))
                check(RuleStore.get(d.context).rules == rules)
            }
        } finally { d.main { activity.finish() } }
    }
}
