package com.goule.prototype

/** Known SystemUI dialogs do not replace the underlying app, even across dismissal. */
internal fun isTransientSystemDialog(packageName: String?, className: String?, fullScreen: Boolean): Boolean =
    packageName == "com.android.systemui" && when (className) {
        // ColorOS uses a transparent full-screen window for its floating volume panel.
        "com.oplus.systemui.volume.view.OplusVolumeDialogView\$CustomDialog" -> true
        "android.app.Dialog" -> !fullScreen
        else -> false
    }

/** Only the ColorOS container update observed while the target still held input focus. */
internal class SystemWindowPolicy(manufacturer: String) {
    private val colorOs = manufacturer.equals("OPPO", ignoreCase = true)
    // Captured after the notification shade was collapsed over closing recents.
    // A confirmed target and an unfinished return are mandatory; never restore an empty foreground.
    fun isClosingContainerDuringReturn(packageName: String?, className: String?, fullScreen: Boolean,
        contentChangeTypes: Int, returningToTarget: Boolean,
    ): Boolean = colorOs && returningToTarget && packageName == "com.android.systemui" &&
        className == "android.widget.FrameLayout" && !fullScreen && contentChangeTypes == 32 // PANE_DISAPPEARED

    fun isPassiveContainerUpdate(packageName: String?, className: String?, fullScreen: Boolean,
        contentChangeTypes: Int,
    ): Boolean = colorOs && packageName == "com.android.systemui" &&
        className == "android.widget.FrameLayout" && !fullScreen && contentChangeTypes == 0
}
