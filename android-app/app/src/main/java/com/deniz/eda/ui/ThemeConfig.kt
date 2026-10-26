package com.deniz.eda.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/**
 * Full visual identity for an EDA skin. The selected profile controls palette,
 * typography, icon glyph family, card geometry, hero layout and its own wallpaper.
 * It deliberately does not own or change any assistant/service behavior.
 */
object ThemeConfig {
    data class Palette(
        val id: String,
        val name: String,
        val background: Long,
        val panel: Long,
        val raised: Long,
        val accent: Long,
        val secondary: Long,
        val muted: Long,
        val fontFamily: String,
        val iconStyle: String,
        val shapeStyle: String,
        /** 0=hero first, 1=AI first, 2=metrics first, 3=split-focus. */
        val layoutMode: Int
    )

    val palettes = listOf(
        Palette("dark_queen", "Dark Queen", 0xFF050304, 0xFF160709, 0xFF2B0A10, 0xFFFFB52E, 0xFFFFE0A0, 0xFFC4B59B, "serif", "royal", "pill", 0),
        Palette("hellhound", "Hellhound", 0xFF050202, 0xFF190605, 0xFF310A08, 0xFFFF321F, 0xFFFF8B48, 0xFFC5A7A2, "sans", "infernal", "cut", 1),
        Palette("obsidian", "Obsidian", 0xFF030405, 0xFF101113, 0xFF202124, 0xFFB8C7D9, 0xFFE4ECF7, 0xFFAAAEB6, "mono", "geometric", "square", 2),
        Palette("pentagram", "Pentagram", 0xFF060204, 0xFF190509, 0xFF300910, 0xFFFF2D87, 0xFFFF8BC2, 0xFFC5A4AD, "serif", "arcane", "sharp", 3),
        Palette("wolf", "Wolf", 0xFF030405, 0xFF111214, 0xFF222326, 0xFFB8D8FF, 0xFFE4F2FF, 0xFFADB8C8, "sans", "rune", "hex", 1),
        Palette("eclipse", "Eclipse", 0xFF040203, 0xFF160608, 0xFF2B0B0E, 0xFFFF6A35, 0xFFFFB17A, 0xFFC2A5A5, "mono", "eclipse", "round", 2),
        Palette("throne", "Throne", 0xFF050202, 0xFF1A0505, 0xFF330909, 0xFFFF4545, 0xFFFF9090, 0xFFC8A5A5, "serif", "throne", "classic", 0),
        Palette("black_sun", "Black Sun", 0xFF050302, 0xFF19100A, 0xFF2E180D, 0xFFFF8A26, 0xFFFFC66E, 0xFFC5B1A4, "mono", "solar", "cut", 3),
        Palette("dark_angel", "Dark Angel", 0xFF030304, 0xFF130608, 0xFF260B10, 0xFFB9A7FF, 0xFFE2D6FF, 0xFFB8A9C8, "serif", "angel", "soft", 1),
        Palette("dragon_core", "Dragon Core", 0xFF050101, 0xFF1B0304, 0xFF350607, 0xFFFF3A24, 0xFFFF9A51, 0xFFC8A4A2, "sans", "dragon", "sharp", 2),
        Palette("fallen_angel", "Fallen Angel", 0xFF030304, 0xFF130609, 0xFF260B11, 0xFF8EA8FF, 0xFFD7E0FF, 0xFFB6B8D0, "cursive", "fallen", "soft", 3),
        Palette("night_queen", "Night Queen", 0xFF050204, 0xFF19050C, 0xFF300A16, 0xFFFF4FA8, 0xFFFFA5D0, 0xFFC7A5B3, "serif", "night", "pill", 0),
        Palette("space_gate", "Space Gate", 0xFF020304, 0xFF0D0D13, 0xFF1D1118, 0xFF7E9BFF, 0xFFB7D8FF, 0xFFAAB2C0, "mono", "portal", "square", 2),
        Palette("neon_abyss", "Neon Abyss", 0xFF020403, 0xFF0B110F, 0xFF151F1B, 0xFF45F2D0, 0xFF73E8D0, 0xFFA8BDB8, "sans", "cyber", "cut", 1)
    )

    fun selectedId(context: Context): String =
        context.getSharedPreferences("eda_settings", Context.MODE_PRIVATE)
            .getString("eda_theme_id", "dark_angel") ?: "dark_angel"

