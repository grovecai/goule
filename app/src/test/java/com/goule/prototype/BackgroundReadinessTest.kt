package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundReadinessTest {
    @Test
    fun absenceOfSystemRestrictionDoesNotProveFullBackgroundAllowance() {
        assertEquals(false, backgroundReady(restricted = false, userConfirmed = false))
    }

    @Test
    fun explicitConfirmationAllowsContinuingWithoutBatteryExemption() {
        assertEquals(true, backgroundReady(restricted = false, userConfirmed = true))
    }

    @Test
    fun systemRestrictionBlocksEvenWithConfirmation() {
        for (confirmed in listOf(false, true)) {
            assertEquals(false, backgroundReady(restricted = true, userConfirmed = confirmed))
        }
    }
}
