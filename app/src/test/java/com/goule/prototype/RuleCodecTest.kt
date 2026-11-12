package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCodecTest {
    private val start = 100 * DAY
    private fun at(minutes: Long) = start + minutes * MINUTE

    @Test
    fun savedRulesRestoreCycleUsageSnapshotsAndIndependentResetTimes() {
        val first = Rule("a", "甲", 10, 120, start).record(8 * MINUTE, at(8)).edit(5, 15, at(9))
        val second = Rule("b", "乙", 2, 15, start).record(MINUTE, at(41))
        val loaded = decodeRules(encodeRules(listOf(first, second)))
        assertEquals(listOf(first, second), loaded)
        assertEquals(2 * MINUTE, loaded[0].remainingUseMs(at(129)))
        assertEquals(5 * MINUTE, loaded[0].remainingUseMs(at(130)))
        assertEquals(at(57), loaded[1].cycleEnd)
        assertEquals(MINUTE, loaded[1].remainingUseMs(at(56)))
        assertEquals(first, loaded[0].record(8 * MINUTE, at(8)))
    }

    @Test
    fun rollingSegmentsBecomeOneStableAggregateAndKeepCurrentDeadline() {
        val raw = rolling("[[${at(0)},${at(5)}],[${at(60)},${at(65)}]]")
        val loaded = decodeRules(raw, at(65)).single()
        assertEquals(10 * MINUTE, loaded.usedMs)
        assertEquals(start, loaded.cycleStart)
        assertEquals(at(130), loaded.blockedUntil)
        assertEquals(0L, loaded.remainingUseMs(at(129)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(130)))
        assertEquals(loaded, decodeRules(encodeRules(listOf(loaded))).single())
    }

    @Test
    fun rollingReleaseAlreadyUnderwayRetainsSavedQuotaInNewFixedCycle() {
        val raw = rolling("[[${at(5)},${at(10)}],[${at(60)},${at(65)}]]", activeUse = 20)
        // Use a saved time at the old first-release boundary; existing aggregate is retained.
        val loaded = decodeRules(raw, at(145)).single()
        assertEquals(at(145), loaded.cycleStart)
        assertEquals(10 * MINUTE, loaded.usedMs)
        for (minute in 145L..284L) assertEquals(10 * MINUTE, loaded.remainingUseMs(at(minute)))
        val restored = decodeRules(encodeRules(listOf(loaded)), at(200)).single()
        assertEquals(loaded.cycleStart, restored.cycleStart)
        assertEquals(10 * MINUTE, restored.remainingUseMs(at(200)))
        assertEquals(10 * MINUTE, restored.remainingUseMs(at(285))) // configured quota becomes 10
    }

    @Test
    fun rollingZeroUsageWithLegacyBlockKeepsItsRestriction() {
        val raw = rolling("[]", until = at(160))
        val loaded = decodeRules(raw, at(100)).single()
        assertEquals(at(100), loaded.cycleStart)
        assertTrue(loaded.isBlocked(at(159)))
        assertEquals(0L, loaded.remainingUseMs(at(159)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(160)))
    }

    @Test
    fun olderAggregateUsesOriginalAnchorIfStillValidAndPreservesQuotaSnapshot() {
        val raw = legacy(8 * MINUTE, quota = 20)
        val loaded = decodeRules(raw, at(60)).single()
        assertEquals(start, loaded.cycleStart)
        assertEquals(20, loaded.activeUseMinutes)
        assertEquals(at(140), loaded.cycleEnd)
        assertEquals(12 * MINUTE, loaded.remainingUseMs(at(139)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(140)))
    }

    @Test
    fun olderStaleAnchorDoesNotClearSavedPartialQuotaOnModelSwitch() {
        val loaded = decodeRules(legacy(5 * MINUTE), at(400)).single()
        assertEquals(at(400), loaded.cycleStart)
        assertEquals(5 * MINUTE, loaded.remainingUseMs(at(401)))
        assertEquals(at(530), loaded.cycleEnd)
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(530)))
    }

    @Test
    fun olderExistingBlockIsNeverShortenedByNewCycle() {
        val raw = legacy(10 * MINUTE, until = at(200))
        val loaded = decodeRules(raw, at(60)).single()
        assertEquals(at(200), loaded.blockedUntil)
        assertEquals(0L, loaded.remainingUseMs(at(130)))
        assertEquals(0L, loaded.remainingUseMs(at(199)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(200)))
    }

    @Test
    fun unusedAndResetRulesRoundTripWithoutLosingPhaseOrRecountingOldIntervals() {
        val rule = Rule("a", "甲", 10, 120, start)
        assertEquals(listOf(rule), decodeRules(encodeRules(listOf(rule))))
        val refreshed = rule.record(MINUTE, at(1)).refresh(at(391))
        val restored = decodeRules(encodeRules(listOf(refreshed))).single()
        assertEquals(refreshed, restored)
        assertEquals(at(390), restored.cycleStart)
        assertEquals(at(520), restored.cycleEnd)
        assertEquals(refreshed, restored.record(MINUTE, at(100)))
        val unusedRolling = decodeRules(rolling("[]"), at(400)).single()
        assertNull(unusedRolling.cycleStart)
        assertEquals(10 * MINUTE, unusedRolling.remainingUseMs(at(400)))
        assertEquals(emptyList<Rule>(), decodeRules(null))
    }

    private fun rolling(spans: String, activeUse: Int = 10, until: Long? = null) = """[{
        "package":"a","label":"甲","use":10,"block":120,"created":$start,
        "usage":$spans,"activeUse":$activeUse,"activeBlock":120,
        "from":null,"legacyUntil":${until ?: "null"},"accountedUntil":${at(65)}}]"""

    private fun legacy(used: Long, quota: Int? = null, until: Long? = null) = """[{
        "package":"a","label":"甲","use":10,"block":120,"created":$start,
        "window":$start,"used":$used,"windowUse":${quota ?: "null"},
        "from":null,"until":${until ?: "null"}}]"""
}
