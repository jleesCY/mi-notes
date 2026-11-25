// A note on the home page: a card in the grid (title, a preview of its text, its date) or a row in the list. Tap opens
// it; hold it to rearrange (drag) or, let go without moving, for its menu.
package com.jlees.minotes.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.checklistProgress
import com.jlees.minotes.notes.cardTitle
import com.jlees.minotes.notes.displayTitle
import com.jlees.minotes.notes.previewLines
import com.jlees.minotes.ui.noteCard
import com.jlees.minotes.ui.common.*
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Tap, hold, and hold-then-drag in one gesture. A tap opens; once held (the long-press time, without moving),
 * [onHold] fires, and from there moving the finger drags ([onDrag], the finger in raw screen px) until it lifts
 * ([onRelease]: whether it moved). A move before the hold is a scroll, left to the list.
 */
@Composable
fun Modifier.cardGestures(
    onTap: () -> Unit, onHold: (raw: Offset, local: Offset) -> Unit, onDrag: (raw: Offset) -> Unit, onRelease: (moved: Boolean) -> Unit,
    onPress: (Boolean) -> Unit = {},
): Modifier {
    // the gesture outlives compositions: it calls the latest callbacks (which see the latest list), not the first ones
    val tap by rememberUpdatedState(onTap); val hold by rememberUpdatedState(onHold); val dragTo by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease); val press by rememberUpdatedState(onPress)
    return cardGestureInput({ tap() }, { a, b -> hold(a, b) }, { dragTo(it) }, { release(it) }, { press(it) })
}

private fun Modifier.cardGestureInput(
    onTap: () -> Unit, onHold: (raw: Offset, local: Offset) -> Unit, onDrag: (raw: Offset) -> Unit, onRelease: (moved: Boolean) -> Unit,
    onPress: (Boolean) -> Unit,
) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // where the finger is on the screen, read now from the touch itself: when the hold time runs out there's no
        // fresh touch event to read it from (it read as 0, 0, and the lifted copy sat far below and right of the finger)
        val downRaw = currentEvent.motionEvent?.let { Offset(it.rawX, it.rawY) }
        onPress(true)
        val slop = viewConfiguration.touchSlop
        var tapped = false
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!c.pressed) { tapped = !c.isConsumed; return@withTimeoutOrNull false }
                if (c.isConsumed || (c.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE") false
        } == null
        if (!held) { onPress(false); if (tapped) onTap(); return@awaitEachGesture }
        onHold(downRaw ?: currentEvent.motionEvent?.let { Offset(it.rawX, it.rawY) } ?: Offset.Zero, down.position)
        var moved = false
        // always ends the drag, even if the card leaves the screen (scrolled away) while held
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (!c.pressed) break
                if ((c.position - down.position).getDistance() > slop) moved = true
                e.motionEvent?.let { onDrag(Offset(it.rawX, it.rawY)) }
            }
        } finally {
            onPress(false)
            onRelease(moved)
        }
    }
}

/** A note's date: the time today, the weekday this past week ("Tuesday"), the day this year, else the full date. */
fun shortDate(ms: Long): String {
    val now = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        System.currentTimeMillis() - ms in 0 until 6 * 86_400_000L -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(Date(ms))
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(Date(ms))
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    }
}

/** How tall every card is in the clean grid: room for a two-line title, [previewLines] of preview and the date. */
fun uniformCardHeight(previewLines: Int) = (98 + previewLines * 18).dp

