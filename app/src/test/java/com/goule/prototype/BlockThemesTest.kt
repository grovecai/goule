package com.goule.prototype

import org.junit.Assert.*
import org.junit.Test

class BlockThemesTest {
    @Test fun existingInstallKeepsElderAndUnknownSettingsRecover() {
        assertEquals(BlockThemeMode.FIXED, BlockThemeMode.parse(null))
        assertEquals(BlockThemeMode.FIXED, BlockThemeMode.parse("future-mode"))
        assertEquals(BlockThemes.default, BlockThemes.choose(BlockThemeMode.FIXED, "removed-theme", emptySet(), false))
    }

    @Test fun fixedThemeDoesNotUseTheRandomPool() {
        val chosen = BlockThemes.choose(BlockThemeMode.FIXED, "kitchen", setOf("exam"), false) { error("Unexpected draw") }
        assertEquals("kitchen", chosen.id)
    }

    @Test fun randomOnlyDrawsFromTheUsersValidSelection() {
        val pool = setOf("garden", "sunlight", "removed-theme")
        for (index in 0..1) {
            assertEquals(listOf("garden", "sunlight")[index],
                BlockThemes.choose(BlockThemeMode.RANDOM, "marriage", pool, true) { size ->
                    assertEquals(2, size)
                    index
                }.id)
        }
    }

    @Test fun emptyAndObsoletePoolsHaveASafeFallback() {
        for (pool in listOf(emptySet(), setOf("removed-theme"))) {
            assertEquals(BlockThemes.default, BlockThemes.choose(BlockThemeMode.RANDOM, "exam", pool, false) { error("Empty draw") })
        }
        assertEquals("exam", BlockThemes.choose(BlockThemeMode.RANDOM, null, setOf("exam"), false).id)
    }

    @Test fun unavailableCustomImageRecoversToElder() {
        assertEquals(BlockThemes.default, BlockThemes.choose(BlockThemeMode.CUSTOM, "exam", emptySet(), false))
        assertEquals(BlockThemes.custom, BlockThemes.choose(BlockThemeMode.CUSTOM, null, emptySet(), true))
    }

    @Test fun everyScenarioHasStableUniqueIdentityAndSpokenCopy() {
        assertEquals(11, BlockThemes.all.size)
        assertEquals(BlockThemes.all.size, BlockThemes.all.map { it.id }.toSet().size)
        for (theme in BlockThemes.all) {
            assertTrue(theme.name.isNotBlank() && theme.headline.isNotBlank() && theme.reminder.isNotBlank() && theme.description.isNotBlank())
        }
    }
}
