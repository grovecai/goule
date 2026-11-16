package com.goule.prototype

import org.junit.Assert.*
import org.junit.Test

class BlockThemesTest {
    private val personal = BlockTheme("user_12345678-1234-1234-1234-123456789abc", "学习提醒", "先去学习", "今天的计划还没有完成。",
        "自己的图片", "先收起手机", "现在开始学习", ThemeImage.Local("photo.webp"))

    @Test fun onlyFixedAndRandomModesRemain() {
        assertEquals(listOf("FIXED", "RANDOM"), BlockThemeMode.entries.map { it.name })
        assertEquals(BlockThemeMode.FIXED, BlockThemeMode.parse("CUSTOM"))
        assertEquals(BlockThemeMode.FIXED, BlockThemeMode.parse("future-mode"))
    }

    @Test fun personalThemeIsAnOrdinaryFixedAndRandomChoice() {
        val themes = BlockThemes.available(emptySet(), listOf(personal))
        assertEquals(personal, BlockThemes.choose(BlockThemeMode.FIXED, personal.id, emptySet(), themes))
        assertEquals(personal, BlockThemes.choose(BlockThemeMode.RANDOM, "elder", setOf(personal.id), themes))
        assertEquals("exam", BlockThemes.choose(BlockThemeMode.RANDOM, null, setOf("exam", personal.id), themes) { 0 }.id)
        assertEquals(personal, BlockThemes.choose(BlockThemeMode.RANDOM, null, setOf("exam", personal.id), themes) { 1 })
    }

    @Test fun deletedAndRetiredSelectionsFallBackWithoutResurrection() {
        val themes = BlockThemes.available(setOf("kitchen", "garden"))
        for (id in listOf("kitchen", "garden", "baby", "unknown")) {
            assertEquals(BlockThemes.default, BlockThemes.find(id, themes))
            assertEquals(BlockThemes.default, BlockThemes.choose(BlockThemeMode.RANDOM, id, setOf(id), themes))
        }
        assertEquals(BlockThemes.default, BlockThemes.choose(BlockThemeMode.RANDOM, null, emptySet(), themes))
    }

    @Test fun protectedElderSurvivesInvalidDeleteEverythingPreference() {
        assertEquals(listOf(BlockThemes.default), BlockThemes.available(BlockThemes.all.map { it.id }.toSet()))
    }

    @Test fun everyBuiltInContainsImageDialogueAndBothActionNames() {
        assertEquals(10, BlockThemes.all.size)
        assertFalse(BlockThemes.all.any { it.id == "baby" || it.name == "奶瓶召唤" })
        assertEquals(10, BlockThemes.all.map { it.id }.toSet().size)
        for (theme in BlockThemes.all) {
            assertTrue(theme.image is ThemeImage.Bundled)
            assertNull(ThemeDraft.from(theme).error())
            assertTrue(theme.closeLabel.isNotBlank() && theme.homeLabel.isNotBlank())
        }
        assertNotEquals(BlockThemes.default.homeLabel, BlockThemes.find("kitchen").homeLabel)
    }

    @Test fun fullPersonalThemeRoundTripsThroughStorage() {
        val decoded = ThemeCodec.decode(ThemeCodec.encode(listOf(personal))).single()
        assertEquals(personal.copy(description = decoded.description), decoded)
        assertEquals("先收起手机", decoded.closeLabel)
        assertEquals("现在开始学习", decoded.homeLabel)
    }

    @Test fun malformedMetadataAndUnsafePathsCannotBecomeThemes() {
        assertTrue(ThemeCodec.decode("broken json").isEmpty())
        val serialized = ThemeCodec.encode(listOf(personal))
        assertTrue(ThemeCodec.decode(serialized.replace("photo.webp", "../photo.webp")).isEmpty())
        assertTrue(ThemeCodec.decode(serialized.replace(personal.id, "elder")).isEmpty())
        assertFalse(ThemeCodec.safeFile("/private/photo.webp"))
    }

    @Test fun themeDraftRejectsMissingAndOverlongNamesAndCopy() {
        assertNotNull(ThemeDraft(name = " ").error())
        assertNotNull(ThemeDraft(closeLabel = " ").error())
        assertNotNull(ThemeDraft(homeLabel = "一二三四五六七").error())
        assertNotNull(ThemeDraft(reminder = "字".repeat(81)).error())
        assertNull(ThemeDraft(closeLabel = "先收起手机吧", homeLabel = "现在开始学习").error())
        assertEquals("我的主题", ThemeDraft(name = " 我的主题 ").trimmed().name)
    }
}
