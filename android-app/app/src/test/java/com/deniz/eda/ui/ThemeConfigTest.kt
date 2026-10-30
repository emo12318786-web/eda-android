package com.deniz.eda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {
    @Test
    fun onlyOneMonochromeNeonThemeIsAvailable() {
        val profiles = ThemeConfig.palettes
        assertEquals("EDA must expose one fixed theme", 1, profiles.size)
        val theme = profiles.single()
        assertEquals("neon_abyss", theme.id)
        assertEquals(0xFF030303, theme.background)
        assertEquals(0xFFF5F5F5, theme.accent)
        assertEquals(0xFFBDBDBD, theme.secondary)
        assertEquals("cyber", theme.iconStyle)
        assertEquals("cut", theme.shapeStyle)
        assertEquals(1, profiles.map { it.id }.distinct().size)
    }

    @Test
    fun monochromeThemeKeepsSemanticIconsAndTypography() {
        val theme = ThemeConfig.palettes.single()
        listOf("brand", "home", "control", "system", "battery", "network", "memory", "reminder",
            "chat", "phone", "settings", "wake", "sleep", "stop").forEach { purpose ->
            assertTrue("Missing icon purpose $purpose", ThemeConfig.icon(purpose, theme).isNotBlank())
        }
        assertTrue(ThemeConfig.fontLabel(theme).isNotBlank())
        assertTrue(ThemeConfig.cornerRadius(theme, 18) in 3..38)
        assertEquals(ThemeConfig.icon("battery", theme), ThemeConfig.metricIcon("BATARYA", "⚡", theme))
        assertEquals(ThemeConfig.icon("network", theme), ThemeConfig.metricIcon("BAĞLANTI", "⌁", theme))
        assertEquals(ThemeConfig.icon("chat", theme), ThemeConfig.actionIcon("Derin sohbeti aç", "✧", theme))
        assertEquals(ThemeConfig.icon("sleep", theme), ThemeConfig.actionIcon("Uyku moduna al", "☾", theme))
    }

}
