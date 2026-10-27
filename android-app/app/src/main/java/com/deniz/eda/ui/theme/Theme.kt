package com.deniz.eda.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Throne - Crimson Nocturne (Zorunlu Tek Tema)
private val ThroneColorScheme = darkColorScheme(
    primary = Color(0xFFFF243D),       // Kırmızı ana
    onPrimary = Color(0xFFF5F1F2),     // Metin ana
    secondary = Color(0xFF8F071A),     // Koyu kırmızı
    onSecondary = Color(0xFFF5F1F2),
    background = Color(0xFF050506),    // Arka plan siyah
    onBackground = Color(0xFFF5F1F2),
    surface = Color(0xFF121015),       // Kart yüzeyi
    onSurface = Color(0xFFF5F1F2),
    surfaceVariant = Color(0xFF121015),
    onSurfaceVariant = Color(0xFFB9ADB2) // Metin ikincil
)

@Composable
fun EdaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ThroneColorScheme,
        content = content
    )
}
