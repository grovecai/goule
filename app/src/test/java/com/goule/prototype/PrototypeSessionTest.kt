package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrototypeSessionTest {
    @Test
    fun separateForegroundSegmentsAndDuplicateEventsUseOneQuota() {
        val session = PrototypeSession()

        assertEquals(10_000L, session.observe(true, 0))
        assertEquals(10_000L, session.observe(true, 1_000))
        assertNull(session.observe(false, 4_000))
        assertEquals(106_000L, session.observe(true, 100_000))
        assertEquals(126_000L, session.observe(true, 106_000))
        assertEquals(126_000L, session.frozenUntil)
    }

    @Test
    fun leavingAndReturningDuringFreezeKeepsTheOriginalDeadline() {
        val session = PrototypeSession()
        session.observe(true, 0)

        assertNull(session.observe(false, 10_000))
        assertEquals(30_000L, session.observe(true, 18_000))
        assertNull(session.observe(false, 20_000))
        assertEquals(40_000L, session.observe(true, 30_000))
        assertNull(session.frozenUntil)
    }

    @Test
    fun expiryWhileTargetStaysVisibleStartsAnotherRound() {
        val session = PrototypeSession()
        session.observe(true, 0)
        session.observe(true, 10_000)

        assertEquals(40_000L, session.observe(true, 30_000))
        assertNull(session.frozenUntil)
        assertEquals(60_000L, session.observe(true, 40_000))
    }
}
