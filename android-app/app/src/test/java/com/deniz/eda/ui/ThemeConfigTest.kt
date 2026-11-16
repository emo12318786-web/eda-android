package com.deniz.eda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {
    @Test
    fun sixDistinctVisualSkinsAreAvailable() {
        val profiles = ThemeConfig.palettes
        assertEquals("EDA should expose six visual skins", 6, profiles.size)
        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        assertEquals(profiles.size, profiles.map { it.name }.distinct().size)
        assertEquals("aurora", profiles.first().id)
        assertTrue(profiles.any { it.id == "ember" })
        assertTrue(profiles.any { it.id == "arctic" })
        assertTrue(profiles.any { it.id == "orchid" })
        assertTrue(profiles.any { it.id == "solar" })
        assertTrue(profiles.any { it.id == "stealth" })
    }

    @Test
    fun skinsHaveDistinctColorsAndValidVisualMetadata() {
        val profiles = ThemeConfig.palettes
        assertTrue(profiles.map { it.background }.distinct().size > 1)
        assertTrue(profiles.map { it.accent }.distinct().size > 1)
        profiles.forEach { theme ->
            assertTrue("Missing name for ${theme.id}", theme.name.isNotBlank())
            assertTrue("Missing font for ${theme.id}", ThemeConfig.fontLabel(theme).isNotBlank())
            assertTrue("Invalid corner radius for ${theme.id}", ThemeConfig.cornerRadius(theme, 18) in 3..38)
            assertTrue("Missing brand icon for ${theme.id}", ThemeConfig.icon("brand", theme).isNotBlank())
            assertTrue("Missing home icon for ${theme.id}", ThemeConfig.icon("home", theme).isNotBlank())
            assertTrue("Missing control icon for ${theme.id}", ThemeConfig.icon("control", theme).isNotBlank())
            assertTrue("Missing system icon for ${theme.id}", ThemeConfig.icon("system", theme).isNotBlank())
            assertTrue("Missing battery icon for ${theme.id}", ThemeConfig.icon("battery", theme).isNotBlank())
            assertTrue("Missing network icon for ${theme.id}", ThemeConfig.icon("network", theme).isNotBlank())
            assertTrue("Missing memory icon for ${theme.id}", ThemeConfig.icon("memory", theme).isNotBlank())
            assertTrue("Missing reminder icon for ${theme.id}", ThemeConfig.icon("reminder", theme).isNotBlank())
            assertTrue("Missing chat icon for ${theme.id}", ThemeConfig.icon("chat", theme).isNotBlank())
            assertTrue("Missing phone icon for ${theme.id}", ThemeConfig.icon("phone", theme).isNotBlank())
            assertTrue("Missing settings icon for ${theme.id}", ThemeConfig.icon("settings", theme).isNotBlank())
            assertTrue("Missing wake icon for ${theme.id}", ThemeConfig.icon("wake", theme).isNotBlank())
            assertTrue("Missing sleep icon for ${theme.id}", ThemeConfig.icon("sleep", theme).isNotBlank())
            assertTrue("Missing stop icon for ${theme.id}", ThemeConfig.icon("stop", theme).isNotBlank())
            assertEquals(ThemeConfig.icon("battery", theme), ThemeConfig.metricIcon("BATARYA", "⚡", theme))
            assertEquals(ThemeConfig.icon("network", theme), ThemeConfig.metricIcon("BAĞLANTI", "⌁", theme))
            assertEquals(ThemeConfig.icon("chat", theme), ThemeConfig.actionIcon("Derin sohbeti aç", "✧", theme))
            assertEquals(ThemeConfig.icon("sleep", theme), ThemeConfig.actionIcon("Uyku moduna al", "☾", theme))
        }
    }

    @Test
    fun unknownThemeIdFallsBackToFirstSkin() {
        val first = ThemeConfig.palettes.first()
        assertEquals(first.id, ThemeConfig.palettes.firstOrNull { it.id == "not-a-real-theme" }?.id ?: first.id)
    }
}
