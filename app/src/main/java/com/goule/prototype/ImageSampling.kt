package com.goule.prototype

/** Decode no smaller than the actual destination, using BitmapFactory's power-of-two sampling. */
internal fun imageSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    var sample = 1
    val w = targetWidth.coerceAtLeast(1)
    val h = targetHeight.coerceAtLeast(1)
    while (width / sample / 2 >= w && height / sample / 2 >= h) sample *= 2
    return sample
}
