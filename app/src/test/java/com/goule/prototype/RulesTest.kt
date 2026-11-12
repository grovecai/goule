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
    fun remainingQuotaReflectsIndependentSegmentsWithoutCountingTimeOnList() {
        val first = rule().record(12_345, start + 12_345).record(10_000, start + MINUTE)
        val second = rule("b").record(5_000, start + MINUTE)
        assertEquals(37_655L, first.remainingUseMs(start + MINUTE))
        assertEquals(37_655L, first.remainingUseMs(start + HOUR))
        assertEquals(55_000L, second.remainingUseMs(start + HOUR))
        assertEquals(22_345L, first.usedMs)
    }

    @Test
    fun remainingQuotaIsZeroWhileBlockedIncludingAfterEditingThenResetsAtExpiry() {
        val blocked = rule().record(MINUTE, start + MINUTE)
        val edited = blocked.edit(2, 25, start + 2 * MINUTE)
        val until = checkNotNull(blocked.blockedUntil)
        assertEquals(0L, blocked.remainingUseMs(until - 1))
        assertEquals(0L, edited.remainingUseMs(until - 1))
        assertEquals(MINUTE, blocked.remainingUseMs(until))
        assertEquals(2 * MINUTE, edited.remainingUseMs(until))
    }

    @Test
    fun remainingQuotaResetsAtDayBoundaryButDoesNotOverrideActiveBlock() {
        val partial = rule().record(20_000, start + 20_000)
        assertEquals(40_000L, partial.remainingUseMs(start + DAY - 1))
        assertEquals(MINUTE, partial.remainingUseMs(start + DAY))
        val blocked = partial.record(40_000, start + DAY - 1)
        assertEquals(0L, blocked.remainingUseMs(start + DAY))
        assertEquals(40_000L, partial.edit(1, 30, start + HOUR).remainingUseMs(start + HOUR))
    }

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
    fun changingOnlyBlockDurationPreservesUsageAndWindowThenUsesNewBlockDuration() {
        val partial = rule().record(20_000, start + 20_000)
        val edited = partial.edit(1, 30, start + MINUTE)
        assertEquals(partial.windowStart, edited.windowStart)
        assertEquals(partial.usedMs, edited.usedMs)
        assertEquals(40_000L, edited.remainingUseMs(start + MINUTE))
        val blocked = edited.record(40_500, start + 2 * MINUTE)
        assertEquals(start + 2 * MINUTE - 500, blocked.blockedFrom)
        assertEquals(30 * MINUTE, checkNotNull(blocked.blockedUntil) - checkNotNull(blocked.blockedFrom))
    }

    @Test
    fun increasingQuotaDoesNotExtendCurrentRoundAndNextRoundUsesLatestQuota() {
        val partial = Rule("a", "a", 20, 30, start).record(8 * MINUTE, start + 8 * MINUTE)
        val edited = partial.edit(40, 30, start + 9 * MINUTE)
        assertEquals(12 * MINUTE, edited.remainingUseMs(start + 9 * MINUTE))
        val blocked = edited.record(12 * MINUTE, start + 21 * MINUTE)
        assertEquals(start + 21 * MINUTE, blocked.blockedFrom)
        val until = checkNotNull(blocked.blockedUntil)
        assertEquals(40 * MINUTE, blocked.remainingUseMs(until))
        val next = blocked.record(MINUTE, until + MINUTE)
        assertEquals(40, next.windowUseMinutes)
        assertEquals(39 * MINUTE, next.remainingUseMs(until + MINUTE))
    }

    @Test
    fun decreasingQuotaBelowUsedTimeStillFinishesOriginalRound() {
        val partial = Rule("a", "a", 20, 30, start).record(8 * MINUTE, start + 8 * MINUTE)
        val edited = partial.edit(5, 15, start + 9 * MINUTE)
        assertEquals(12 * MINUTE, edited.remainingUseMs(start + 9 * MINUTE))
        assertFalse(edited.isBlocked(start + 9 * MINUTE))
        val blocked = edited.record(12 * MINUTE, start + 21 * MINUTE)
        assertEquals(start + 21 * MINUTE, blocked.blockedFrom)
        assertEquals(5 * MINUTE, blocked.remainingUseMs(checkNotNull(blocked.blockedUntil)))
    }

    @Test
    fun repeatedEditsKeepEarliestQuotaAndDoNotTouchAnotherApp() {
        val partial = rule().record(20_000, start + 20_000)
        val other = rule("b").record(10_000, start + 10_000)
        val edited = partial.edit(2, 30, start + MINUTE)
            .record(10_000, start + 2 * MINUTE)
            .edit(3, 45, start + 6 * MINUTE)
        assertEquals(1, edited.windowUseMinutes)
        assertEquals(start, edited.windowStart)
        assertEquals(30_000L, edited.remainingUseMs(start + 6 * MINUTE))
        assertEquals(50_000L, other.remainingUseMs(start + 6 * MINUTE))
        assertEquals(edited, edited.edit(3, 45, start + 7 * MINUTE))
    }

    @Test
    fun editsDoNotMoveDayBoundaryAndRolloverUsesLatestQuota() {
        val edited = rule().record(20_000, start + 20_000).edit(2, 30, start + HOUR)
        assertEquals(40_000L, edited.remainingUseMs(start + DAY - 1))
        assertEquals(2 * MINUTE, edited.remainingUseMs(start + DAY))
        val rollover = edited.record(1_000, start + DAY + 400)
        assertEquals(start + DAY, rollover.windowStart)
        assertEquals(400L, rollover.usedMs)
        assertEquals(2, rollover.windowUseMinutes)
        val almostFull = edited.copy(usedMs = MINUTE - 300)
        val blocked = almostFull.record(1_000, start + DAY + 400)
        assertEquals(start + DAY - 300, blocked.blockedFrom)
        assertEquals(0L, blocked.remainingUseMs(start + DAY + 400))
    }

    @Test
    fun editingBeforeFirstUseOrAfterExpiryUsesLatestQuotaImmediately() {
        val unused = rule().edit(2, 30, start + MINUTE)
        assertNull(unused.windowUseMinutes)
        assertEquals(2 * MINUTE, unused.remainingUseMs(start + MINUTE))
        val first = unused.record(10_000, start + 2 * MINUTE)
        assertEquals(2, first.windowUseMinutes)
        val expired = rule().record(MINUTE, start + MINUTE)
            .edit(3, 30, start + HOUR)
        assertEquals(3 * MINUTE, expired.remainingUseMs(start + HOUR))
        assertNull(expired.windowUseMinutes)
        val dayExpired = rule().record(20_000, start + 20_000).edit(4, 30, start + DAY)
        assertEquals(4 * MINUTE, dayExpired.remainingUseMs(start + DAY))
        assertNull(dayExpired.windowStart)
    }

    @Test
    fun legacyInProgressRuleCapturesStoredQuotaBeforeFirstEdit() {
        val legacy = rule().copy(windowStart = start, usedMs = 20_000)
        val edited = legacy.edit(2, 30, start + MINUTE)
        assertEquals(1, edited.windowUseMinutes)
        assertEquals(40_000L, edited.remainingUseMs(start + MINUTE))
        assertEquals(start, edited.windowStart)
        assertEquals(start + 2 * MINUTE, edited.record(40_000, start + 2 * MINUTE).blockedFrom)
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
