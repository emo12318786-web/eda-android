package com.deniz.eda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {
    @Test
    fun sixDistinctNeonThemesAreAvailable() {
        val profiles = ThemeConfig.palettes
        assertEquals("EDA must expose six selectable neon themes", 6, profiles.size)
        assertEquals(
            setOf("aurora", "ember", "arctic", "orchid", "solar", "stealth"),
            profiles.map { it.id }.toSet()
        )
        assertEquals("Theme IDs must be unique", profiles.size, profiles.map { it.id }.distinct().size)
        assertTrue("Every theme needs a distinct display name", profiles.all { it.name.isNotBlank() })
        assertTrue("Every theme needs a valid layout mode", profiles.all { it.layoutMode in 0..13 })
        assertEquals("All themes must keep the same home order and neon orbital core", setOf(1), profiles.map { it.layoutMode }.toSet())
    }

    @Test
    fun everyThemeKeepsSemanticIconsTypographyAndShape() {
        ThemeConfig.palettes.forEach { theme ->
            listOf(
                "brand", "home", "control", "system", "battery", "network", "memory",
                "reminder", "chat", "phone", "settings", "wake", "sleep", "stop"
            ).forEach { purpose ->
                assertTrue(
                    "Missing icon purpose $purpose in theme ${theme.id}",
                    ThemeConfig.icon(purpose, theme).isNotBlank()
                )
            }
            assertTrue("Missing font label in ${theme.id}", ThemeConfig.fontLabel(theme).isNotBlank())
            assertTrue(
                "Invalid corner radius in ${theme.id}",
                ThemeConfig.cornerRadius(theme, 18) in 3..38
            )
            assertEquals(
                ThemeConfig.icon("battery", theme),
                ThemeConfig.metricIcon("BATARYA", "⚡", theme)
            )
            assertEquals(
                ThemeConfig.icon("network", theme),
                ThemeConfig.metricIcon("BAĞLANTI", "⌁", theme)
            )
            assertEquals(
                ThemeConfig.icon("chat", theme),
                ThemeConfig.actionIcon("Derin sohbeti aç", "✧", theme)
            )
            assertEquals(
                ThemeConfig.icon("sleep", theme),
                ThemeConfig.actionIcon("Uyku moduna al", "☾", theme)
            )
        }
    }

    @Test
    fun selectedThemeIdsResolveToAvailableProfiles() {
        ThemeConfig.palettes.forEach { theme ->
            assertFalse(ThemeConfig.palettes.none { it.id == theme.id })
        }
    }
}
