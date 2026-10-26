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
        // The reference direction is shared across all 14 models: near-black surfaces,
        // deep crimson lighting and readable neutral text. Each theme keeps its own accent.
        Palette("dark_queen", "Dark Queen", 0xFF050304, 0xFF160709, 0xFF2B0A10, 0xFFFF263B, 0xFFFF6675, 0xFFC4A8AC),
        Palette("hellhound", "Hellhound", 0xFF050202, 0xFF190605, 0xFF310A08, 0xFFFF2B1F, 0xFFFF704A, 0xFFC5A7A2),
        Palette("obsidian", "Obsidian", 0xFF030405, 0xFF101113, 0xFF202124, 0xFFFF3945, 0xFFB7B9C0, 0xFFAAAEB6),
        Palette("pentagram", "Pentagram", 0xFF060204, 0xFF190509, 0xFF300910, 0xFFFF174A, 0xFFFF637D, 0xFFC5A4AD),
        Palette("wolf", "Wolf", 0xFF030405, 0xFF111214, 0xFF222326, 0xFFFF3038, 0xFFD0D3D8, 0xFFADB0B8),
        Palette("eclipse", "Eclipse", 0xFF040203, 0xFF160608, 0xFF2B0B0E, 0xFFFF342E, 0xFFFF8278, 0xFFC2A5A5),
        Palette("throne", "Throne", 0xFF050202, 0xFF1A0505, 0xFF330909, 0xFFFF2525, 0xFFFF6868, 0xFFC8A5A5),
        Palette("black_sun", "Black Sun", 0xFF050302, 0xFF19100A, 0xFF2E180D, 0xFFFF3A22, 0xFFFF9A54, 0xFFC5B1A4),
        Palette("dark_angel", "Dark Angel", 0xFF030304, 0xFF130608, 0xFF260B10, 0xFFFF263D, 0xFFE2D6D8, 0xFFB8A9AD),
        Palette("dragon_core", "Dragon Core", 0xFF050101, 0xFF1B0304, 0xFF350607, 0xFFFF1E24, 0xFFFF773D, 0xFFC8A4A2),
        Palette("fallen_angel", "Fallen Angel", 0xFF030304, 0xFF130609, 0xFF260B11, 0xFFFF2B43, 0xFFD7D0D6, 0xFFB6A8B0),
        Palette("night_queen", "Night Queen", 0xFF050204, 0xFF19050C, 0xFF300A16, 0xFFFF2454, 0xFFFF83A7, 0xFFC7A5B3),
        Palette("space_gate", "Space Gate", 0xFF020304, 0xFF0D0D13, 0xFF1D1118, 0xFFFF2D4D, 0xFFB7C5D5, 0xFFAAB2C0),
        Palette("neon_abyss", "Neon Abyss", 0xFF020403, 0xFF0B110F, 0xFF151F1B, 0xFFFF2949, 0xFF73E8D0, 0xFFA8BDB8)
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
