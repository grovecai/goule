package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceConnectionStateTest {
    @Test fun registeredButDisconnectedWaitsThenReportsInterruption() {
        val state = ServiceConnectionState()
        assertEquals(ServiceState.CONNECTING, state.observe(true, true, false, true, 100))
        assertEquals(ServiceState.CONNECTING, state.observe(true, true, false, true, 5_099))
        assertEquals(ServiceState.INTERRUPTED, state.observe(true, true, false, true, 5_100))
        assertEquals(ServiceState.INTERRUPTED, state.observe(true, true, false, true, 60_000))
    }

    @Test fun onlyActualConnectionCompletesRecoveryAndNextDisconnectHasItsOwnGrace() {
        val state = ServiceConnectionState()
        state.observe(true, true, false, true, 0)
        assertEquals(ServiceState.INTERRUPTED, state.observe(true, true, false, true, 5_000))
        assertEquals(ServiceState.ON, state.observe(true, true, true, true, 5_001))
        assertEquals(ServiceState.CONNECTING, state.observe(true, true, false, true, 10_000))
        assertEquals(ServiceState.INTERRUPTED, state.observe(true, true, false, true, 15_000))
    }

    @Test fun disablingAndReenablingResetsWaitAndKeepsFirstSetupDistinct() {
        val state = ServiceConnectionState()
        assertEquals(ServiceState.OFF, state.observe(false, false, false, false, 0))
        assertEquals(ServiceState.CONNECTING, state.observe(true, true, false, false, 1))
        assertEquals(ServiceState.ON, state.observe(true, true, true, false, 2))
        assertEquals(ServiceState.PAUSED, state.observe(true, false, false, true, 3))
        assertEquals(ServiceState.CONNECTING, state.observe(true, true, false, true, 60_000))
    }

    @Test fun staleConnectionDoesNotOverrideMissingRegistrationOrConsent() {
        val state = ServiceConnectionState()
        assertEquals(ServiceState.PAUSED, state.observe(true, false, true, true, 0))
        assertEquals(ServiceState.OFF, state.observe(false, true, true, false, 1))
    }

    @Test fun interruptionExplainsToggleWhileExplicitlyDisabledRemainsClosed() {
        assertEquals("连接中断", ServiceState.INTERRUPTED.statusLabel)
        assertEquals(true, ServiceState.INTERRUPTED.needsRecovery)
        assertEquals(true, ServiceState.INTERRUPTED.pauseMessage.contains("先关闭再开启"))
        assertEquals("已关闭", ServiceState.PAUSED.statusLabel)
        assertEquals(false, ServiceState.CONNECTING.needsRecovery)
        assertEquals("已设置", ServiceState.ON.statusLabel)
    }
}
