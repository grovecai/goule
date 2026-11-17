package com.goule.prototype

import android.app.Application
import android.content.ComponentCallbacks2

class GouleApplication : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Modern Android still reports UI_HIDDEN; do not rely on deprecated low-memory levels.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level in 1..15) trimImageCaches()
    }
}

internal fun trimImageCaches() {
    BackdropImages.clear()
    BlockAppearanceStore.trimCache()
}
