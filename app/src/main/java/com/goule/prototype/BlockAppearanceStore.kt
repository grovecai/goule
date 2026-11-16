package com.goule.prototype

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal fun BlockTheme.artResource(): Int = when ((image as? ThemeImage.Bundled)?.key) {
    "rescue" -> R.drawable.block_rescue
    "magic" -> R.drawable.block_magic
    "kitchen" -> R.drawable.block_kitchen
    "garden" -> R.drawable.block_garden
    "marriage" -> R.drawable.block_marriage
    "work" -> R.drawable.block_work
    "exam" -> R.drawable.block_exam
    "sunlight" -> R.drawable.block_sunlight
    "craving" -> R.drawable.block_craving
    else -> R.drawable.discipline_elder
}

internal data class BlockAppearance(val theme: BlockTheme, val image: Bitmap? = null)

/** One theme collection and independent appearance preferences; never writes the rule store. */
internal class BlockAppearanceStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_appearance", Context.MODE_PRIVATE)
    private val images = File(context.noBackupFilesDir, "block-themes")
    private val draftFile = AtomicFile(File(context.cacheDir, "block-theme-draft.webp"))
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private var generation = 0
    var importing = false
        private set
    var onImportFinished: (() -> Unit)? = null
    private var importMessage: String? = null

    init { migrateLegacy() }

    val mode get() = BlockThemeMode.parse(prefs.getString("mode", null))
    val deletedIds: Set<String> get() = prefs.getStringSet("deleted", emptySet()).orEmpty().toSet()
    val personalThemes get() = ThemeCodec.decode(prefs.getString("personal_themes", null))
    val availableThemes get() = BlockThemes.available(deletedIds, personalThemes)
    val fixedId get() = find(prefs.getString("fixed", null)).id
    val pool: Set<String> get() = (prefs.getStringSet("pool", null)?.intersect(availableThemes.map { it.id }.toSet())
        ?: availableThemes.mapTo(mutableSetOf()) { it.id }).ifEmpty { setOf(BlockThemes.default.id) }
    val summary get() = if (mode == BlockThemeMode.FIXED) "固定 · ${find(fixedId).name}" else "随机 · ${pool.size} 个主题"
    val hasDraftImage get() = draftFile.baseFile.isFile

    fun find(id: String?) = BlockThemes.find(id, availableThemes)
    fun canDelete(id: String) = id != BlockThemes.default.id && availableThemes.any { it.id == id }
    fun canEdit(id: String) = personalThemes.any { it.id == id }
    fun setMode(value: BlockThemeMode) = !importing && prefs.edit().putString("mode", value.name).commit()
    fun setFixed(id: String) = !importing && availableThemes.any { it.id == id } && prefs.edit()
        .putString("fixed", id).putString("mode", BlockThemeMode.FIXED.name).commit()
    fun setPool(ids: Set<String>): Boolean {
        val valid = ids.intersect(availableThemes.map { it.id }.toSet())
        return !importing && valid.isNotEmpty() && prefs.edit().putStringSet("pool", valid).commit()
    }

    fun resolve(theme: BlockTheme? = null): BlockAppearance {
        val chosen = if (theme == null) BlockThemes.choose(mode, fixedId, pool, availableThemes) else find(theme.id)
        val local = chosen.image as? ThemeImage.Local ?: return BlockAppearance(chosen)
        val image = cache.get(chosen.id) ?: decode(imageFile(local.fileName))?.also { cache.put(chosen.id, it) }
        return if (image == null) BlockAppearance(BlockThemes.default) else BlockAppearance(chosen, image)
    }

    private fun imageFile(name: String): AtomicFile {
        require(ThemeCodec.safeFile(name))
        return AtomicFile(File(images, name))
    }

    private fun decode(file: AtomicFile): Bitmap? = try {
        file.openRead().use { BitmapFactory.decodeStream(it) }
    } catch (_: IOException) { null }

    @Synchronized fun draftImage(): Bitmap? = cache.get("__draft") ?: decode(draftFile)?.also { cache.put("__draft", it) }

    @Synchronized fun discardDraft() {
        generation++
        importing = false
        draftFile.delete()
        cache.remove("__draft")
    }

    fun savePersonal(draft: ThemeDraft): BlockTheme? {
        if (importing) return null
        val value = draft.trimmed()
        if (value.error() != null || availableThemes.any { it.name == value.name && it.id != value.id }) return null
        val previous = value.id?.let { id -> personalThemes.firstOrNull { it.id == id } ?: return null }
        val id = previous?.id ?: "user_${UUID.randomUUID()}"
        val previousImage = previous?.image as? ThemeImage.Local
        if (hasDraftImage && draftImage() == null) return null
        if (!hasDraftImage && (previousImage == null || !imageFile(previousImage.fileName).baseFile.isFile)) return null
        val nextImage = if (hasDraftImage) ThemeImage.Local("${UUID.randomUUID()}.webp") else checkNotNull(previousImage)
        val next = BlockTheme(id, value.name, value.headline, value.reminder, "主题「${value.name}」的图片",
            value.closeLabel, value.homeLabel, nextImage)
        try {
            if (hasDraftImage) draftFile.openRead().use { input -> writeAtomic(imageFile(nextImage.fileName)) { input.copyTo(it) } }
            val themes = personalThemes.filterNot { it.id == id } + next
            val edit = prefs.edit().putString("personal_themes", ThemeCodec.encode(themes))
            if (previous == null) edit.putString("fixed", id).putStringSet("pool", pool + id)
            if (!edit.commit()) {
                if (nextImage != previousImage) imageFile(nextImage.fileName).delete()
                return null
            }
            cache.remove(id)
            if (previousImage != null && previousImage != nextImage) imageFile(previousImage.fileName).delete()
            discardDraft()
            return next
        } catch (_: IOException) {
            if (nextImage != previousImage) imageFile(nextImage.fileName).delete()
            return null
        }
    }

    fun deleteTheme(id: String): Boolean {
        if (importing || !canDelete(id)) return false
        val personal = personalThemes.firstOrNull { it.id == id }
        val edit = prefs.edit().putString("fixed", if (fixedId == id) BlockThemes.default.id else fixedId)
            .putStringSet("pool", (pool - id).ifEmpty { setOf(BlockThemes.default.id) })
        if (personal != null) edit.putString("personal_themes", ThemeCodec.encode(personalThemes.filterNot { it.id == id }))
        else edit.putStringSet("deleted", deletedIds + id)
        if (!edit.commit()) return false
        cache.remove(id)
        (personal?.image as? ThemeImage.Local)?.let { imageFile(it.fileName).delete() }
        return true
    }

    private fun migrateLegacy() {
        if (prefs.getBoolean("unified_library", false)) return
        val legacy = AtomicFile(File(context.noBackupFilesDir, "block-image.webp"))
        val personal = mutableListOf<BlockTheme>()
        try {
            if (legacy.baseFile.isFile) {
                val photo = ThemeImage.Local("legacy-custom.webp")
                legacy.openRead().use { input -> writeAtomic(imageFile(photo.fileName)) { input.copyTo(it) } }
                personal.add(BlockTheme("custom", "我的主题", "先放下手机", "把时间留给你真正想做的事。",
                    "从原有图片迁移的主题", image = photo))
            }
            val available = BlockThemes.available(deletedIds, personal)
            val ids = available.map { it.id }.toSet()
            val selected = (prefs.getStringSet("pool", null)?.intersect(ids) ?: ids).toMutableSet()
            if (personal.isNotEmpty() && !prefs.getBoolean("custom_pool_initialized", false)) selected.add("custom")
            val fixed = if (prefs.getString("mode", null) == "CUSTOM" && personal.isNotEmpty()) "custom"
                else BlockThemes.find(prefs.getString("fixed", null), available).id
            val saved = prefs.edit().putString("personal_themes", ThemeCodec.encode(personal))
                .putString("mode", mode.name).putString("fixed", fixed)
                .putStringSet("pool", selected.ifEmpty { setOf(BlockThemes.default.id) })
                .putBoolean("unified_library", true).commit()
            if (saved) legacy.delete()
            else importMessage = "旧主题未能保存，原图片已保留，请稍后重开应用"
        } catch (_: IOException) {
            importMessage = "旧主题图片暂时无法迁移，原文件已保留，请稍后重开应用"
        }
    }

    private fun writeAtomic(file: AtomicFile, write: (java.io.FileOutputStream) -> Unit) {
        val output = file.startWrite()
        try { write(output); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    fun takeImportMessage(): String? = importMessage.also { importMessage = null }

    /** Normalize a temporary draft off the main thread; saved themes remain untouched. */
    fun importImage(uri: Uri) {
        if (importing) return
        importing = true
        val token = synchronized(this) { ++generation }
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
                        val encoded = java.io.ByteArrayOutputStream().use { buffer ->
                            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, buffer))
                            buffer.toByteArray()
                        }
                        synchronized(this) {
                            if (token == generation) {
                                writeAtomic(draftFile) { it.write(encoded) }
                                cache.remove("__draft")
                            }
                        }
                    } finally { bitmap.recycle() }
                } finally { source.delete() }
            }
            main.post {
                if (token != generation) return@post
                importing = false
                importMessage = if (result.isSuccess) "图片已选择，请保存主题"
                    else (result.exceptionOrNull() as? IllegalArgumentException)?.message ?: "图片处理失败，已保存的主题保持不变"
                onImportFinished?.invoke()
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: BlockAppearanceStore? = null
        fun get(context: Context): BlockAppearanceStore = instance ?: synchronized(this) {
            instance ?: BlockAppearanceStore(context.applicationContext).also { instance = it }
        }
    }
}
