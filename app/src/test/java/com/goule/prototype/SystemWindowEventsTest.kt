package com.goule.prototype

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWindowEventsTest {
    @Test fun floatingSystemDialogDoesNotReplaceTheUnderlyingApp() {
        assertTrue(isTransientSystemDialog("com.android.systemui", "android.app.Dialog", false))
    }

    @Test fun colorOsVolumePanelUsesATransparentFullscreenDialog() {
        val volume = "com.oplus.systemui.volume.view.OplusVolumeDialogView\$CustomDialog"
        assertTrue(isTransientSystemDialog("com.android.systemui", volume, true))
        assertTrue(isTransientSystemDialog("com.android.systemui", volume, false))
        assertFalse(isTransientSystemDialog("other.app", volume, true))
        assertFalse(isTransientSystemDialog("com.android.systemui", "com.oplus.systemui.OtherDialog", true))
    }

    @Test fun notificationShadeAndFullscreenSystemWindowsStillLeaveTheApp() {
        assertFalse(isTransientSystemDialog("com.android.systemui", "com.android.systemui.shade.NotificationShadeWindowView", true))
        assertFalse(isTransientSystemDialog("com.android.systemui", "android.app.Dialog", true))
        assertFalse(isTransientSystemDialog("com.android.systemui", "android.widget.FrameLayout", false))
    }

    @Test fun otherAppsAndUnknownWindowsAreNotIgnored() {
        for (name in listOf("com.android.settings", "com.android.dialer", "target", null)) {
            assertFalse(isTransientSystemDialog(name, "android.app.Dialog", false))
        }
        assertFalse(isTransientSystemDialog("com.android.systemui", null, false))
    }
}
