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
        /** Typeface style flags: Android Typeface.NORMAL/BOLD/ITALIC/BOLD_ITALIC. */
        val fontStyle: Int,
        val iconStyle: String,
        val shapeStyle: String,
        /** 0–13 = 14 unique hero compositions; screen tools and assistant behavior stay unchanged. */
        val layoutMode: Int
    )

    // One fixed monochrome identity: no theme carousel or color switching.
    // Keep the legacy ID so existing settings and dashboard references remain compatible.
    val palettes = listOf(
        Palette(
            "dark_angel", "Monochrome Neon",
            0xFF030405, // background: near-black
            0xFF101216, // panel
            0xFF1D2229, // raised panel
            0xFFF5F7FF, // primary neon-white
            0xFFBFC9D8, // secondary silver-white
            0xFF8F9AAA, // muted text
            "sans-serif", 0, "geometric", "soft", 0
        )
    )

    fun selectedId(context: Context): String = "dark_angel"

    fun selected(context: Context): Palette = palettes.first()

    val current: Palette
        get() = cachedPalette

    private var cachedPalette: Palette = palettes.first()

    fun refresh(context: Context) {
        cachedPalette = selected(context)
    }

    fun save(context: Context, id: String) {
        // Compatibility no-op: EDA now uses one fixed monochrome appearance.
        refresh(context)
    }

    fun composeFont(palette: Palette = current): FontFamily =
        FontFamily(androidTypeface(palette))

    fun androidTypeface(
        palette: Palette = current,
        style: Int = palette.fontStyle
    ): Typeface = Typeface.create(palette.fontFamily, style)

    fun composeFontWeight(palette: Palette = current): androidx.compose.ui.text.font.FontWeight =
        if (palette.fontStyle and Typeface.BOLD != 0)
            androidx.compose.ui.text.font.FontWeight.Bold
        else
            androidx.compose.ui.text.font.FontWeight.Normal

    fun composeFontStyle(palette: Palette = current): androidx.compose.ui.text.font.FontStyle =
        if (palette.fontStyle and Typeface.ITALIC != 0)
            androidx.compose.ui.text.font.FontStyle.Italic
        else
            androidx.compose.ui.text.font.FontStyle.Normal

    fun fontLabel(palette: Palette): String {
        val family = when (palette.fontFamily) {
            "serif" -> "Serif"
            "sans-serif-condensed" -> "Sans Condensed"
            "monospace" -> "Monospace"
            "sans-serif-light" -> "Sans Light"
            "sans-serif-medium" -> "Sans Medium"
            "sans-serif-condensed-light" -> "Condensed Light"
            "casual" -> "Casual"
            "sans-serif-black" -> "Sans Black"
            "cursive" -> "Cursive"
            "sans-serif-condensed-medium" -> "Condensed Medium"
            "sans-serif-smallcaps" -> "Small Caps"
            "sans-serif-thin" -> "Sans Thin"
            else -> palette.fontFamily
        }
        val style = when (palette.fontStyle) {
            Typeface.BOLD -> "Bold"
            Typeface.ITALIC -> "Italic"
            Typeface.BOLD_ITALIC -> "Bold Italic"
            else -> "Regular"
        }
        return "$family · $style"
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
