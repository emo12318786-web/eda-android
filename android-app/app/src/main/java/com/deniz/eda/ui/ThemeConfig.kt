package com.deniz.eda.ui

import android.content.Context
import androidx.compose.ui.graphics.Color

/** Paletler yalnızca görünümü değiştirir; EDA servislerinin çalışma biçimine dokunmaz. */
object ThemeConfig {
    data class Palette(
        val id: String, val name: String, val background: Long, val panel: Long,
        val raised: Long, val accent: Long, val secondary: Long, val muted: Long
    )

    val palettes = listOf(
        // Each theme has a distinct dark-fantasy color identity while preserving near-black surfaces and readable text.
        Palette("dark_queen", "Dark Queen", 0xFF050304, 0xFF160709, 0xFF2B0A10, 0xFFFFB52E, 0xFFFFE0A0, 0xFFC4B59B),
        Palette("hellhound", "Hellhound", 0xFF050202, 0xFF190605, 0xFF310A08, 0xFFFF321F, 0xFFFF8B48, 0xFFC5A7A2),
        Palette("obsidian", "Obsidian", 0xFF030405, 0xFF101113, 0xFF202124, 0xFFB8C7D9, 0xFFE4ECF7, 0xFFAAAEB6),
        Palette("pentagram", "Pentagram", 0xFF060204, 0xFF190509, 0xFF300910, 0xFFFF2D87, 0xFFFF8BC2, 0xFFC5A4AD),
        Palette("wolf", "Wolf", 0xFF030405, 0xFF111214, 0xFF222326, 0xFFB8D8FF, 0xFFE4F2FF, 0xFFADB8C8),
        Palette("eclipse", "Eclipse", 0xFF040203, 0xFF160608, 0xFF2B0B0E, 0xFFFF6A35, 0xFFFFB17A, 0xFFC2A5A5),
        Palette("throne", "Throne", 0xFF050202, 0xFF1A0505, 0xFF330909, 0xFFFF4545, 0xFFFF9090, 0xFFC8A5A5),
        Palette("black_sun", "Black Sun", 0xFF050302, 0xFF19100A, 0xFF2E180D, 0xFFFF8A26, 0xFFFFC66E, 0xFFC5B1A4),
        Palette("dark_angel", "Dark Angel", 0xFF030304, 0xFF130608, 0xFF260B10, 0xFFB9A7FF, 0xFFE2D6FF, 0xFFB8A9C8),
        Palette("dragon_core", "Dragon Core", 0xFF050101, 0xFF1B0304, 0xFF350607, 0xFFFF3A24, 0xFFFF9A51, 0xFFC8A4A2),
        Palette("fallen_angel", "Fallen Angel", 0xFF030304, 0xFF130609, 0xFF260B11, 0xFF8EA8FF, 0xFFD7E0FF, 0xFFB6B8D0),
        Palette("night_queen", "Night Queen", 0xFF050204, 0xFF19050C, 0xFF300A16, 0xFFFF4FA8, 0xFFFFA5D0, 0xFFC7A5B3),
        Palette("space_gate", "Space Gate", 0xFF020304, 0xFF0D0D13, 0xFF1D1118, 0xFF7E9BFF, 0xFFB7D8FF, 0xFFAAB2C0),
        Palette("neon_abyss", "Neon Abyss", 0xFF020403, 0xFF0B110F, 0xFF151F1B, 0xFF45F2D0, 0xFF73E8D0, 0xFFA8BDB8)
    )

    fun selectedId(context: Context): String =
        context.getSharedPreferences("eda_settings", Context.MODE_PRIVATE)
            .getString("eda_theme_id", "dark_angel") ?: "dark_angel"

    fun selected(context: Context): Palette =
        palettes.firstOrNull { it.id == selectedId(context) } ?: palettes[8]

    val current: Palette
        get() = cachedPalette

    private var cachedPalette: Palette = palettes[8]

    fun refresh(context: Context) {
        cachedPalette = selected(context)
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("eda_settings", Context.MODE_PRIVATE)
            .edit().putString("eda_theme_id", id).apply()
        refresh(context)
    }
}
