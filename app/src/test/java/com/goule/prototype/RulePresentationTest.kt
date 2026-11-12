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
    fun labelsShowProgressiveRecoveryAndKeepActiveQuotaAfterEditing() {
        val edited = Rule("a", "A", 10, 120, 0).record(10 * MINUTE, 10 * MINUTE)
            .edit(5, 15, 11 * MINUTE)
        assertEquals("还可刷 0 秒", remainingUseLabel(edited, 130 * MINUTE))
        assertEquals("还可刷 1 秒", remainingUseLabel(edited, 130 * MINUTE + 1))
        assertEquals("还可刷 2 分钟", remainingUseLabel(edited, 132 * MINUTE))
        assertEquals("还可刷 5 分钟", remainingUseLabel(edited, 140 * MINUTE))
    }

    @Test
    fun detailExplainsActualWindowWhileChangedParametersArePending() {
        val edited = Rule("a", "A", 10, 120, 0).record(MINUTE, MINUTE).edit(5, 15, 2 * MINUTE)
        assertEquals("任意连续 130 分钟内，最多刷 10 分钟。新时长待有效用量全部过期后生效。",
            usageWindowLabel(edited, 2 * MINUTE))
        assertEquals("任意连续 20 分钟内，最多刷 5 分钟。旧用量过期后逐步恢复额度。",
            usageWindowLabel(edited, 131 * MINUTE))
    }
}
