package com.goule.prototype

import org.junit.Assert.*
import org.junit.Test

class ServiceHealthTest {
    private val observation = ServiceObservation(1_000, 2_000, 0, 42)
    private fun lines(evidence: ServiceHealthEvidence, state: ServiceState = ServiceState.INTERRUPTED) =
        evidence.lines(state) { "time=$it" }.joinToString("\n")

    @Test fun systemKillIsAssociatedOnlyWithObservedServiceProcess() {
        val text = lines(ServiceHealthEvidence(observation, ProcessExit(3_000, 42, 13, "o-kill(4013)"), true))
        assertTrue(text.contains("最近确认连接：time=2000"))
        assertTrue(text.contains("服务所在进程退出：time=3000"))
        assertTrue(text.contains("系统结束了应用进程，具体策略未确定"))
        assertTrue(text.contains("系统原始说明：o-kill(4013)"))
        assertFalse(text.contains("一键清理"))
        assertFalse(text.contains("内存不足"))
        assertFalse(text.contains("崩溃"))
    }

    @Test fun oldExitCannotExplainAnInterruptionAfterReconnection() {
        val text = lines(ServiceHealthEvidence(observation, ProcessExit(500, 42, 4, "stale crash"), true))
        assertTrue(text.contains("早于最近一次连接"))
        assertFalse(text.contains("stale crash"))
        assertFalse(text.contains("应用发生异常崩溃"))
    }

    @Test fun laterActivityExitDoesNotHideTheServiceProcessExit() {
        val service = ProcessExit(3_000, 42, 13, "system kill")
        val activity = ProcessExit(4_000, 43, 4, "later activity crash")
        assertEquals(service, serviceProcessExit(observation, listOf(activity, service)))
        assertEquals(activity, serviceProcessExit(ServiceObservation(), listOf(service, activity)))
        assertEquals(activity, serviceProcessExit(observation.copy(connectedAt = 5_000, lastSeenAt = 5_000), listOf(service, activity)))
        assertNull(serviceProcessExit(observation, emptyList()))
    }

    @Test fun unrelatedPidOrNormalDisconnectDoesNotClaimCausation() {
        for (value in listOf(observation to 43, observation.copy(disconnectedAt = 2_500) to 42)) {
            val text = lines(ServiceHealthEvidence(value.first, ProcessExit(3_000, value.second, 16, null), true))
            assertTrue(text.contains("上次应用进程退出"))
            assertTrue(text.contains("不一定是本次"))
            assertTrue(text.contains("安装更新"))
        }
    }

    @Test fun oldInstallAndMissingHistoryAreHonestAboutEvidence() {
        val legacy = lines(ServiceHealthEvidence(exit = ProcessExit(3_000, 42, 13, null), historyReady = true))
        assertTrue(legacy.contains("暂无服务连接观测记录"))
        assertTrue(legacy.contains("不一定是本次"))
        assertTrue(lines(ServiceHealthEvidence()).contains("正在读取"))
        assertTrue(lines(ServiceHealthEvidence(historyReady = true)).contains("没有保留"))
        assertTrue(lines(ServiceHealthEvidence(historyReady = true, historyUnavailable = true)).contains("无法提供"))
    }

    @Test fun recoveredAndInitialStatesDoNotKeepShowingFailure() {
        for (state in listOf(ServiceState.ON, ServiceState.OFF, ServiceState.CONNECTING)) {
            assertEquals("", lines(ServiceHealthEvidence(observation, ProcessExit(3_000, 42, 13, "kill"), true), state))
        }
    }

    @Test fun descriptionsAreBoundedAndCannotInjectExtraLinesOrBidiControls() {
        assertEquals("a  b", cleanExitDescription("\na\t\u202eb\r"))
        assertEquals(240, cleanExitDescription("长".repeat(1_000)).length)
        assertEquals("", cleanExitDescription(null))
        assertTrue(exitReasonLabel(10).contains("或系统请求"))
        assertTrue(exitReasonLabel(99).contains("未提供"))
    }
}
