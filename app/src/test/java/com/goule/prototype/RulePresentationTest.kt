package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class RulePresentationTest {
    @Test
    fun quotaLabelsRoundUpAtSecondAndMinuteBoundaries() {
        val start = 100 * DAY
        val rule = Rule("a", "A", 20, 30, start)
        assertEquals("还可刷 20 分钟", remainingUseLabel(rule, start))
        for ((used, expected) in listOf(1_001L to "还可刷 19 分 59 秒",
            20 * MINUTE - 59_001 to "还可刷 1 分钟",
            20 * MINUTE - 1 to "还可刷 1 秒", 20 * MINUTE to "还可刷 0 秒",
            20 * MINUTE + 1 to "还可刷 0 秒")) {
            assertEquals(expected, remainingUseLabel(rule.record(used, start + used), start + used))
        }
    }

    @Test
    fun labelsStayStableUntilOneFullResetAndAdoptPendingQuotaAtBoundary() {
        val edited = Rule("a", "A", 10, 120, 0).record(10 * MINUTE, 10 * MINUTE)
            .edit(5, 15, 11 * MINUTE)
        assertEquals("还可刷 0 秒", remainingUseLabel(edited, 130 * MINUTE - 1))
        assertEquals("还可刷 5 分钟", remainingUseLabel(edited, 130 * MINUTE))
        assertEquals("还可刷 5 分钟", remainingUseLabel(edited, 132 * MINUTE))
        assertEquals("还可刷 5 分钟", remainingUseLabel(edited, 140 * MINUTE))
    }

    @Test
    fun descriptionUsesEachRulesActualPeriodAndExplainsNextCycleChanges() {
        val edited = Rule("a", "A", 10, 120, 0).record(MINUTE, MINUTE).edit(5, 15, 2 * MINUTE)
        assertEquals("每 130 分钟重置一次，可刷 10 分钟。新时长从下一周期生效。",
            usageWindowLabel(edited, 2 * MINUTE))
        assertEquals("每 20 分钟重置一次，可刷 5 分钟。周期内分段累计，用满后等到重置。",
            usageWindowLabel(edited, 130 * MINUTE))
        assertEquals("每 50 分钟重置一次，可刷 20 分钟。周期内分段累计，用满后等到重置。",
            usageWindowLabel(Rule("b", "B", 20, 30, 0), 0))
    }
}
