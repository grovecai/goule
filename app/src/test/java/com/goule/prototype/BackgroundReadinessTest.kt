package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundReadinessTest {
    @Test
    fun confirmationSupportsOemSettingsButNeverOverridesSystemRestriction() {
        for (exempt in listOf(false, true)) {
            for (confirmed in listOf(false, true)) {
                assertEquals(false, backgroundReady(exempt, true, confirmed))
                assertEquals(exempt || confirmed, backgroundReady(exempt, false, confirmed))
            }
        }
    }
}
