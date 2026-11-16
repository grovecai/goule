package com.goule.prototype

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import java.io.File

/** Exercises the real dropdown callbacks and owned dialogs in the isolated application. */
internal object ThemeManagementChecks {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.goule.uiverify")
        val store = BlockAppearanceStore.get(context)
        fun <T> onMain(action: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(action) }
            return checkNotNull(result).getOrThrow()
        }
        onMain {
            check(store.setFixed("elder"))
            check(store.setPool(setOf("kitchen")))
        }
        val rulesBefore = RuleStore.get(context).rules.toList()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        fun select(label: String, index: Int) {
            onMain { dropdown(activity, label).setSelection(index) }
            instrumentation.waitForIdleSync()
        }
        fun deleteCurrent(confirm: Boolean) {
            onMain {
                click(activity, "删除这个内置主题")
                val dialog = dialog(activity)
                dialog.getButton(if (confirm) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
        }
        fun openPool(): AlertDialog {
            onMain { click(activity, "选择随机主题（1 个）") }
            instrumentation.waitForIdleSync()
            return onMain { dialog(activity) }
        }
        try {
            onMain { navigate(activity, "THEMES") }
            instrumentation.waitForIdleSync()
            onMain {
                check(views(root(activity)).filterIsInstance<Spinner>().size == 2)
                check(views(root(activity)).filterIsInstance<ImageView>().size == 1) { "Theme page still expands all images" }
                check(texts(activity).none { it.text.toString() == "删除这个内置主题" })
                check(!store.deleteBuiltIn("elder"))
            }
            select("选择主题", store.availableThemes.indexOfFirst { it.id == "kitchen" })
            onMain {
                check(store.fixedId == "kitchen")
                val view = root(activity)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(context.getExternalFilesDir(null), "theme-dropdown-page.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            deleteCurrent(confirm = false)
            onMain { check(store.fixedId == "kitchen" && store.deletedIds.isEmpty()) }
            deleteCurrent(confirm = true)
            onMain {
                check(store.fixedId == "elder" && store.pool == setOf("elder"))
                check("kitchen" in store.deletedIds && !store.setFixed("kitchen"))
                check(store.resolve(BlockThemes.find("kitchen")).theme.id == "elder")
                check((0 until dropdown(activity, "选择主题").count).none {
                    dropdown(activity, "选择主题").getItemAtPosition(it) == "锅要糊了"
                })
                MainActivity::class.java.getDeclaredField("previewThemeId").apply { isAccessible = true }
                    .set(activity, "kitchen")
                navigate(activity, "THEME_PREVIEW")
            }
            instrumentation.waitForIdleSync()
            onMain {
                check(texts(activity).any { it.text.toString() == "够了！" }) { "Saved preview resurrected a deleted theme" }
                click(activity, "回洞府")
            }
            instrumentation.waitForIdleSync()
            select("选择主题", store.availableThemes.indexOfFirst { it.id == "garden" })
            select("显示方式", BlockThemeMode.RANDOM.ordinal)
            onMain { check(store.setPool(setOf("garden"))) }
            deleteCurrent(confirm = true)
            onMain { check(store.mode == BlockThemeMode.RANDOM && store.pool == setOf("elder")) }

            val emptyDraft = openPool()
            onMain {
                val dialog = emptyDraft
                val list = dialog.listView
                list.performItemClick(list.getChildAt(0), 0, 0)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(dialog.isShowing && store.pool == setOf("elder")) { "Empty pool was saved or dialog closed" }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            // Canceling a populated draft must not alter the pool either.
            for (save in listOf(false, true)) {
                val draft = openPool()
                onMain {
                    val dialog = draft
                    val list = dialog.listView
                    val index = store.availableThemes.indexOfFirst { it.id == "exam" }
                    list.performItemClick(list.getChildAt(0), 0, 0)
                    list.performItemClick(list.getChildAt(index), index, index.toLong())
                    dialog.getButton(if (save) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()
                    check(store.pool == if (save) setOf("exam") else setOf("elder"))
                }
                instrumentation.waitForIdleSync()
            }
            onMain {
                val fresh = freshStore(context)
                check(fresh.deletedIds == setOf("kitchen", "garden") && fresh.fixedId == "elder")
                check(fresh.pool == setOf("exam") && fresh.resolve().theme.id == "exam")
                // Built-in deletion also preserves an active personal picture and CUSTOM mode.
                val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
                File(context.noBackupFilesDir, "block-image.webp").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it)
                }
                bitmap.recycle()
                check(store.setMode(BlockThemeMode.CUSTOM))
                val image = store.resolve().image
                check(image != null && store.deleteBuiltIn("marriage"))
                check(store.mode == BlockThemeMode.CUSTOM && store.resolve().image === image)
                // Older versions had no custom option. Migrate once, then honor deliberate opt-out.
                context.getSharedPreferences("block_appearance", 0).edit()
                    .remove("custom_pool_initialized").putStringSet("pool", setOf("exam")).commit()
                check(freshStore(context).pool == setOf("exam", "custom"))
                check(store.setPool(setOf("exam")))
                check(freshStore(context).pool == setOf("exam"))
                check(store.setPool(setOf("custom")))
                check(store.setMode(BlockThemeMode.RANDOM))
                navigate(activity, "THEMES")
            }
            instrumentation.waitForIdleSync()
            val personalPool = openPool()
            onMain {
                val index = store.randomThemes.indexOfFirst { it.id == "custom" }
                check(index >= 0 && personalPool.listView.adapter.getItem(index) == "自定义图片")
                check(personalPool.listView.isItemChecked(index))
                personalPool.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(store.pool == setOf("custom") && store.resolve().theme == BlockThemes.custom)
                check(freshStore(context).resolve().theme == BlockThemes.custom)
            }
            instrumentation.waitForIdleSync()
            onMain {
                check(store.deleteImage())
                check(store.mode == BlockThemeMode.RANDOM && store.pool == setOf("elder"))
                check(store.randomThemes.none { it.id == "custom" } && !store.setPool(setOf("custom")))
                for (theme in store.availableThemes.filter { BlockThemes.canDelete(it.id) }) check(store.deleteBuiltIn(theme.id))
                check(store.availableThemes == listOf(BlockThemes.default) && store.pool == setOf("elder"))
                check(!store.deleteBuiltIn("elder") && !store.deleteBuiltIn("unknown"))
                val reopened = freshStore(context)
                check(reopened.availableThemes == listOf(BlockThemes.default))
                check(reopened.deletedIds.size == 10 && reopened.resolve().theme.id == "elder")
                // Stale selection data from an upgrade must not revive a deleted ID.
                context.getSharedPreferences("block_appearance", 0).edit()
                    .putString("fixed", "exam").putStringSet("pool", setOf("exam")).commit()
                check(reopened.fixedId == "elder" && reopened.pool == setOf("elder"))
                check(RuleStore.get(context).rules == rulesBefore) { "Theme management altered rules" }
                navigate(activity, "THEMES")
            }
            instrumentation.waitForIdleSync()
            onMain {
                check(dropdown(activity, "管理内置主题").count == 1)
                check(texts(activity).none { it.text.toString() == "删除这个内置主题" })
            }
        } finally { onMain { activity.finish() } }
    }

    private fun freshStore(context: Context) = BlockAppearanceStore::class.java.getDeclaredConstructor(Context::class.java)
        .apply { isAccessible = true }.newInstance(context)
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun texts(activity: Activity) = views(root(activity)).filterIsInstance<TextView>()
    private fun dropdown(activity: Activity, label: String) = views(root(activity)).filterIsInstance<Spinner>()
        .single { it.contentDescription == label }
    private fun dialog(activity: Activity) = MainActivity::class.java.getDeclaredField("themeDialog")
        .apply { isAccessible = true }.get(activity) as AlertDialog
    private fun click(activity: Activity, label: String) {
        check(texts(activity).first { it.text.toString() == label }.performClick())
    }
    private fun navigate(activity: Activity, name: String) {
        val type = MainActivity::class.java.declaredClasses.first { it.simpleName == "Page" }
        val page = checkNotNull(type.enumConstants).first { (it as Enum<*>).name == name }
        MainActivity::class.java.getDeclaredMethod("go", type).apply { isAccessible = true }.invoke(activity, page)
    }
}
