// Accent seed colours. Colour schemes are generated from the accent seed (Material 3).
package com.jlees.minotes.logic

/** accentColor value meaning "use wallpaper colours" (Material You dynamic colour, Android 12+). */
const val DYNAMIC_ACCENT = "dynamic"

const val GRAY_ACCENT = "#8E8E93"
/** mi. Notes' own colour, its icon's yellow: the default accent. */
const val NOTES_YELLOW = "#FFCC00"
val ACCENTS = listOf(
    GRAY_ACCENT, // gray (monochrome scheme)
    "#3D9BFF",   // blue: mi. Browser's Sky blue
    "#34C759",   // green
    "#FF3B30",   // red
    "#FF9500",   // orange
    NOTES_YELLOW, // yellow
    "#AF52DE",   // purple
)

/** "#RRGGBB" → ARGB long, mi. Notes' yellow when invalid. */
fun hexColor(hex: String?): Long = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: 0xFFFFCC00