/** A grid card. [lifted]: being dragged (it rises a little). [uniform]: the clean grid's fixed height, preview cut to fit. */
@Composable
fun NoteCardView(n: Note, previewLines: Int, showDate: Boolean, lifted: Boolean, pressed: Boolean, folderName: String?, gestures: Modifier, uniform: Boolean = false) {
    val ui = LocalUi.current
    val scale by animateFloatAsState(if (lifted) 1.02f else if (pressed) 0.97f else 1f, Motion.fastSpatial(), label = "card")
    val title = cardTitle(n)
    // staggered: at most the Preview Lines setting, and a note without a title shows the two lines its title would have
    // taken (so it's never thinner than a titled one for want of a title); the grid's fixed card: as much as fits
    val previewMax = if (uniform) 40 else previewLines + if (title == null) 2 else 0
    val lines = remember(n.body, n.title, previewMax) { previewLines(n, previewMax) }
    val progress = remember(n.body) { checklistProgress(n.body) }
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (lifted) 8.dp.toPx() else 0f; shape = RoundedCornerShape(ui.radius.coerceAtMost(20).dp); clip = true }
            .then(if (uniform) Modifier.height(uniformCardHeight(previewLines)) else Modifier)
            .background(noteCard(n.color))
            .then(gestures)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
    ) {
        if (title != null || n.pinned) Row(verticalAlignment = Alignment.Top) {
            Txt(title ?: "", 16f, ui.cs.onSurface, ui.bold, Modifier.weight(1f), maxLines = 2)
            if (n.pinned) Sym("keep", 16f, ui.cs.primary, Modifier.padding(start = 6.dp, top = 2.dp))
        }
        val top = if (title != null || n.pinned) 6.dp else 0.dp
        if (lines.isNotEmpty() && uniform) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(top = top).weight(1f).fillMaxWidth()) {
            // every whole line that fits the card, down to its date
            val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { 18.sp.toDp() }
            Txt(lines.joinToString("\n"), 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = (maxHeight / lineH).toInt().coerceAtLeast(1), lineHeight = 18f)
        } else if (lines.isNotEmpty()) Txt(lines.joinToString("\n"), 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = top), maxLines = previewMax, lineHeight = 18f)
        if (n.body.images.isNotEmpty() && lines.isEmpty()) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym("image", 16f, ui.cs.onSurfaceVariant, Modifier.padding(end = 6.dp)); Txt(if (n.body.images.size == 1) "1 picture" else "${n.body.images.size} pictures", 13f, ui.cs.onSurfaceVariant, ui.regular)
        }
        val meta = listOfNotNull(
            if (showDate) shortDate(n.edited) else null,
            progress?.let { (d, t) -> "$d/$t done" },
            folderName,
        )
        if (uniform && lines.isEmpty()) Spacer(Modifier.weight(1f)) // the date sits at the bottom of every card alike (the preview fills down to it)
        if (meta.isNotEmpty()) Txt(meta.joinToString(" · "), 12f, ui.cs.onSurfaceVariant.copy(alpha = 0.8f), ui.semibold, Modifier.padding(top = 10.dp), maxLines = 1)
    }
}

/** A list row: the note's colour tile, title and first line, date. */
@Composable
fun NoteRowView(n: Note, showDate: Boolean, lifted: Boolean, pressed: Boolean, gestures: Modifier, height: Float) {
    val ui = LocalUi.current
    val scale by animateFloatAsState(if (lifted) 1.02f else if (pressed) 0.98f else 1f, Motion.fastSpatial(), label = "row")
    val sub = remember(n.body, n.title) { previewLines(n, 1).firstOrNull() }
    val rowShape = libraryRowShape() // the lifted row's shadow follows its shape
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (lifted) 8.dp.toPx() else 0f; shape = rowShape }
            .fillMaxWidth().heightIn(min = height.dp).listRow().then(gestures).padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the tile: the row tile colour, tinted with the note's own colour when it has one
        RowIconTile(color = if (n.color == com.jlees.minotes.notes.NoteColor.NONE) rowTileColor() else noteCard(n.color)) {
            Sym(if (checklistProgress(n.body) != null) "checklist" else "sticky_note_2", 22f, ui.cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Txt(displayTitle(n), 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
            // the details line: its date first, in the accent, then the note's first line
            if (showDate || sub != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDate) Txt(shortDate(n.edited), 12f, ui.cs.primary, ui.semibold, Modifier.padding(end = 6.dp), maxLines = 1)
                if (sub != null) Txt(sub, 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
            }
        }
        if (n.pinned) Sym("keep", 18f, ui.cs.primary, Modifier.padding(start = 8.dp))
    }
}

/**
 * Screen readers can't hold and drag: the card's tap and menu as actions, and moving it past its neighbour as "Move
 * up" / "Move down" ([moveUp], [moveDown]: null where it can't go, or when notes aren't in your own order).
 */
fun Modifier.cardSemantics(label: String, onOpen: () -> Unit, onMenu: () -> Unit, moveUp: (() -> Unit)? = null, moveDown: (() -> Unit)? = null) = semantics {
    onClick(label = "Open $label") { onOpen(); true }
    onLongClick(label = "Note options") { onMenu(); true }
    customActions = listOfNotNull(
        moveUp?.let { m -> CustomAccessibilityAction("Move up") { m(); true } },
        moveDown?.let { m -> CustomAccessibilityAction("Move down") { m(); true } },
    )
}