    fun selected(context: Context): Palette =
        palettes.firstOrNull { it.id == selectedId(context) } ?: palettes[8]

    val current: Palette
        get() = cachedPalette

    private var cachedPalette: Palette = palettes.first { it.id == "dark_angel" }

    fun refresh(context: Context) {
        cachedPalette = selected(context)
    }

    fun save(context: Context, id: String) {
        val safeId = palettes.firstOrNull { it.id == id }?.id ?: "dark_angel"
        context.getSharedPreferences("eda_settings", Context.MODE_PRIVATE)
            .edit().putString("eda_theme_id", safeId).apply()
        refresh(context)
    }

    fun composeFont(palette: Palette = current): FontFamily = when (palette.fontFamily) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    fun androidTypeface(palette: Palette = current): Typeface = when (palette.fontFamily) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "cursive" -> Typeface.create("casual", Typeface.NORMAL)
        else -> Typeface.SANS_SERIF
    }

    fun fontLabel(palette: Palette): String = when (palette.fontFamily) {
        "serif" -> "Serif · klasik"
        "mono" -> "Monospace · terminal"
        "cursive" -> "Casual · zarif"
        else -> "Sans Serif · modern"
    }

    fun cornerRadius(palette: Palette = current, baseDp: Int): Int = when (palette.shapeStyle) {
        "pill" -> (baseDp * 1.45).toInt().coerceAtMost(34)
        "cut" -> (baseDp * 0.62).toInt().coerceAtLeast(5)
        "square" -> 4
        "sharp" -> (baseDp * 0.28).toInt().coerceAtLeast(4)
        "hex" -> (baseDp * 0.52).toInt().coerceAtLeast(7)
        "soft" -> (baseDp * 1.35).toInt().coerceAtMost(38)
        "round" -> (baseDp * 1.6).toInt().coerceAtMost(36)
        else -> baseDp
    }

    fun shapeLabel(palette: Palette): String = when (palette.shapeStyle) {
        "pill" -> "Yuvarlak / akıcı"
        "cut" -> "Kesik köşeli"
        "square" -> "Kare / terminal"
        "sharp" -> "Keskin hatlı"
        "hex" -> "Geometrik"
        "soft" -> "Yumuşak"
        "round" -> "Dairesel"
        "classic" -> "Klasik"
        else -> "Yuvarlak"
    }

    /**
     * Every skin has its own icon vocabulary. Purpose names are stable semantic
     * keys so a visual redesign never changes the action behind a control.
     */
    fun icon(purpose: String, palette: Palette = current): String {
        val glyphs = when (palette.iconStyle) {
            "royal" -> listOf("♛", "⌂", "♜", "⚜", "ϟ", "⌁", "✧", "◷", "✦", "☎", "⚙", "▶", "☾", "■")
            "infernal" -> listOf("♨", "⌖", "ϟ", "♟", "⚡", "⊙", "⛓", "◴", "♞", "♧", "⛭", "ϟ", "▼", "×")
            "geometric" -> listOf("⬡", "◇", "▦", "◈", "ϟ", "⌁", "▣", "◷", "⌘", "◫", "⌬", "▷", "Ⅱ", "■")
            "arcane" -> listOf("⛧", "△", "✣", "✥", "ϟ", "◉", "✧", "⏱", "✶", "☎", "✥", "✶", "☽", "×")
            "rune" -> listOf("ᛟ", "ᚦ", "ᚠ", "❄", "ϟ", "◌", "ᛞ", "◷", "ᚱ", "☎", "ᛉ", "ᚠ", "ᛞ", "᛫")
            "eclipse" -> listOf("◐", "◎", "◉", "◌", "☼", "⟡", "✦", "◷", "☽", "⌕", "◍", "☼", "◑", "●")
            "throne" -> listOf("♔", "♜", "♜", "♖", "ϟ", "⛊", "✦", "◷", "✧", "☎", "♞", "⚔", "☾", "■")
            "solar" -> listOf("☼", "⊙", "✹", "☀", "☼", "⌁", "✺", "◷", "✶", "☎", "☉", "☼", "◒", "▪")
            "angel" -> listOf("✟", "✟", "✧", "⚜", "ϟ", "☁", "✧", "◷", "✦", "☎", "✟", "✧", "☽", "†")
            "dragon" -> listOf("♢", "⟡", "ϟ", "♨", "⚡", "⌁", "◈", "◷", "⟠", "☎", "♢", "♢", "▼", "■")
            "fallen" -> listOf("†", "†", "✥", "✢", "ϟ", "☁", "✦", "◷", "✧", "☎", "†", "✟", "☽", "×")
            "night" -> listOf("♛", "♛", "♜", "☽", "ϟ", "✧", "✦", "◷", "✶", "☎", "♛", "♛", "☾", "■")
            "portal" -> listOf("◎", "⊡", "◈", "⌘", "ϟ", "◉", "⬡", "◷", "✧", "⌕", "⊙", "◎", "Ⅱ", "■")
            "cyber" -> listOf("⌘", "◈", "⌖", "⌬", "ϟ", "⌁", "▣", "◷", "⌘", "▯", "⌬", "⌁", "Ⅱ", "■")
            else -> listOf("E", "⌂", "⌘", "◉", "ϟ", "⌁", "✧", "◷", "✦", "☎", "⚙", "▶", "☾", "■")
        }
        val index = when (purpose.lowercase()) {
            "brand", "crown", "identity" -> 0
            "home" -> 1
            "control", "application", "app", "tools" -> 2
            "system", "screen", "scroll", "back", "screenshot" -> 3
            "battery", "power" -> 4
            "network", "internet", "location", "map", "navigation" -> 5
            "memory", "knowledge" -> 6
            "reminder", "notification", "alarm" -> 7
            "chat", "message", "sms", "translation", "jarvis", "deep_chat", "ollama", "groq", "pollinations" -> 8
            "phone", "call", "camera", "dialer", "car" -> 9
            "settings", "setup" -> 10
            "wake", "start", "microphone", "voice" -> 11
            "sleep" -> 12
            "stop", "security", "lock", "protect" -> 13
            else -> 2
        }
        return glyphs[index]
    }

    fun metricIcon(label: String, fallback: String, palette: Palette = current): String {
        val normalized = label.uppercase()
        val purpose = when {
            "BATARYA" in normalized -> "battery"
            "BAĞLANTI" in normalized || "İNTERNET" in normalized -> "network"
            "HAFIZA" in normalized -> "memory"
            "HATIRLATMA" in normalized -> "reminder"
            "GÜNLÜK" in normalized || "LOG" in normalized -> "system"
            else -> return fallback
        }
        return icon(purpose, palette)
    }

    fun actionIcon(title: String, fallback: String, palette: Palette = current): String {
        val normalized = title.lowercase()
        val purpose = when {
            "sohbet" in normalized || "jarvis" in normalized || "ollama" in normalized ||
                "derin analiz" in normalized || "yapay zek" in normalized -> "chat"
            "telefon" in normalized || "phone" in normalized || "arama" in normalized ||
                "çağrı" in normalized || "kamera" in normalized || "araba" in normalized -> "phone"
            "mesaj" in normalized || "sms" in normalized || "çevir" in normalized -> "message"
            "ekran" in normalized || "kaydır" in normalized || "yukarı" in normalized ||
                "aşağı" in normalized || "geri git" in normalized || "screenshot" in normalized -> "screen"
            "uygulama" in normalized || "app " in normalized -> "application"
            "internet" in normalized || "bağlantı" in normalized || "konum" in normalized ||
                "harita" in normalized || "navigasyon" in normalized -> "network"
            "bildirim" in normalized || "hatırlat" in normalized || "alarm" in normalized -> "reminder"
            "güvenlik" in normalized || "koruma" in normalized || "kilitle" in normalized -> "security"
            "ayar" in normalized || "kurulum" in normalized || "izin" in normalized -> "settings"
            "uyku" in normalized -> "sleep"
            "durdur" in normalized || "stop" in normalized -> "stop"
            "başlat" in normalized || "uyandır" in normalized || "aktif" in normalized ||
                "sesli" in normalized || "mikrofon" in normalized -> "wake"
            "pil" in normalized || "batarya" in normalized -> "battery"
            "hafıza" in normalized || "günlük" in normalized -> "memory"
            "groq" in normalized || "pollinations" in normalized -> "network"
            "ilk kurulum" in normalized -> "settings"
            else -> return fallback
        }
        return icon(purpose, palette)
    }
}
