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

    @Test fun colorOsRootUpdateKeepsTheUnderlyingTargetButPaneChangesDoNot() {
        val policy = SystemWindowPolicy("OPPO")
        assertTrue(policy.isPassiveContainerUpdate("com.android.systemui", "android.widget.FrameLayout", false, 0))
        for (changes in listOf(1, 16, 32, 16 or 32, 64)) {
            assertFalse(policy.isPassiveContainerUpdate("com.android.systemui", "android.widget.FrameLayout", false, changes))
        }
    }

    @Test fun colorOsContainerExceptionDoesNotBroadenOtherWindowsOrVendors() {
        for (manufacturer in listOf("Google", "Samsung", "OnePlus", "", "unknown")) {
            assertFalse(SystemWindowPolicy(manufacturer).isPassiveContainerUpdate(
                "com.android.systemui", "android.widget.FrameLayout", false, 0))
        }
        val policy = SystemWindowPolicy("OPPO")
        assertFalse(policy.isPassiveContainerUpdate("com.android.systemui", "android.widget.FrameLayout", true, 0))
        assertFalse(policy.isPassiveContainerUpdate("com.android.systemui", "android.widget.ImageView", false, 0))
        assertFalse(policy.isPassiveContainerUpdate("com.android.systemui", null, false, 0))
        for (name in listOf("com.android.settings", "target", null)) {
            assertFalse(policy.isPassiveContainerUpdate(name, "android.widget.FrameLayout", false, 0))
        }
    }
}
