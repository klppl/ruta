package io.github.klppl.ruta.ui.theme

import androidx.compose.ui.graphics.Color

// Fallback brand palette used when dynamic color is unavailable (API < 31) or disabled.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

/**
 * Per-profile accent seeds. Each profile is assigned one deterministically so the
 * active account is always visually obvious. Index 0 is reserved for the default profile.
 */
val ProfileAccents = listOf(
    Color(0xFF6750A4), // violet  (default)
    Color(0xFF006A6A), // teal
    Color(0xFFB3261E), // red
    Color(0xFF7D5800), // amber
    Color(0xFF1B6C2F), // green
    Color(0xFF8E4585), // magenta
    Color(0xFF00639C), // blue
    Color(0xFF904D00), // orange
)

fun accentForProfile(profileId: String): Color {
    if (profileId.isEmpty()) return ProfileAccents[0]
    val hash = profileId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
    return ProfileAccents[hash % ProfileAccents.size]
}
