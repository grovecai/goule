package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    private val start = 100 * DAY
    private fun at(minutes: Long) = start + minutes * MINUTE
    private fun rule(name: String = "a") = Rule(name, name, 10, 120, start)

    @Test
    fun firstUseAnchorsCycleRatherThanCreationOrScreenOpen() {
        val unused = rule()
        assertNull(unused.refresh(at(60)).cycleStart)
        val first = unused.record(MINUTE, at(61))
        assertEquals(at(60), first.cycleStart)
        assertEquals(at(190), first.cycleEnd)
        assertEquals(9 * MINUTE, first.remainingUseMs(at(189)))
        assertEquals(10 * MINUTE, first.remainingUseMs(at(190)))
    }

    @Test
    fun fullQuotaResetsAllAtOnceAtExactBoundary() {
        val full = rule().record(10 * MINUTE, at(10))
        assertEquals(at(10), full.blockedFrom)
        assertEquals(at(130), full.blockedUntil)
        assertTrue(full.isBlocked(at(130) - 1))
        assertEquals(0L, full.remainingUseMs(at(130) - 1))
        assertFalse(full.isBlocked(at(130)))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(130)))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(130) + 1))
        assertEquals(at(130), full.refresh(at(130)).cycleStart)
        assertEquals(at(260), full.refresh(at(130)).cycleEnd)
    }

    @Test
    fun partialQuotaNeverGrowsBetweenResetsEvenWhenOldRollingSegmentsWouldExpire() {
        val partial = rule().record(5 * MINUTE, at(5)).record(2 * MINUTE, at(65))
        for (minute in 65L..129L) assertEquals(3 * MINUTE, partial.remainingUseMs(at(minute)))
        val next = partial.refresh(at(130)).record(2 * MINUTE, at(135))
        // Former rolling expiry at 130–135 must not release any more quota.
        for (minute in 135L..259L) assertEquals(8 * MINUTE, next.remainingUseMs(at(minute)))
        assertEquals(10 * MINUTE, next.remainingUseMs(at(260)))
    }

    @Test
    fun splitUseExhaustsSameCycleAndWaitsOnlyUntilItsFixedEnd() {
        val full = rule().record(5 * MINUTE, at(5)).record(5 * MINUTE, at(65))
        assertEquals(at(65), full.blockedFrom)
        assertEquals(at(130), full.blockedUntil)
        assertEquals(0L, full.remainingUseMs(at(129)))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(130)))
    }

    @Test
    fun fixedCyclesIntentionallyAllowUsingTailThenNextCyclesQuota() {
        val full = rule().record(MINUTE, at(1)).record(9 * MINUTE, at(130))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(130)))
        val next = full.record(10 * MINUTE, at(140))
        assertEquals(at(130), next.cycleStart)
        assertEquals(10 * MINUTE, next.usedMs)
        assertEquals(at(140), next.blockedFrom)
        assertEquals(at(260), next.blockedUntil)
    }

    @Test
    fun idleCyclesKeepOriginalPhaseWithoutAccumulatingUnusedQuota() {
        val partial = rule().record(3 * MINUTE, at(3))
        val idle = partial.refresh(at(391))
        assertEquals(at(390), idle.cycleStart)
        assertEquals(at(520), idle.cycleEnd)
        assertEquals(10 * MINUTE, idle.remainingUseMs(at(391)))
        val resumed = idle.record(MINUTE, at(392))
        assertEquals(at(390), resumed.cycleStart)
        assertEquals(9 * MINUTE, resumed.remainingUseMs(at(400)))
        assertEquals(10 * MINUTE, resumed.remainingUseMs(at(520)))
    }

    @Test
    fun cycleLengthAndAnchorAreIndependentForDifferentActualRules() {
        val a = rule().record(10 * MINUTE, at(10))
        val b = Rule("b", "B", 2, 15, start).record(2 * MINUTE, at(42))
        val c = Rule("c", "C", 20, 30, start).record(5 * MINUTE, at(5))
        assertEquals(at(130), a.blockedUntil)
        assertEquals(at(57), b.blockedUntil)
        assertEquals(at(50), c.cycleEnd)
        assertEquals(2 * MINUTE, b.remainingUseMs(at(57)))
        assertEquals(0L, a.remainingUseMs(at(57)))
        assertEquals(20 * MINUTE, c.remainingUseMs(at(57)))
    }

    @Test
    fun sampleStraddlingBoundaryCountsOnlyItsNewCyclePart() {
        val partial = rule().record(9 * MINUTE, at(9))
        val crossed = partial.record(1_000, at(130) + 400)
        assertEquals(at(130), crossed.cycleStart)
        assertEquals(400L, crossed.usedMs)
        assertEquals(10 * MINUTE - 400, crossed.remainingUseMs(at(130) + 400))
    }

    @Test
    fun quotaExhaustedBeforeBoundaryDoesNotCountCoveredRemainder() {
        val partial = rule().record(10 * MINUTE - 300, at(10) - 300)
        val crossed = partial.record(1_000, at(130) + 400)
        assertEquals(at(130) - 300, crossed.blockedFrom)
        assertEquals(0L, crossed.usedMs)
        assertEquals(at(130), crossed.cycleStart)
        assertEquals(10 * MINUTE, crossed.remainingUseMs(at(130) + 400))
        val alreadyBlocked = rule().record(10 * MINUTE, at(10)).record(1_000, at(130) + 400)
        assertEquals(0L, alreadyBlocked.usedMs)
    }

    @Test
    fun quotaExhaustedExactlyAtBoundaryStartsNextCycleWithoutBlockingIt() {
        val partial = rule().record(9 * MINUTE, at(9))
        val crossed = partial.record(MINUTE + 400, at(130) + 400)
        assertEquals(400L, crossed.usedMs)
        assertNull(crossed.blockedFrom)
        assertFalse(crossed.isBlocked(at(130) + 400))
    }

    @Test
    fun overrunAndRepeatedReentryCannotMoveResetOrCountTwice() {
        val full = rule().record(11 * MINUTE, at(11))
        assertEquals(10 * MINUTE, full.usedMs)
        assertEquals(at(10), full.blockedFrom)
        val repeated = full.record(MINUTE, at(12)).record(MINUTE, at(13))
        assertEquals(full.usedMs, repeated.usedMs)
        assertEquals(full.blockedFrom, repeated.blockedFrom)
        assertEquals(at(130), repeated.blockedUntil)
    }

    @Test
    fun repeatedOverlappingAndOutOfOrderIntervalsDoNotAddUsage() {
        val first = rule().record(3 * MINUTE, at(3))
        assertEquals(first, first.record(3 * MINUTE, at(3)))
        val overlap = first.record(3 * MINUTE, at(5))
        assertEquals(5 * MINUTE, overlap.usedMs)
        assertEquals(overlap, overlap.record(MINUTE, at(4)))
        val idle = overlap.refresh(at(260))
        assertEquals(idle, idle.record(MINUTE, at(100)))
    }

    @Test
    fun editingEitherDurationPreservesCurrentQuotaAndEndThenAdoptsLatestValues() {
        val partial = rule().record(8 * MINUTE, at(8))
        for ((use, block) in listOf(20 to 120, 5 to 120, 10 to 15, 10 to 180)) {
            val edited = partial.edit(use, block, at(9))
            assertEquals(partial.cycleStart, edited.cycleStart)
            assertEquals(partial.usedMs, edited.usedMs)
            assertEquals(at(130), edited.cycleEnd)
            assertEquals(2 * MINUTE, edited.remainingUseMs(at(129)))
            val full = edited.record(2 * MINUTE, at(11))
            assertEquals(at(130), full.blockedUntil)
            val next = full.refresh(at(130))
            assertEquals(use * MINUTE, next.remainingUseMs(at(130)))
            assertEquals(at(130L + use + block), next.cycleEnd)
            assertEquals(use, next.activeUseMinutes)
            assertEquals(block, next.activeBlockMinutes)
        }
    }

    @Test
    fun multipleEditsTakeEffectAtNextBoundaryAndKeepPhaseThroughIdleCycles() {
        val edited = rule().record(10 * MINUTE, at(10)).edit(20, 15, at(11)).edit(5, 15, at(16))
        assertEquals(at(130), edited.blockedUntil)
        assertEquals(0L, edited.remainingUseMs(at(129)))
        val advanced = edited.refresh(at(171))
        assertEquals(at(170), advanced.cycleStart)
        assertEquals(at(190), advanced.cycleEnd)
        assertEquals(5 * MINUTE, advanced.remainingUseMs(at(171)))
        assertEquals(5, advanced.activeUseMinutes)
    }

    @Test
    fun unusedRuleEditsImmediatelyButStartedIdleCycleKeepsItsPolicyUntilBoundary() {
        val unused = rule().edit(5, 15, at(1))
        assertNull(unused.cycleStart)
        assertEquals(5 * MINUTE, unused.remainingUseMs(at(1)))
        assertEquals(at(21), unused.record(MINUTE, at(2)).cycleEnd)
        val idle = rule().record(MINUTE, at(1)).refresh(at(131)).edit(2, 30, at(131))
        assertEquals(10 * MINUTE, idle.remainingUseMs(at(131)))
        assertEquals(at(260), idle.cycleEnd)
        assertEquals(2 * MINUTE, idle.remainingUseMs(at(260)))
        assertEquals(at(292), idle.refresh(at(260)).cycleEnd)
    }

    @Test
    fun noOpEditsAndFiveMinuteGuardRemainIntact() {
        val edited = rule().record(MINUTE, at(1)).edit(5, 15, at(2))
        assertEquals(1L, edited.editWait(at(7) - 1))
        assertEquals(0L, edited.editWait(at(7)))
        assertEquals(edited, edited.edit(5, 15, at(3)))
        try {
            edited.edit(6, 15, at(7) - 1)
            throw AssertionError("An early change was accepted")
        } catch (_: IllegalStateException) { /* expected */ }
        assertEquals(6, edited.edit(6, 15, at(7)).useMinutes)
    }

    @Test
    fun removalGuardUsesMostRecentBlockAndSurvivesReset() {
        assertEquals(1L, rule().removeWait(at(60) - 1))
        assertEquals(0L, rule().removeWait(at(60)))
        val laterBlock = rule().record(10 * MINUTE, at(10)).record(10 * MINUTE, at(140))
        assertEquals(at(140), laterBlock.blockedFrom)
        assertEquals(60 * MINUTE, laterBlock.removeWait(at(140)))
        assertEquals(1L, laterBlock.removeWait(at(200) - 1))
        assertEquals(at(140), laterBlock.refresh(at(300)).blockedFrom)
    }

    @Test
    fun serviceChecksFixedResetOrQuotaWithoutAnyRollingReleaseBoundary() {
        val full = rule().record(10 * MINUTE, at(10))
        assertEquals(120 * MINUTE, full.nextCheckDelayMs(at(10)))
        assertEquals(1L, full.nextCheckDelayMs(at(130) - 1))
        assertEquals(1_000L, full.nextCheckDelayMs(at(130)))
        val partial = rule().record(MINUTE, at(1))
        assertEquals(500L, partial.nextCheckDelayMs(at(130) - 500))
        val almostFull = rule().record(10 * MINUTE - 100, at(10) - 100)
        assertEquals(100L, almostFull.nextCheckDelayMs(at(10) - 100))
    }

    @Test
    fun midnightAndFormerDayBoundaryDoNotAddExtraResets() {
        val partial = rule().record(2 * MINUTE, at(2))
        val recent = partial.record(3 * MINUTE, at(24 * 60))
        assertEquals(at(1430), recent.cycleStart)
        assertEquals(7 * MINUTE, recent.remainingUseMs(at(24 * 60)))
        assertEquals(7 * MINUTE, recent.remainingUseMs(at(24 * 60) + 1))
    }

    @Test
    fun subsecondPartitionsAgreeAcrossAResetBoundary() {
        val partial = rule().record(MINUTE, at(1))
        val end = at(130) + 500
        val whole = partial.record(1_000, end)
        var partitioned = partial
        var elapsed = 0L
        while (elapsed < 1_000) {
            val delta = minOf(23, 1_000 - elapsed)
            elapsed += delta
            partitioned = partitioned.record(delta, end - 1_000 + elapsed)
        }
        assertEquals(whole, partitioned)
        assertEquals(500L, whole.usedMs)
    }

    @Test
    fun deterministicTimelineMatchesIndependentFixedCycleCounter() {
        for ((use, block) in listOf(1 to 15, 10 to 120, 20 to 30)) {
            val random = Random(20261113)
            val quota = use * 60
            val period = (use + block) * 60
            var occupied = 0
            var current = Rule("a", "A", use, block, start)
            repeat(5 * period) { second ->
                val wantsUse = second < 2 * period || (second !in 2 * period..3 * period && random.nextBoolean())
                if (wantsUse && occupied < quota) occupied++
                if ((second + 1) % period == 0) occupied = 0
                val now = start + (second + 1) * 1_000L
                current = if (wantsUse) current.record(1_000, now) else current.refresh(now)
                assertEquals("$use/$block second=$second", (quota - occupied) * 1_000L, current.remainingUseMs(now))
                assertEquals(occupied == quota, current.isBlocked(now))
            }
        }
    }

    @Test
    fun sleepAndDelayedCallbacksAreExcludedFromActiveUsage() {
        assertEquals(1_000L, observedUsage(1_000, 2_000, 2_000, 3_000))
        assertEquals(0L, observedUsage(1_000, 2_000, 2_000, 15_000))
        assertEquals(0L, observedUsage(1_000, 2_000, 14_000, 15_000))
        assertTrue(observedUsage(1_000, 2_000, 1_100, 2_102) > 0)
    }

    @Test
    fun persistedClockIgnoresSameBootWallChangesAndNeverRewindsAcrossReboot() {
        assertEquals(start + MINUTE, restoreRuleTime(start, 10_000, 70_000, start + DAY, true))
        assertEquals(start + MINUTE, restoreRuleTime(start, 10_000, 70_000, start - DAY, true))
        assertEquals(start, restoreRuleTime(start, 10_000, 1_000, start - DAY, false))
        assertEquals(start + HOUR, restoreRuleTime(start, 10_000, 1_000, start + HOUR, false))
    }
}
