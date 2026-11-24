// Settings' screen parts: the top bar lists scroll under, search-result headers, the default-browser banner, segmented rows.
package com.jlees.minotes.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jlees.minotes.ui.common.*

/**
 * A top bar (search field, or a section's back arrow and title) that the list scrolls under: the bar sits on the sheet's
 * colour with a soft fade below it, so rows slide behind it and fade out instead of being cut off. [content] gets the
 * top padding that keeps its first row clear of the bar at rest.
 */
@Composable
internal fun TopBarOver(bg: Color, bar: @Composable () -> Unit, content: @Composable (top: androidx.compose.ui.unit.Dp) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barPx by remember { mutableIntStateOf(0) }
    val fade = 20.dp
    Box(Modifier.fillMaxSize()) {
        content(with(density) { barPx.toDp() } + fade)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().background(bg).padding(bottom = 6.dp).onSizeChanged { barPx = it.height }) { bar() }
            Box(Modifier.fillMaxWidth().height(fade).background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
        }
    }
}

/** A section's title above its rows in search results. */
@Composable
internal fun SectionHeader(icon: String?, title: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Sym(icon, 18f, ui.cs.primary, Modifier.padding(end = 8.dp))
        Txt(title, 15f, ui.cs.primary, ui.bold)
    }
}

/** Expressive segmented list: large outer corners, small inner corners, 2dp gaps. */
/** Row [i] of [count] in a segmented card: full width, a 2dp gap below, rounded outer corners. */
@Composable
internal fun Modifier.segment(i: Int, count: Int): Modifier {
    val ui = LocalUi.current
    return fillMaxWidth().padding(bottom = if (i < count - 1) 2.dp else 0.dp).clip(segmentShape(i, count, ui.radius)).background(ui.cs.surfaceContainerHighest)
}

internal fun segmentShape(i: Int, count: Int, radius: Int): RoundedCornerShape {
    val big = radius.coerceAtLeast(4).dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small, bottomEnd = if (i == count - 1) big else small,
    )
}
