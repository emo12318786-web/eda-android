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
        Palette("dark_queen", "Dark Queen", 0xFF100710, 0xFF291326, 0xFF421B35, 0xFFE34D70, 0xFFFF8FA8, 0xFFB9A5B3),
        Palette("hellhound", "Hellhound", 0xFF100606, 0xFF2B1110, 0xFF451B18, 0xFFE34B42, 0xFFFF9A72, 0xFFB8A3A1),
        Palette("obsidian", "Obsidian", 0xFF070A10, 0xFF17202B, 0xFF263545, 0xFFE05B63, 0xFFFFA0A5, 0xFF9DA8B8),
        Palette("pentagram", "Pentagram", 0xFF0C0612, 0xFF261033, 0xFF3B1A50, 0xFFCF526E, 0xFFDA8BFF, 0xFFB5A1BE),
        Palette("wolf", "Wolf", 0xFF050B14, 0xFF12283D, 0xFF1D405C, 0xFFB84D5A, 0xFF8BB8E8, 0xFF9EADBF),
        Palette("eclipse", "Eclipse", 0xFF080611, 0xFF201633, 0xFF342453, 0xFF9D6BDA, 0xFFE0A0F7, 0xFFA9A4B9),
        Palette("throne", "Throne", 0xFF110505, 0xFF301010, 0xFF4A1B1B, 0xFFC94C4C, 0xFFE5B1A5, 0xFFB8A5A5),
        Palette("black_sun", "Black Sun", 0xFF100904, 0xFF30200D, 0xFF4A3216, 0xFFE07A42, 0xFFFFC17D, 0xFFB8AA9B),
        Palette("dark_angel", "Dark Angel", 0xFF050A17, 0xFF12233A, 0xFF1C3857, 0xFFB49AFF, 0xFF64E5E0, 0xFF98A2B9),
        Palette("dragon_core", "Dragon Core", 0xFF100404, 0xFF321010, 0xFF501B1B, 0xFFFF4545, 0xFFFFA15C, 0xFFB9A1A1),
        Palette("fallen_angel", "Fallen Angel", 0xFF070B18, 0xFF172443, 0xFF263A61, 0xFF9D8BFF, 0xFFB3D7FF, 0xFFA2A8BF),
        Palette("night_queen", "Night Queen", 0xFF120512, 0xFF35102F, 0xFF501847, 0xFFE65BA8, 0xFFFFA4D6, 0xFFBCA2B7),
        Palette("space_gate", "Space Gate", 0xFF050A19, 0xFF10264A, 0xFF1A3D70, 0xFF827BFF, 0xFF61D9F4, 0xFF9BAAC8),
        Palette("neon_abyss", "Neon Abyss", 0xFF03100E, 0xFF0B2924, 0xFF12463D, 0xFF39E6C5, 0xFF91FFF0, 0xFF91B8B4)
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
