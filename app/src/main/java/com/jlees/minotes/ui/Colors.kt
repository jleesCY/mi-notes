// The colours of mi. Notes' big surfaces: the home page, and a note's page and card in the note's own colour.
package com.jlees.minotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jlees.minotes.notes.NoteColor
import com.jlees.minotes.ui.common.LocalUi

/** The home page's background. Light mode mixes in a little accent, so it doesn't read as colourless. */
@Composable
fun homeBackground(): Color {
    val ui = LocalUi.current
    return if (ui.isDark) ui.cs.surface else lerp(ui.cs.surface, ui.cs.primaryContainer, 0.15f)
}

/** A note's page: the home background, tinted with the note's colour. */
@Composable
fun notePage(c: NoteColor): Color {
    val ui = LocalUi.current
    val base = homeBackground()
    return if (c == NoteColor.NONE) base else lerp(base, Color(c.argb), if (ui.isDark) 0.16f else 0.32f)
}

/** A note's card on the home page (or a row's tile): the cards' colour, tinted with the note's. */
@Composable
fun noteCard(c: NoteColor): Color {
    val ui = LocalUi.current
    val base = ui.cs.surfaceContainerHighest
    return if (c == NoteColor.NONE) base else lerp(base, Color(c.argb), if (ui.isDark) 0.26f else 0.45f)
}

/** The swatch for a colour choice (NONE shows the card colour). */
@Composable
fun swatch(c: NoteColor): Color = if (c == NoteColor.NONE) LocalUi.current.cs.surfaceContainerHighest else Color(c.argb)
