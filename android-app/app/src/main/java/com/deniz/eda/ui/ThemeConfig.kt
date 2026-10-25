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
        Palette("dark_queen", "Dark Queen", 0xFF0B080D, 0xFF1A101B, 0xFF281522, 0xFFE34D70, 0xFFFF8FA8, 0xFFB9A5B3),
        Palette("hellhound", "Hellhound", 0xFF0B0909, 0xFF1B1212, 0xFF2A1919, 0xFFE34B42, 0xFFFF9A72, 0xFFB8A3A1),
        Palette("obsidian", "Obsidian", 0xFF08090C, 0xFF14171D, 0xFF20252D, 0xFFE05B63, 0xFFFFA0A5, 0xFF9DA8B8),
        Palette("pentagram", "Pentagram", 0xFF0B080D, 0xFF19111F, 0xFF28182F, 0xFFCF526E, 0xFFDA8BFF, 0xFFB5A1BE),
        Palette("wolf", "Wolf", 0xFF080B10, 0xFF121A24, 0xFF1D2937, 0xFFB84D5A, 0xFF8BB8E8, 0xFF9EADBF),
        Palette("eclipse", "Eclipse", 0xFF09090D, 0xFF171620, 0xFF242231, 0xFF9D6BDA, 0xFFE0A0F7, 0xFFA9A4B9),
        Palette("throne", "Throne", 0xFF0B0808, 0xFF1B1213, 0xFF2B1A1B, 0xFFC94C4C, 0xFFE5B1A5, 0xFFB8A5A5),
        Palette("black_sun", "Black Sun", 0xFF0B0907, 0xFF1B1510, 0xFF2A2118, 0xFFE07A42, 0xFFFFC17D, 0xFFB8AA9B),
        Palette("dark_angel", "Dark Angel", 0xFF080A12, 0xFF121626, 0xFF191F33, 0xFFB49AFF, 0xFF64E5E0, 0xFF98A2B9),
        Palette("dragon_core", "Dragon Core", 0xFF090909, 0xFF191313, 0xFF2A1A1A, 0xFFFF4545, 0xFFFFA15C, 0xFFB9A1A1),
        Palette("fallen_angel", "Fallen Angel", 0xFF090A10, 0xFF151725, 0xFF22253A, 0xFF9D8BFF, 0xFFB3D7FF, 0xFFA2A8BF),
        Palette("night_queen", "Night Queen", 0xFF0D080E, 0xFF1E111E, 0xFF2D192C, 0xFFE65BA8, 0xFFFFA4D6, 0xFFBCA2B7),
        Palette("space_gate", "Space Gate", 0xFF080912, 0xFF11172A, 0xFF1A2440, 0xFF827BFF, 0xFF61D9F4, 0xFF9BAAC8),
        Palette("neon_abyss", "Neon Abyss", 0xFF050A0A, 0xFF0E1919, 0xFF152727, 0xFF39E6C5, 0xFF91FFF0, 0xFF91B8B4)
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
