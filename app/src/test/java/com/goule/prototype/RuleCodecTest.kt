package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCodecTest {
    private val start = 100 * DAY
    private fun at(minutes: Long) = start + minutes * MINUTE

    @Test
    fun saveAndReloadPreserveSegmentsPolicyDeadlinesAndEachAppsQuota() {
        val first = Rule("a", "甲", 10, 120, start).record(5 * MINUTE, at(5))
            .record(5 * MINUTE, at(65)).edit(5, 15, at(66))
        val second = Rule("b", "乙", 10, 30, start).record(MINUTE, at(100))
        val restored = decodeRules(encodeRules(listOf(first, second)))
        assertEquals(listOf(first, second), restored)
        assertEquals(at(130), restored[0].blockedUntil)
        assertEquals(MINUTE, restored[0].remainingUseMs(at(131)))
        assertEquals(5 * MINUTE, restored[0].remainingUseMs(at(135)))
        assertEquals(9 * MINUTE, restored[1].remainingUseMs(at(135)))
        assertEquals(5 * MINUTE, restored[0].remainingUseMs(at(195)))
        // Re-delivery of the last interval after restart is idempotent.
        assertEquals(first, restored[0].record(5 * MINUTE, at(65)))
    }

    @Test
    fun legacyAggregateMigratesAtLastSaveRatherThanOldWindowStartOrReopenTime() {
        val legacy = """[{"package":"a","label":"甲","use":10,"block":120,
            "created":$start,"edited":null,"window":$start,"used":${5 * MINUTE},
            "from":null,"until":null}]"""
        val loaded = decodeRules(legacy, at(60)).single()
        assertEquals(listOf(UsageSpan(at(55), at(60))), loaded.usage)
        assertEquals(5 * MINUTE, loaded.remainingUseMs(at(130)))
        assertEquals(6 * MINUTE, loaded.remainingUseMs(at(186)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(190)))
        val restored = decodeRules(encodeRules(listOf(loaded)), at(180)).single()
        assertEquals(loaded, restored)
        assertEquals(5 * MINUTE, restored.remainingUseMs(at(180)))
        assertEquals(loaded.usage, loaded.edit(5, 15, at(61)).usage)
    }

    @Test
    fun legacyQuotaSnapshotAndExistingDeadlineArePreservedIncludingZeroAggregate() {
        val legacy = """[{"package":"a","label":"甲","use":5,"block":15,
            "created":$start,"edited":${at(11)},"window":$start,"used":${10 * MINUTE},
            "windowUse":10,"from":${at(10)},"until":${at(130)}}]"""
        val loaded = decodeRules(legacy, at(12)).single()
        assertEquals(10, loaded.activeUseMinutes)
        assertEquals(at(130), loaded.blockedUntil)
        assertEquals(0L, loaded.remainingUseMs(at(100)))
        assertEquals(5 * MINUTE, loaded.remainingUseMs(at(130)))
        val zero = decodeRules(legacy.replace("\"used\":${10 * MINUTE}", "\"used\":0"), at(12)).single()
        assertTrue(zero.usage.isEmpty())
        assertTrue(zero.isBlocked(at(129)))
        assertEquals(0L, zero.remainingUseMs(at(129)))
        assertEquals(5 * MINUTE, zero.remainingUseMs(at(130)))
    }

    @Test
    fun legacyFixedBlockWithUnchangedParametersTransitionsToGradualRecovery() {
        val legacy = """[{"package":"a","label":"甲","use":10,"block":120,
            "created":$start,"window":$start,"used":${10 * MINUTE},
            "from":${at(10)},"until":${at(130)}}]"""
        val loaded = decodeRules(legacy, at(100)).single()
        assertEquals(at(130), loaded.blockedUntil)
        assertEquals(0L, loaded.remainingUseMs(at(130)))
        assertEquals(MINUTE, loaded.remainingUseMs(at(131)))
        assertEquals(10 * MINUTE, loaded.remainingUseMs(at(140)))
    }

    @Test
    fun missingLegacyClockFallsBackWithoutDiscardingUsage() {
        val legacy = """[{"package":"a","label":"甲","use":10,"block":120,
            "created":$start,"window":$start,"used":${5 * MINUTE}}]"""
        val loaded = decodeRules(legacy).single()
        assertEquals(listOf(UsageSpan(start, at(5))), loaded.usage)
        assertEquals(5 * MINUTE, loaded.remainingUseMs(at(5)))
    }

    @Test
    fun emptyAndExpiredHistoryRoundTripWithoutRevivingLegacyAggregates() {
        val rule = Rule("a", "甲", 10, 120, start)
        assertEquals(listOf(rule), decodeRules(encodeRules(listOf(rule))))
        val refreshed = rule.record(MINUTE, at(1)).edit(5, 15, at(2)).refresh(at(131))
        val restored = decodeRules(encodeRules(listOf(refreshed))).single()
        assertEquals(refreshed, restored)
        assertNull(restored.activeUseMinutes)
        assertNull(restored.activeBlockMinutes)
        assertEquals(5 * MINUTE, restored.remainingUseMs(at(131)))
        assertEquals(refreshed, restored.record(MINUTE, at(1)))
        assertEquals(emptyList<Rule>(), decodeRules(null))
    }
}
