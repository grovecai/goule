package com.goule.prototype

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Share immutable pixels, not Drawables (whose bounds and shaders belong to individual views). */
internal object BackdropImages {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Synchronized fun load(resources: Resources, resource: Int, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(resources, resource, bounds)
        val sample = imageSampleSize(bounds.outWidth, bounds.outHeight, width, height)
        val key = "$resource:$sample"
        return cache.get(key) ?: checkNotNull(BitmapFactory.decodeResource(resources, resource,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })).also { cache.put(key, it) }
    }

    fun clear() = cache.evictAll() // Never recycle: an active view may still own these pixels.
    fun retainedBytes() = cache.size()
}
