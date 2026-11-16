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
            val context = fixture("retired-baby")
            context.getSharedPreferences("block_appearance", 0).edit().putString("mode", "RANDOM")
                .putString("fixed", "baby").putStringSet("pool", setOf("baby")).commit()
            val migrated = d.fresh(context)
            check(migrated.mode == BlockThemeMode.RANDOM && migrated.fixedId == "elder" && migrated.pool == setOf("elder"))
            check(migrated.availableThemes.none { it.id == "baby" })
            check(migrated.resolve(BlockTheme("baby", "旧主题", "旧", "旧", "旧")).theme == BlockThemes.default)
        }
    }
}
