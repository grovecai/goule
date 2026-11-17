package com.goule.prototype

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import java.io.File

internal object ThemeMigrationChecks {
    fun run(instrumentation: Instrumentation) {
        val d = ThemeTestDriver(instrumentation)
        fun fixture(name: String): Context = object : ContextWrapper(d.context) {
            override fun getSharedPreferences(key: String, mode: Int) = super.getSharedPreferences("$name-$key", mode)
            override fun getNoBackupFilesDir() = File(super.getNoBackupFilesDir(), name).apply { mkdirs() }
        }
        d.main {
            for (excluded in listOf(false, true)) {
                val context = fixture("migration-$excluded")
                val file = File(context.noBackupFilesDir, "block-image.webp")
                val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }; bitmap.recycle()
                context.getSharedPreferences("block_appearance", 0).edit().putString("mode", "CUSTOM")
                    .putString("fixed", "baby").putStringSet("pool", setOf("elder"))
                    .putBoolean("custom_pool_initialized", excluded).commit()
                val migrated = d.fresh(context)
                check(migrated.mode == BlockThemeMode.FIXED && migrated.fixedId == "custom")
                val theme = migrated.find("custom")
                check(theme.image is ThemeImage.Local && theme.closeLabel == "斩退心魔")
                check(migrated.resolve().image?.width == 120)
                check(("custom" in migrated.pool) == !excluded)
                check(!file.exists())
                check(d.fresh(context).find("custom") == theme)
            }
            for (id in listOf("baby", "magic", "garden", "marriage", "sunlight", "craving")) {
                for (unified in listOf(false, true)) {
                    val context = fixture("retired-$id-$unified")
                    context.getSharedPreferences("block_appearance", 0).edit().putBoolean("unified_library", unified)
                        .putString("mode", "RANDOM").putString("fixed", id).putStringSet("pool", setOf(id)).commit()
                    val migrated = d.fresh(context)
                    check(migrated.mode == BlockThemeMode.RANDOM && migrated.fixedId == "elder" && migrated.pool == setOf("elder"))
                    check(migrated.availableThemes.none { it.id == id })
                    check(migrated.resolve(BlockTheme(id, "旧主题", "旧", "旧", "旧")).theme == BlockThemes.default)
                }
            }
            val context = fixture("retired-mixed-personal")
            val image = File(context.noBackupFilesDir, "block-themes/own.webp").apply { requireNotNull(parentFile).mkdirs() }
            val bitmap = Bitmap.createBitmap(50, 30, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }; bitmap.recycle()
            val own = BlockTheme("user_12345678-1234-1234-1234-123456789abc", "个人保留主题", "继续行动", "把今天的事做好。",
                "个人图片", image = ThemeImage.Local("own.webp"))
            context.getSharedPreferences("block_appearance", 0).edit().putBoolean("unified_library", true)
                .putString("mode", "RANDOM").putString("fixed", "magic").putStringSet("deleted", setOf("work"))
                .putStringSet("pool", setOf("magic", "kitchen", own.id))
                .putString("personal_themes", ThemeCodec.encode(listOf(own))).commit()
            val migrated = d.fresh(context)
            check(migrated.fixedId == "elder" && migrated.pool == setOf("kitchen", own.id))
            check(migrated.availableThemes.none { it.id == "work" })
            check(migrated.find(own.id).name == own.name && migrated.resolve(own).image?.width == 50)
            check(migrated.setFixed(own.id) && migrated.resolve().theme.id == own.id)
        }
    }
}
