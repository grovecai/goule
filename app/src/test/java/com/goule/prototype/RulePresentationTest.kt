package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class RulePresentationTest {
    @Test
    fun quotaLabelsRoundUpAtSecondAndMinuteBoundaries() {
        val rule = Rule("a", "A", 20, 30, 0)
        assertEquals("还可刷 20 分钟", remainingUseLabel(rule, 0))
        assertEquals("还可刷 19 分 59 秒", remainingUseLabel(rule.copy(usedMs = 1_001), 0))
        assertEquals("还可刷 1 分钟", remainingUseLabel(rule.copy(usedMs = 20 * MINUTE - 59_001), 0))
        assertEquals("还可刷 1 秒", remainingUseLabel(rule.copy(usedMs = 20 * MINUTE - 1), 0))
        assertEquals("还可刷 0 秒", remainingUseLabel(rule.copy(usedMs = 20 * MINUTE), 0))
        assertEquals("还可刷 0 秒", remainingUseLabel(rule.copy(usedMs = 20 * MINUTE + 1), 0))
    }
}
