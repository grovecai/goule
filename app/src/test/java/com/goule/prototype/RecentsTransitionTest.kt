package com.goule.prototype

import com.goule.prototype.RecentsTransition.Decision
import org.junit.Assert.assertEquals
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
}
