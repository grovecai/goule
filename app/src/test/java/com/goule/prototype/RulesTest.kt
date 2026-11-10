package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val start = 100 * DAY
    private fun rule(name: String = "a") = Rule(name, name, 1, 15, start)

    @Test
    fun foregroundSegmentsAccumulateIndependentlyAndFreezeOnlyOnce() {
        val first = rule().record(30_000, start + 30_000)
        val second = rule("b").record(20_000, start + 80_000)
        val blocked = first.record(31_000, start + 150_000)
        assertEquals(20_000L, second.usedMs)
        assertNull(second.blockedUntil)
        assertEquals(start + 149_000, blocked.blockedFrom)
        assertEquals(blocked, blocked.record(1_000, start + 151_000))
        val expired = blocked.refresh(checkNotNull(blocked.blockedUntil))
        assertEquals(0L, expired.usedMs)
        assertNull(expired.blockedUntil)
        assertEquals(blocked.blockedFrom, expired.blockedFrom)
    }

    @Test
    fun dayRolloverCountsOnlyNewWindowUnlessOldQuotaWasReached() {
        val before = rule().record(50_000, start + 50_000)
        val rollover = before.record(1_000, start + DAY + 400)
        assertEquals(400L, rollover.usedMs)
        assertEquals(start + DAY, rollover.windowStart)
        val almostFull = before.copy(usedMs = MINUTE - 300)
        val blocked = almostFull.record(1_000, start + DAY + 400)
        assertEquals(start + DAY - 300, blocked.blockedFrom)
    }

    @Test
    fun editsPreserveCurrentBlockAndEnforceFiveMinuteBoundary() {
        val blocked = rule().record(MINUTE, start + MINUTE)
        val edited = blocked.edit(2, 25, start + 2 * MINUTE)
        assertEquals(blocked.blockedUntil, edited.blockedUntil)
        assertEquals(25, edited.blockMinutes)
        assertEquals(1L, edited.editWait(start + 7 * MINUTE - 1))
        assertEquals(0L, edited.editWait(start + 7 * MINUTE))
        assertEquals(edited, edited.edit(2, 25, start + 8 * MINUTE))
    }

    @Test
    fun removalWaitUsesLaterOfCreationAndLatestBlockEvenAfterExpiry() {
        assertEquals(1L, rule().removeWait(start + HOUR - 1))
        assertEquals(0L, rule().removeWait(start + HOUR))
        val blocked = rule().record(MINUTE, start + 2 * HOUR)
        val expired = blocked.refresh(start + 2 * HOUR + 15 * MINUTE)
        assertFalse(expired.isBlocked(start + 2 * HOUR + 15 * MINUTE))
        assertEquals(45 * MINUTE, expired.removeWait(start + 2 * HOUR + 15 * MINUTE))
        assertEquals(0L, expired.removeWait(start + 3 * HOUR))
    }

    @Test
    fun sleepAndDelayedCallbacksAreExcludedFromActiveUsage() {
        assertEquals(1_000L, observedUsage(1_000, 2_000, 2_000, 3_000))
        assertEquals(0L, observedUsage(1_000, 2_000, 2_000, 15_000))
        assertEquals(0L, observedUsage(1_000, 2_000, 14_000, 15_000))
        assertTrue(observedUsage(1_000, 2_000, 1_100, 2_102) > 0)
    }

    @Test
    fun persistedClockContinuesOnSameBootAndDoesNotGoBackwardsAcrossReboot() {
        assertEquals(start + MINUTE, restoreRuleTime(start, 10_000, 70_000, start + DAY, true))
        assertEquals(start + MINUTE, restoreRuleTime(start, 10_000, 70_000, start - DAY, true))
        assertEquals(start, restoreRuleTime(start, 10_000, 1_000, start - DAY, false))
        assertEquals(start + HOUR, restoreRuleTime(start, 10_000, 1_000, start + HOUR, false))
    }

}
