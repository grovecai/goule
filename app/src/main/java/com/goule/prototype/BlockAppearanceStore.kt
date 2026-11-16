package com.goule.prototype

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal fun BlockTheme.artResource(): Int = when (id) {
    "rescue" -> R.drawable.block_rescue
    "magic" -> R.drawable.block_magic
    "kitchen" -> R.drawable.block_kitchen
    "garden" -> R.drawable.block_garden
    "marriage" -> R.drawable.block_marriage
    "baby" -> R.drawable.block_baby
    "work" -> R.drawable.block_work
    "exam" -> R.drawable.block_exam
    "sunlight" -> R.drawable.block_sunlight
    "craving" -> R.drawable.block_craving
    else -> R.drawable.discipline_elder
}

internal data class BlockAppearance(val theme: BlockTheme, val image: Bitmap? = null)

/** Small independent preferences and a normalized private image; no rule-store writes. */
internal class BlockAppearanceStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_appearance", Context.MODE_PRIVATE)
    private val imageFile = AtomicFile(File(context.noBackupFilesDir, "block-image.webp"))
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var cachedImage: Bitmap? = null
    var importing = false
        private set
    var onImportFinished: (() -> Unit)? = null
    private var importMessage: String? = null

    val mode get() = BlockThemeMode.parse(prefs.getString("mode", null))
    val deletedIds: Set<String> get() = prefs.getStringSet("deleted", emptySet()).orEmpty().toSet()
    val availableThemes get() = BlockThemes.available(deletedIds)
    val randomThemes get() = BlockThemes.randomChoices(deletedIds, hasCustom)
    val fixedId get() = BlockThemes.find(prefs.getString("fixed", null), deletedIds).id
    val pool: Set<String> get() {
        val available = randomThemes.mapTo(mutableSetOf()) { it.id }
        val selected = (prefs.getStringSet("pool", null)?.intersect(available) ?: available).toMutableSet()
        // Earlier versions could not include personal images. Add an existing image once, while
        // retaining deliberate exclusions saved with the new random-pool editor.
        if (!prefs.getBoolean("custom_pool_initialized", false) && hasCustom) selected.add(BlockThemes.custom.id)
        return selected.ifEmpty { setOf(BlockThemes.default.id) }
    }
    val hasCustom get() = imageFile.baseFile.isFile
    val summary get() = when (mode) {
        BlockThemeMode.FIXED -> "固定 · ${BlockThemes.find(fixedId).name}"
        BlockThemeMode.RANDOM -> "随机 · ${pool.size} 个主题"
        BlockThemeMode.CUSTOM -> if (hasCustom) "自定义图片" else "自定义图片不可用 · 戒律长老"
    }

    fun setMode(value: BlockThemeMode): Boolean = !importing &&
        (value != BlockThemeMode.CUSTOM || hasCustom) && prefs.edit().putString("mode", value.name).commit()

    fun setFixed(id: String): Boolean = !importing && availableThemes.any { it.id == id } && prefs.edit()
        .putString("fixed", id).putString("mode", BlockThemeMode.FIXED.name).commit()

    fun setPool(ids: Set<String>): Boolean {
        val valid = ids.intersect(randomThemes.map { it.id }.toSet())
        return !importing && valid.isNotEmpty() && prefs.edit().putStringSet("pool", valid)
            .putBoolean("custom_pool_initialized", true).commit()
    }

    fun deleteBuiltIn(id: String): Boolean {
        if (importing || !BlockThemes.canDelete(id) || id in deletedIds) return false
        val deleted = deletedIds + id
        val remainingPool = (pool - id).ifEmpty { setOf(BlockThemes.default.id) }
        return prefs.edit().putStringSet("deleted", deleted)
            .putString("fixed", BlockThemes.find(fixedId, deleted).id)
            .putStringSet("pool", remainingPool).putBoolean("custom_pool_initialized", true).commit()
    }

    fun resolve(theme: BlockTheme? = null): BlockAppearance {
        val chosen = if (theme == null) BlockThemes.choose(mode, fixedId, pool, hasCustom, deletedIds)
            else if (theme.id == BlockThemes.custom.id) theme else BlockThemes.find(theme.id, deletedIds)
        if (chosen.id != BlockThemes.custom.id) return BlockAppearance(chosen)
        val image = customImage()
        return if (image != null) BlockAppearance(chosen, image) else BlockAppearance(BlockThemes.default)
    }

    @Synchronized private fun customImage(): Bitmap? {
        cachedImage?.let { return it }
        return try {
            imageFile.openRead().use { BitmapFactory.decodeStream(it) }?.also { cachedImage = it }
        } catch (_: IOException) { null }
    }

    fun deleteImage(): Boolean {
        if (importing) return false
        val edit = prefs.edit()
        if (mode == BlockThemeMode.CUSTOM) edit.putString("mode", BlockThemeMode.FIXED.name).putString("fixed", BlockThemes.default.id)
        edit.putStringSet("pool", (pool - BlockThemes.custom.id).ifEmpty { setOf(BlockThemes.default.id) })
            .putBoolean("custom_pool_initialized", true)
        if (!edit.commit()) return false
        synchronized(this) {
            imageFile.delete()
            cachedImage = null // Existing views can keep their bitmap until detached.
        }
        return !hasCustom
    }

    fun takeImportMessage(): String? = importMessage.also { importMessage = null }

    /** Copy/decode/compress off the main thread so a large photo cannot stall service timing. */
    fun importImage(uri: Uri) {
        if (importing) return
        importing = true
        worker.execute {
            val result = runCatching {
                require(uri.scheme == "content") { "请通过系统图片选择器选择图片" }
                val source = File.createTempFile("block-import-", ".image", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        source.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 20 * 1024 * 1024) { "图片不能超过 20 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: throw IOException("无法读取图片")
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                        val size = info.size
                        require(size.width > 0 && size.height > 0 && size.width.toLong() * size.height <= 100_000_000) {
                            "图片尺寸过大，请换一张图片"
                        }
                        val scale = minOf(1.0, 1440.0 / maxOf(size.width, size.height))
                        decoder.setTargetSize((size.width * scale).roundToInt().coerceAtLeast(1),
                            (size.height * scale).roundToInt().coerceAtLeast(1))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    try {
                        val encoded = ByteArrayOutputStream().use { buffer ->
                            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, buffer))
                            buffer.toByteArray()
                        }
                        synchronized(this) {
                            val output = imageFile.startWrite()
                            try {
                                output.write(encoded)
                                imageFile.finishWrite(output)
                                cachedImage = null
                            } catch (error: Throwable) {
                                imageFile.failWrite(output)
                                throw error
                            }
                        }
                    } finally { bitmap.recycle() }
                } finally { source.delete() }
            }
            main.post {
                importing = false
                importMessage = if (result.isSuccess) {
                    val saved = prefs.edit().putString("mode", BlockThemeMode.CUSTOM.name)
                        .putStringSet("pool", pool + BlockThemes.custom.id)
                        .putBoolean("custom_pool_initialized", true).commit()
                    if (saved) "图片已保存，已启用自定义禁用页并加入随机池"
                    else "图片已保存，请重新选择自定义图片模式"
                } else {
                    val error = result.exceptionOrNull()
                    if (error is IllegalArgumentException) error.message ?: "无法导入这张图片"
                    else "无法导入这张图片，请选择完整的图片文件重试"
                }
                onImportFinished?.invoke()
            }
        }
    }

    companion object {
        // get() always passes applicationContext; no Activity or Service is retained here.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: BlockAppearanceStore? = null
        fun get(context: Context): BlockAppearanceStore = instance ?: synchronized(this) {
            instance ?: BlockAppearanceStore(context.applicationContext).also { instance = it }
        }
    }
}
