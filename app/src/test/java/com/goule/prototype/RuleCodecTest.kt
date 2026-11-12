package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleCodecTest {
    private val start = 100 * DAY

    @Test
    fun savedEditsRestoreOriginalQuotaUsageAndWindowForEachApp() {
        val first = Rule("a", "甲", 20, 30, start).record(8 * MINUTE, start + 8 * MINUTE)
            .edit(5, 15, start + 9 * MINUTE)
        val second = Rule("b", "乙", 10, 30, start).record(MINUTE, start + MINUTE)
        val restored = decodeRules(encodeRules(listOf(first, second)))
        assertEquals(listOf(first, second), restored)
        assertEquals(12 * MINUTE, restored[0].remainingUseMs(start + HOUR))
        assertEquals(9 * MINUTE, restored[1].remainingUseMs(start + HOUR))
        val blocked = restored[0].record(12 * MINUTE, start + 21 * MINUTE)
        val reloadedBlock = decodeRules(encodeRules(listOf(blocked))).single()
        assertEquals(blocked, reloadedBlock)
        val until = checkNotNull(reloadedBlock.blockedUntil)
        assertEquals(0L, reloadedBlock.remainingUseMs(until - 1))
        assertEquals(5 * MINUTE, reloadedBlock.remainingUseMs(until))
    }

    @Test
    fun legacyJsonWithoutQuotaSnapshotKeepsUsageThroughEditAndSave() {
        val legacy = """[{"package":"a","label":"甲","use":20,"block":30,
            "created":$start,"edited":null,"window":$start,"used":${8 * MINUTE},
            "from":null,"until":null}]"""
        val loaded = decodeRules(legacy).single()
        assertNull(loaded.windowUseMinutes)
        val edited = loaded.edit(5, 15, start + 9 * MINUTE)
        val restored = decodeRules(encodeRules(listOf(edited))).single()
        assertEquals(20, restored.windowUseMinutes)
        assertEquals(start, restored.windowStart)
        assertEquals(12 * MINUTE, restored.remainingUseMs(start + HOUR))
    }

    @Test
    fun unusedAndRefreshedRulesRestoreWithoutStaleQuotaSnapshot() {
        val rule = Rule("a", "甲", 20, 30, start)
        assertEquals(listOf(rule), decodeRules(encodeRules(listOf(rule))))
        val refreshed = rule.record(MINUTE, start + MINUTE)
            .edit(5, 15, start + 2 * MINUTE).refresh(start + DAY)
        val restored = decodeRules(encodeRules(listOf(refreshed))).single()
        assertNull(restored.windowUseMinutes)
        assertEquals(5 * MINUTE, restored.remainingUseMs(start + DAY))
        assertEquals(emptyList<Rule>(), decodeRules(null))
    }
}
