package com.goule.prototype

/** Known SystemUI dialogs do not replace the underlying app, even across dismissal. */
internal fun isTransientSystemDialog(packageName: String?, className: String?, fullScreen: Boolean): Boolean =
    packageName == "com.android.systemui" && when (className) {
        // ColorOS uses a transparent full-screen window for its floating volume panel.
        "com.oplus.systemui.volume.view.OplusVolumeDialogView\$CustomDialog" -> true
        "android.app.Dialog" -> !fullScreen
        else -> false
    }
