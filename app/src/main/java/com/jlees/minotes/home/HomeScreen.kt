// The home page: every note, in folders. The folder's name at the top with Settings beside it, the folders as chips
// under it, then the notes (pinned first) as a grid of cards or a list, and the Pill at the bottom to search and start
// a note. Hold a note to drag it somewhere else, or let go to get its menu.
package com.jlees.minotes.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.data.NotesLayout
import com.jlees.minotes.data.SortOrder
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.displayTitle
import com.jlees.minotes.notes.visibleNotes
import com.jlees.minotes.ui.homeBackground
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.delay

/** A note being dragged: which, where the finger is (window px), and where on the note it took hold. */
private class Drag {
    var id by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var size = androidx.compose.ui.geometry.Size.Zero
    var rawToWindow = Offset.Zero
    var lastTarget: String? = null
}

@Composable
fun HomeScreen(vm: NotesViewModel, statusDp: Float, navDp: Float, imeDp: () -> Float, dragSheet: (Sheet, Float) -> Unit, releaseSheet: (Boolean) -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    val haptics = LocalHaptics.current
    val shown = remember(vm.notes.notes, vm.folder, vm.query, s.sort) { visibleNotes(vm.notes.notes, vm.folder, vm.query, s.sort) }
    val folderName = vm.notes.folders.firstOrNull { it.id == vm.folder }?.name
    val drag = remember { Drag() }
    var pressedId by remember { mutableStateOf<String?>(null) }
    val rootView = androidx.compose.ui.platform.LocalView.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun checkSwap() {
        val id = drag.id ?: return
        if (s.sort != SortOrder.MANUAL || vm.query.isNotEmpty()) return
        val me = shown.firstOrNull { it.id == id } ?: return
        val target = shown.firstOrNull { it.id != id && it.pinned == me.pinned && vm.cardBounds[it.id]?.contains(drag.pointer) == true }
        if (target == null) { drag.lastTarget = null; return }
        if (target.id == drag.lastTarget) return
        drag.lastTarget = target.id
        // no buzz here: the lift had its haptic, and nothing else in a drag is a decision point
        vm.notes.reorder(id, target.id, after = shown.indexOf(target) > shown.indexOf(me))
    }

    // for "Move up" / "Move down": each note's place, and whether notes are in the order you put them in
    val place = remember(shown) { shown.withIndex().associate { it.value.id to it.index } }
    val ownOrder = s.sort == SortOrder.MANUAL && vm.query.isEmpty()
    /** The note next to [n] ([step] -1 above, 1 below) that it can swap with: in its group (pinned or not), in your own order. */
    fun neighbour(n: Note, step: Int): Note? = if (!ownOrder) null else shown.getOrNull((place[n.id] ?: return null) + step)?.takeIf { it.pinned == n.pinned }

    @Composable
    fun gesturesFor(n: Note) = Modifier
        .cardSemantics(
            displayTitle(n), onOpen = { vm.open(n.id) }, onMenu = { vm.menuFor = n.id },
            moveUp = neighbour(n, -1)?.let { t -> { vm.notes.reorder(n.id, t.id, after = false) } },
            moveDown = neighbour(n, 1)?.let { t -> { vm.notes.reorder(n.id, t.id, after = true) } },
        )
        .cardGestures(
            onTap = { vm.open(n.id) },
            onHold = { raw, local ->
                haptics?.longPress()
                val b = vm.cardBounds[n.id] ?: return@cardGestures
                // the finger in window px: its place on the screen less the window's (exact, whatever the card's scale)
                val loc = IntArray(2).also { rootView.getLocationOnScreen(it) }
                drag.rawToWindow = Offset(-loc[0].toFloat(), -loc[1].toFloat())
                drag.pointer = raw + drag.rawToWindow; drag.grab = drag.pointer - b.topLeft; drag.size = b.size
                drag.lastTarget = null
                drag.id = n.id
            },
            onDrag = { raw ->
                drag.pointer = raw + drag.rawToWindow
                checkSwap()
            },
            onRelease = { moved -> if (!moved) vm.menuFor = n.id; drag.id = null },
            onPress = { pressedId = if (it) n.id else null },
        )

    /**
     * Where a note's card is (for dragging, and for the editor to grow out of). The card itself never moves while it's
     * dragged: it stays in the list as an empty gap, and a copy of it ([DragCopy]) follows the finger above everything.
     */
    fun Modifier.place(n: Note) = onGloballyPositioned { vm.cardBounds[n.id] = it.boundsInWindow() }
        .graphicsLayer { alpha = if (drag.id == n.id || vm.shownNote == n.id) 0f else 1f }

    Box(Modifier.fillMaxSize().background(homeBackground())) {
        Column(Modifier.fillMaxSize().tapAway(focusManager).padding(top = statusDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxSize()) {
                Header(vm, folderName, shown.size)
                FolderChips(vm)
                val bottom = (navDp + 120).dp
                if (shown.isEmpty()) Box(Modifier.fillMaxSize()) {
                    if (vm.query.isNotBlank()) EmptyState("search", "No Matches") else EmptyState("sticky_note_2", "No Notes Yet")
                } else if (s.layout != NotesLayout.LIST) {
                    val pinned = shown.count { it.pinned }
                    val both = pinned > 0 && pinned < shown.size
                    val padding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottom)
                    @Composable fun Card(n: Note, uniform: Boolean) =
                        GridItem(vm, n, false, pressedId == n.id, gesturesFor(n), Modifier.place(n), uniform)
                    if (s.layout == NotesLayout.STAGGERED) {
                        // cards as tall as their note: a staggered grid
                        val grid = rememberLazyStaggeredGridState()
                        AutoScroll(drag, { grid.scrollBy(it) }, ::checkSwap)
                        FollowTop(shown.firstOrNull()?.id, { grid.firstVisibleItemIndex }) { grid.requestScrollToItem(0) }
                        LazyVerticalStaggeredGrid(
                            StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(), grid, contentPadding = padding,
                            verticalItemSpacing = 18.dp, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (both) item(key = "h:pinned", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Pinned") }
                            items(shown.take(pinned), key = { it.id }) { n -> Box(Modifier.animateItem(placementSpec = Motion.spatial())) { Card(n, false) } }
                            if (both) item(key = "h:others", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Others") }
                            items(shown.drop(pinned), key = { it.id }) { n -> Box(Modifier.animateItem(placementSpec = Motion.spatial())) { Card(n, false) } }
                        }
                    } else {
                        // every card the same size: a clean grid
                        val grid = rememberLazyGridState()
                        AutoScroll(drag, { grid.scrollBy(it) }, ::checkSwap)
                        FollowTop(shown.firstOrNull()?.id, { grid.firstVisibleItemIndex }) { grid.requestScrollToItem(0) }
                        LazyVerticalGrid(
                            GridCells.Fixed(2), Modifier.fillMaxSize(), grid, contentPadding = padding,
                            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (both) item(key = "h:pinned", span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Pinned") }
                            gridItems(shown.take(pinned), key = { it.id }) { n -> Box(Modifier.animateItem(placementSpec = Motion.spatial())) { Card(n, true) } }
                            if (both) item(key = "h:others", span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Others") }
                            gridItems(shown.drop(pinned), key = { it.id }) { n -> Box(Modifier.animateItem(placementSpec = Motion.spatial())) { Card(n, true) } }
                        }
                    }
                } else {
                    val list = rememberLazyListState()
                    AutoScroll(drag, { list.scrollBy(it) }, ::checkSwap)
                    FollowTop(shown.firstOrNull()?.id, { list.firstVisibleItemIndex }) { list.requestScrollToItem(0) }
                    val rowH = rowHeight(s.uiPadding, s.fontScale) + 8f
                    LazyColumn(
                        Modifier.fillMaxSize(), list,
                        contentPadding = PaddingValues(start = listSides(), end = listSides(), top = 8.dp, bottom = bottom),
                        verticalArrangement = Arrangement.spacedBy(listGap().dp),
                    ) {
                        items(shown, key = { it.id }) { n ->
                            Box(Modifier.animateItem(placementSpec = Motion.spatial()).place(n)) {
                                val row = @Composable { NoteRowView(n, s.showDates, false, pressedId == n.id, gesturesFor(n), rowH) }
                                if (s.swipeRows) SwipeActionRow(null, onDelete = { vm.deleteNote(n.id) }, onEdit = { vm.togglePin(n.id) }, editIcon = if (n.pinned) "keep_off" else "keep", editLabel = if (n.pinned) "Unpin" else "Pin") { row() }
                                else row()
                            }
                        }
                    }
                }
            }
        }
        DragCopy(vm, drag)
        HomePill(vm, navDp, imeDp, dragSheet, releaseSheet)
    }
}

/**
 * A note added at the top (a new one) while the list is at its top shows there at once. A lazy list keeps its old
 * first item where it was, so the new card landed above, out of sight, and the cards only moved once it was
 * scrolled to (well after the note had closed). [firstIndex] is 1 just after such an insert.
 */
@Composable
private fun FollowTop(firstId: String?, firstIndex: () -> Int, toTop: () -> Unit) =
    LaunchedEffect(firstId) { if (firstIndex() <= 1) toTop() }

/**
 * A tap anywhere above the Pill (not a scroll) lets go of the search field, and the keyboard goes with it. It only
 * watches: the tap still reaches whatever was under it (a card opens as usual).
 */
private fun Modifier.tapAway(focusManager: androidx.compose.ui.focus.FocusManager) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (waitForUpOrCancellation(PointerEventPass.Initial) != null) focusManager.clearFocus()
    }
}

/** The note being dragged, lifted, under the finger exactly where it was picked up. */
@Composable
private fun DragCopy(vm: NotesViewModel, drag: Drag) {
    val n = vm.notes[drag.id ?: return] ?: return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val s = vm.settings
    val size = with(density) { androidx.compose.ui.unit.DpSize(drag.size.width.toDp(), drag.size.height.toDp()) }
    Box(
        Modifier.zIndex(20f)
            .offset { androidx.compose.ui.unit.IntOffset((drag.pointer.x - drag.grab.x).toInt(), (drag.pointer.y - drag.grab.y).toInt()) }
            .size(size),
    ) {
        if (s.layout == NotesLayout.LIST) NoteRowView(n, s.showDates, true, false, Modifier, rowHeight(s.uiPadding, s.fontScale) + 8f)
        else NoteCardView(n, s.previewLines, s.showDates, true, false, null, Modifier, uniform = s.layout == NotesLayout.GRID)
    }
}

@Composable
private fun GridItem(vm: NotesViewModel, n: Note, lifted: Boolean, pressed: Boolean, gestures: Modifier, modifier: Modifier, uniform: Boolean) {
    // under All Notes, each card says which folder it's in
    val folder = if (vm.folder == null) vm.notes.folders.firstOrNull { it.id == n.folder }?.name else null
    Box(modifier) { NoteCardView(n, vm.settings.previewLines, vm.settings.showDates, lifted, pressed, folder, gestures, uniform) }
}

/** While a note is dragged near the top or bottom edge, the notes scroll to bring more into reach. */
@Composable
private fun AutoScroll(drag: Drag, scroll: suspend (Float) -> Unit, onStep: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenH = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edge = with(density) { 96.dp.toPx() }
    val step by rememberUpdatedState(onStep)
    val by by rememberUpdatedState(scroll)
    LaunchedEffect(drag.id) {
        while (drag.id != null) {
            val y = drag.pointer.y
            val d = when { y < edge * 2.2f -> -14f; y > screenH - edge -> 14f; else -> 0f }
            if (d != 0f) { by(d); step() }
            delay(16)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val ui = LocalUi.current
    Txt(text, 14f, ui.cs.onSurfaceVariant, ui.bold, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
private fun Header(vm: NotesViewModel, folderName: String?, count: Int) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(folderName ?: "All Notes", 30f, ui.cs.onSurface, ui.extrabold, maxLines = 1)
            Txt(if (count == 1) "1 note" else "$count notes", 13f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 18f)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChips(vm: NotesViewModel) {
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val live = vm.notes.notes.filter { it.deletedAt == null }
    val listState = rememberLazyListState()
    // the folder showing stays in view (it changes by swiping the Pill too)
    LaunchedEffect(vm.folder) {
        val i = if (vm.folder == null) 0 else vm.notes.folders.indexOfFirst { it.id == vm.folder } + 1
        if (i >= 0) listState.animateScrollToItem(i.coerceAtLeast(0))
    }
    LazyRow(Modifier.fillMaxWidth(), listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { Chip("All Notes", live.size, vm.folder == null, onClick = { vm.showFolder(null) }) }
        items(vm.notes.folders, key = { it.id }) { f ->
            Chip(f.name, live.count { it.folder == f.id }, vm.folder == f.id,
                onClick = { vm.showFolder(f.id) }, onLongClick = { haptics?.longPress(); vm.ask = Ask.FolderMenu(f.id) })
        }
        item(key = "new") {
            Box(Modifier.size(40.dp).clip(CircleShape).background(ui.cs.surfaceContainerHigh).touchable { vm.ask = Ask.NewFolder() }, contentAlignment = Alignment.Center) {
                Sym("create_new_folder", 20f, ui.cs.onSurfaceVariant, label = "New folder")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(label: String, count: Int, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val ui = LocalUi.current
    val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHigh, label = "chip")
    val fg = if (on) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(press).height(40.dp).clip(CircleShape).background(bg)
            .combinedClickable(press, androidx.compose.material3.ripple(), onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, 14f, fg, ui.semibold, maxLines = 1)
        Txt("$count", 12f, fg.copy(alpha = 0.7f), ui.semibold, Modifier.padding(start = 6.dp))
    }
}
