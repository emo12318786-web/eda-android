package com.deniz.eda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {
    @Test
    fun allFourteenSkinsHaveCompleteUniqueProfiles() {
        val profiles = ThemeConfig.palettes
        assertEquals("Expected exactly 14 curated skins", 14, profiles.size)
        assertEquals("Theme IDs must be unique", 14, profiles.map { it.id }.distinct().size)
        assertEquals("Theme artwork IDs must be unique", 14, profiles.map { it.id }.distinct().size)
        assertEquals("Every theme needs its own icon vocabulary", 14, profiles.map { it.iconStyle }.distinct().size)
        assertEquals("Every theme needs its own identity", 14, profiles.map { listOf(it.id, it.fontFamily, it.iconStyle, it.shapeStyle, it.layoutMode) }.distinct().size)
        assertTrue(profiles.all { it.layoutMode in 0..3 })
        assertTrue(profiles.map { it.layoutMode }.containsAll(listOf(0, 1, 2, 3)))
        assertTrue(profiles.map { it.fontFamily }.containsAll(listOf("sans", "serif", "mono", "cursive")))
        assertTrue(profiles.all { it.shapeStyle.isNotBlank() })
    }

    @Test
    fun everySkinProvidesSemanticIconsAndTypography() {
        ThemeConfig.palettes.forEach { profile ->
            listOf("brand", "home", "control", "system", "battery", "network", "memory", "reminder",
                "chat", "phone", "settings", "wake", "sleep", "stop").forEach { purpose ->
                assertTrue("${profile.name} is missing icon purpose $purpose",
                    ThemeConfig.icon(purpose, profile).isNotBlank())
            }
            assertTrue(ThemeConfig.androidTypeface(profile) != null)
            assertTrue(ThemeConfig.cornerRadius(profile, 18) in 3..38)
        }
        assertTrue(
            "Different skins must not share a single home glyph",
            ThemeConfig.palettes.map { ThemeConfig.icon("home", it) }.distinct().size >= 8
        )
    }

    @Test
    fun semanticUiIconsStayMappedToLabelsAndActions() {
        ThemeConfig.palettes.forEach { profile ->
            assertEquals(ThemeConfig.icon("battery", profile), ThemeConfig.metricIcon("BATARYA", "⚡", profile))
            assertEquals(ThemeConfig.icon("network", profile), ThemeConfig.metricIcon("BAĞLANTI", "⌁", profile))
            assertEquals(ThemeConfig.icon("chat", profile), ThemeConfig.actionIcon("Derin sohbeti aç", "✧", profile))
            assertEquals(ThemeConfig.icon("sleep", profile), ThemeConfig.actionIcon("Uyku moduna al", "☾", profile))
        }
    }
}
