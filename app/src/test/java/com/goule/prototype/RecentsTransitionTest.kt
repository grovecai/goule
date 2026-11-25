package com.goule.prototype

import com.goule.prototype.RecentsTransition.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsTransitionTest {
    private val recents = RecentsTransition()

    @Test fun closingRecentsDoesNotClearTheResumedApp() {
        assertEquals(Decision.PASS, recents.launcherEvent("home", 100, appeared = true, disappeared = false))
        recents.targetResumed(2_000)
        assertEquals(Decision.DEFER, recents.launcherEvent("home", 2_490, false, false))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 2_491, false, true))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 2_494, false, false))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 2_700, false, false))
    }

    @Test fun regularHomeAndReopeningRecentsStillLeaveTheApp() {
        assertEquals(Decision.PASS, recents.launcherEvent("home", 100, false, false))
        recents.launcherEvent("home", 200, true, false)
        recents.targetResumed(300)
        assertEquals(Decision.PASS, recents.launcherEvent("home", 500, true, false))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 550, false, false))
    }
    @Test fun delayedCloseAndMissingCloseHaveBoundedReturnWindow() {
        recents.launcherEvent("home", 100, true, false)
        recents.targetResumed(200)
        assertEquals(Decision.DEFER, recents.launcherEvent("home", 220, false, false))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 650, false, true))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 655, false, false))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 900, false, false))
        recents.launcherEvent("home", 1_000, true, false)
        recents.targetResumed(1_100)
        assertEquals(Decision.DEFER, recents.launcherEvent("home", 1_120, false, false))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 2_601, false, false))
    }

    @Test fun departedPaneCannotResumeAndOpeningWaitsForLauncherResponse() {
        assertFalse(recents.acceptsTarget(disappeared = true))
        assertTrue(recents.acceptsTarget(disappeared = false))
        recents.openRequested()
        assertFalse(recents.acceptsTarget(disappeared = false))
        recents.launcherEvent("home", 100, true, false)
        assertFalse(recents.acceptsTarget(disappeared = true))
        assertTrue(recents.acceptsTarget(disappeared = false))
        recents.openRequested()
        recents.launcherEvent("home", 200, false, false)
        assertTrue(recents.acceptsTarget(disappeared = false))
        recents.openRequested()
        recents.reset()
        assertTrue(recents.acceptsTarget(disappeared = false))
    }

    @Test fun nonTargetBetweenPaneAndReturnDoesNotLoseClosingState() {
        recents.launcherEvent("home", 100, true, false)
        recents.nonTargetWindow()
        recents.launcherEvent("home", 900, false, false)
        recents.targetResumed(1_000)
        assertEquals(Decision.DEFER, recents.launcherEvent("home", 1_450, false, false))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 1_451, false, true))
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 1_455, false, false))
        // An actual non-target switch after resuming must still discard protection.
        recents.nonTargetWindow()
        assertEquals(Decision.PASS, recents.launcherEvent("home", 1_460, false, false))
    }

    @Test fun interruptedPaneCannotProtectAnUnrelatedLaterLaunch() {
        recents.launcherEvent("home", 100, true, false)
        recents.nonTargetWindow()
        recents.targetResumed(1_601)
        assertEquals(Decision.PASS, recents.launcherEvent("home", 1_602, false, false))
    }

    @Test fun paneCanCloseBeforeTargetResumesWithoutCoveringCards() {
        recents.launcherEvent("home", 100, true, false)
        assertEquals(Decision.PASS, recents.launcherEvent("home", 500, false, true))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 501, false, false))
        recents.targetResumed(520)
        assertEquals(Decision.IGNORE, recents.launcherEvent("home", 525, false, false))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 621, false, false))
        recents.launcherEvent("home", 1_000, true, false)
        recents.launcherEvent("home", 1_100, false, true)
        recents.targetResumed(2_601)
        assertEquals(Decision.PASS, recents.launcherEvent("home", 2_602, false, false))
    }

    @Test fun recentReturnExpiresAndCannotReviveWithoutTarget() {
        recents.launcherEvent("home", 100, true, false)
        assertFalse(recents.returningToTarget(110))
        recents.targetResumed(200)
        assertTrue(recents.returningToTarget(600))
        recents.launcherEvent("home", 650, false, true)
        assertTrue(recents.returningToTarget(770))
        assertTrue(recents.returningToTarget(771))
        assertEquals(Decision.PASS, recents.launcherEvent("home", 771, false, false))
        assertFalse(recents.returningToTarget(772))
        recents.reset()
        assertFalse(recents.returningToTarget(800))
        recents.launcherEvent("home", 1_000, true, false)
        recents.targetResumed(1_100)
        assertFalse(recents.returningToTarget(2_601))
    }

}
