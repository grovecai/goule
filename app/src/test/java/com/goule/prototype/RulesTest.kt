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
    fun continuousUsageReleasesGraduallyInsteadOfResettingAtBlockEnd() {
        val full = rule().record(10 * MINUTE, at(10))
        assertEquals(at(10), full.blockedFrom)
        assertEquals(at(130), full.blockedUntil)
        assertTrue(full.isBlocked(at(130) - 1))
        assertFalse(full.isBlocked(at(130)))
        assertEquals(0L, full.remainingUseMs(at(130)))
        assertEquals(1L, full.remainingUseMs(at(130) + 1))
        assertEquals(5 * MINUTE, full.remainingUseMs(at(135)))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(140)))
        assertNull(full.refresh(at(140)).activeUseMinutes)
    }

    @Test
    fun separatedUsageRecoversAtEachSegmentsOwnExpiry() {
        val full = rule().record(5 * MINUTE, at(5)).record(5 * MINUTE, at(65))
        assertEquals(at(130), full.blockedUntil)
        assertEquals(at(65), full.blockedFrom)
        assertEquals(MINUTE, full.remainingUseMs(at(131)))
        assertEquals(5 * MINUTE, full.remainingUseMs(at(135)))
        assertEquals(5 * MINUTE, full.remainingUseMs(at(190)))
        assertEquals(6 * MINUTE, full.remainingUseMs(at(191)))
        assertEquals(10 * MINUTE, full.remainingUseMs(at(195)))
    }

    @Test
    fun partialQuotaSurvivesShortAbsenceAndFullyRecoversAfterLongAbsence() {
        val partial = rule().record(3 * MINUTE, at(3))
        assertEquals(7 * MINUTE, partial.remainingUseMs(at(33)))
        assertEquals(7 * MINUTE, partial.remainingUseMs(at(130)))
        assertEquals(8 * MINUTE, partial.remainingUseMs(at(131)))
        assertEquals(10 * MINUTE, partial.remainingUseMs(at(133)))
        assertEquals(10 * MINUTE, partial.remainingUseMs(at(24 * 60)))
        assertEquals(3 * MINUTE, partial.usedMs) // Reading the list does not consume or mutate usage.
    }

    @Test
    fun fixedWindowBoundaryCannotGrantAnotherFullQuota() {
        val full = rule().record(MINUTE, at(1)).record(9 * MINUTE, at(130))
        val continued = full.record(10 * MINUTE, at(140))
        // Only the first minute has expired; the other nine minutes are still in the window.
        assertEquals(10 * MINUTE, continued.usedMs)
        assertEquals(at(131), continued.blockedFrom)
        assertEquals(at(251), continued.blockedUntil)
        assertEquals(0L, continued.remainingUseMs(at(140)))
        assertEquals(listOf(UsageSpan(at(121), at(131))), continued.usage)
    }

    @Test
    fun continuousUseCanReplaceExpiringUsageWithoutPrematureBlocking() {
        val full = rule().record(5 * MINUTE, at(5)).record(5 * MINUTE, at(65))
        val continued = full.record(5 * MINUTE, at(135))
        assertEquals(10 * MINUTE, continued.usedMs)
        assertEquals(listOf(UsageSpan(at(60), at(65)), UsageSpan(at(130), at(135))), continued.usage)
        assertEquals(at(135), continued.blockedFrom)
        assertEquals(at(190), continued.blockedUntil)
        assertTrue(continued.isBlocked(at(135)))
        // A blocked interval must not get counted after a delayed callback crosses release.
        val delayed = continued.record(60 * MINUTE, at(195))
        assertEquals(listOf(UsageSpan(at(130), at(135))), delayed.usage)
        assertEquals(5 * MINUTE, delayed.remainingUseMs(at(195)))
    }

    @Test
    fun intervalCrossingExpiryAccumulatesOnlyValidForegroundTime() {
        val partial = rule().record(5 * MINUTE, at(5))
        val continued = partial.record(10 * MINUTE, at(135))
        assertEquals(10 * MINUTE, continued.usedMs)
        assertEquals(listOf(UsageSpan(at(125), at(135))), continued.usage)
        assertEquals(at(255), continued.blockedUntil)
    }

    @Test
    fun excessSamplingIsClampedAtExactQuotaAndRepeatedOpenDoesNotMoveDeadline() {
        val full = rule().record(11 * MINUTE, at(11))
        assertEquals(listOf(UsageSpan(start, at(10))), full.usage)
        assertEquals(at(10), full.blockedFrom)
        assertEquals(at(130), full.blockedUntil)
        val repeated = full.record(MINUTE, at(12)).record(MINUTE, at(13))
        assertEquals(full.usage, repeated.usage)
        assertEquals(full.blockedFrom, repeated.blockedFrom)
        assertEquals(full.blockedUntil, repeated.blockedUntil)
    }

    @Test
    fun repeatedAndOverlappingIntervalsNeverCountTwice() {
        val first = rule().record(3 * MINUTE, at(3))
        assertEquals(first, first.record(3 * MINUTE, at(3)))
        val overlap = first.record(3 * MINUTE, at(5))
        assertEquals(listOf(UsageSpan(start, at(5))), overlap.usage)
        assertEquals(5 * MINUTE, overlap.usedMs)
        assertEquals(overlap, overlap.record(MINUTE, at(4)))
    }

    @Test
    fun appUsageAndExpiryRemainIndependentIncludingScreenOffGaps() {
        val a = rule().record(5 * MINUTE, at(5)).record(5 * MINUTE, at(65))
        val b = rule("b").record(3 * MINUTE, at(100))
        assertEquals(0L, a.remainingUseMs(at(100)))
        assertEquals(7 * MINUTE, b.remainingUseMs(at(100)))
        assertEquals(10 * MINUTE, a.remainingUseMs(at(195)))
        assertEquals(7 * MINUTE, b.remainingUseMs(at(195)))
        assertEquals(0L, observedUsage(1_000, 2_000, 2_000, 15_000))
    }

    @Test
    fun midnightAndFormerDayBoundaryNeverResetRecentUsage() {
        val full = rule().record(10 * MINUTE, at(24 * 60 + 5))
        assertEquals(at(24 * 60 + 125), full.blockedUntil)
        assertEquals(0L, full.remainingUseMs(at(24 * 60 + 6)))
        val recent = rule().record(MINUTE, at(1)).record(9 * MINUTE, at(24 * 60))
        assertEquals(MINUTE, recent.remainingUseMs(at(24 * 60)))
        assertEquals(MINUTE, recent.remainingUseMs(at(24 * 60) + 1))
    }

    @Test
    fun editingEitherDurationPreservesBothActivePolicyAndSegments() {
        val partial = rule().record(8 * MINUTE, at(8))
        for ((use, block) in listOf(20 to 120, 5 to 120, 10 to 15, 10 to 180)) {
            val edited = partial.edit(use, block, at(9))
            assertEquals(partial.usage, edited.usage)
            assertEquals(10, edited.activeUseMinutes)
            assertEquals(120, edited.activeBlockMinutes)
            assertEquals(2 * MINUTE, edited.remainingUseMs(at(9)))
            val full = edited.record(2 * MINUTE, at(11))
            assertEquals(at(130), full.blockedUntil)
            assertEquals(MINUTE, full.remainingUseMs(at(131)))
            assertEquals(use * MINUTE, full.remainingUseMs(at(141)))
            val next = full.record(MINUTE, at(142))
            assertEquals(use, next.activeUseMinutes)
            assertEquals(block, next.activeBlockMinutes)
        }
    }

    @Test
    fun editingWhileBlockedDoesNotResetOrExtendReleaseAndKeepsProgressiveRecovery() {
        val full = rule().record(10 * MINUTE, at(10))
        val edited = full.edit(20, 15, at(11)).edit(5, 180, at(16))
        assertEquals(full.blockedUntil, edited.blockedUntil)
        assertEquals(0L, edited.remainingUseMs(at(130)))
        assertEquals(5 * MINUTE, edited.remainingUseMs(at(135)))
        assertEquals(5 * MINUTE, edited.remainingUseMs(at(140)))
    }

    @Test
    fun replacementUsageKeepsOriginalPolicyUntilAllLiveUsageExpires() {
        val edited = rule().record(10 * MINUTE, at(10)).edit(5, 15, at(11))
        val replaced = edited.record(10 * MINUTE, at(140))
        assertEquals(10, replaced.activeUseMinutes)
        assertEquals(120, replaced.activeBlockMinutes)
        assertEquals(10 * MINUTE, replaced.usedMs)
        assertEquals(MINUTE, replaced.remainingUseMs(at(261)))
        assertEquals(5 * MINUTE, replaced.remainingUseMs(at(270)))
    }

    @Test
    fun editingBeforeFirstUseOrAfterAllHistoryExpiresAdoptsLatestPolicy() {
        val unused = rule().edit(5, 15, at(1))
        assertNull(unused.activeUseMinutes)
        assertEquals(5 * MINUTE, unused.remainingUseMs(at(1)))
        val expired = rule().record(MINUTE, at(1)).edit(2, 30, at(131))
        assertNull(expired.activeUseMinutes)
        assertEquals(2 * MINUTE, expired.remainingUseMs(at(131)))
        val first = expired.record(MINUTE, at(132))
        assertEquals(2, first.activeUseMinutes)
        assertEquals(30, first.activeBlockMinutes)
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
    fun removalGuardUsesLatestActualBlockAndSurvivesRecovery() {
        assertEquals(1L, rule().removeWait(at(60) - 1))
        assertEquals(0L, rule().removeWait(at(60)))
        val full = rule().record(10 * MINUTE, at(10))
        val laterBlock = full.record(10 * MINUTE, at(140))
        assertEquals(at(140), laterBlock.blockedFrom)
        assertEquals(60 * MINUTE, laterBlock.removeWait(at(140)))
        assertEquals(1L, laterBlock.removeWait(at(200) - 1))
        assertEquals(0L, laterBlock.refresh(at(300)).removeWait(at(300)))
    }

    @Test
    fun fragmentedObservationsMergeWithoutGrowingStorageForEveryTick() {
        var current = rule()
        for (second in 1L..600L) current = current.record(1_000, start + second * 1_000)
        assertEquals(listOf(UsageSpan(start, at(10))), current.usage)
        assertTrue(current.refresh(at(140)).usage.isEmpty())
        assertEquals(at(10), current.refresh(at(140)).blockedFrom)
    }

    @Test
    fun serviceChecksReleaseAndQuotaBoundariesWithoutBusyLoopingDuringReplacement() {
        val full = rule().record(10 * MINUTE, at(10))
        assertEquals(120 * MINUTE, full.nextCheckDelayMs(at(10)))
        assertEquals(1L, full.nextCheckDelayMs(at(130) - 1))
        assertEquals(1_000L, full.nextCheckDelayMs(at(130)))
        val continuing = full.record(10 * MINUTE - 500, at(140) - 500)
        assertEquals(500L, continuing.nextCheckDelayMs(at(140) - 500))
        val almostFull = rule().record(10 * MINUTE - 100, at(10) - 100)
        assertEquals(100L, almostFull.nextCheckDelayMs(at(10) - 100))
    }

    @Test
    fun subsecondPartitionsAgreeWithWholeIntervalAcrossExpiryAndExhaustion() {
        val full = Rule("a", "A", 1, 15, start).record(20_000, start + 20_000)
            .record(40_000, start + 90_000)
        val release = start + 16 * MINUTE
        val whole = full.record(20_500, release + 20_500)
        var partitioned = full
        var elapsed = 0L
        while (elapsed < 20_500) {
            val delta = minOf(23, 20_500 - elapsed)
            elapsed += delta
            partitioned = partitioned.record(delta, release + elapsed)
        }
        assertEquals(whole, partitioned)
        assertEquals(release + 20_000, whole.blockedFrom)
        assertEquals(start + 16 * MINUTE + 50_000, whole.blockedUntil)
    }

    @Test
    fun deterministicTimelineMatchesIndependentSlidingRingAndNeverExceedsQuota() {
        for ((use, block) in listOf(1 to 15, 10 to 120)) compareTimeline(use, block)
    }

    private fun compareTimeline(use: Int, block: Int) {
        // Independent one-second occupancy model across multiple windows, including a full
        // initial burst, 1:1 replacement at expiry, fragmented use and long inactivity.
        val random = Random(20261113)
        val quota = use * 60
        val window = (use + block) * 60
        val ring = BooleanArray(window)
        var occupied = 0
        var current = Rule("a", "A", use, block, start)
        repeat(9 * window) { second ->
            val index = second % window
            if (ring[index]) occupied--
            val wantsUse = when {
                second < 2 * window -> true
                second in 5 * window..7 * window -> false
                else -> random.nextInt(100) < 35
            }
            ring[index] = wantsUse && occupied < quota
            if (ring[index]) occupied++
            val now = start + (second + 1) * 1_000L
            current = if (wantsUse) current.record(1_000, now) else current.refresh(now)
            assertEquals("second=$second", (quota - occupied) * 1_000L, current.remainingUseMs(now))
            assertTrue(current.usedMs <= quota * 1_000L)
            assertEquals("blocked second=$second", occupied == quota && !ring[(second + 1) % window],
                current.isBlocked(now))
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
