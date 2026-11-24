// The home page: every note, in folders. The folder's name at the top with Settings beside it, the folders as chips
// under it, then the notes (pinned first) as a grid of cards or a list, and the Pill at the bottom to search and start
// a note. Hold a note to drag it somewhere else, or let go to get its menu.
package com.jlees.minotes.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.jlees.minotes.ui.swatch
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.delay

/** A note being dragged: which, where the finger is (window px), and where on the note it took hold. */
private class Drag {
    var id by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var rawToWindow = Offset.Zero
    var lastTarget: String? = null
    var warned = false
}

@Composable
fun HomeScreen(vm: NotesViewModel, statusDp: Float, navDp: Float, imeDp: () -> Float) {
    val ui = LocalUi.current
    val s = vm.settings
    val haptics = LocalHaptics.current
    val shown = visibleNotes(vm.notes.notes, vm.folder, vm.query, s.sort)
    val folderName = vm.notes.folders.firstOrNull { it.id == vm.folder }?.name
    val drag = remember { Drag() }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var pillHidden by remember { mutableStateOf(false) }
    LaunchedEffect(vm.query) { if (vm.query.isNotEmpty()) pillHidden = false }

    // scrolling down tucks the Pill away; scrolling up brings it back (Look & Feel › Auto Hide Pill)
    val hide = remember(s.hidePillOnScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (s.hidePillOnScroll && vm.query.isEmpty()) { if (available.y < -8f) pillHidden = true else if (available.y > 8f) pillHidden = false }
                return Offset.Zero
            }
        }
    }

    fun checkSwap() {
        val id = drag.id ?: return
        if (s.sort != SortOrder.MANUAL || vm.query.isNotEmpty()) return
        val me = shown.firstOrNull { it.id == id } ?: return
        val target = shown.firstOrNull { it.id != id && it.pinned == me.pinned && vm.cardBounds[it.id]?.contains(drag.pointer) == true }
        if (target == null) { drag.lastTarget = null; return }
        if (target.id == drag.lastTarget) return
        drag.lastTarget = target.id
        vm.notes.reorder(id, target.id, after = shown.indexOf(target) > shown.indexOf(me))
        haptics?.tick()
    }

    @Composable
    fun gesturesFor(n: Note) = Modifier
        .cardSemantics(displayTitle(n), onOpen = { vm.open(n.id) }, onMenu = { vm.menuFor = n.id })
        .cardGestures(
            onTap = { vm.open(n.id) },
            onHold = { raw, local ->
                haptics?.longPress()
                val b = vm.cardBounds[n.id] ?: return@cardGestures
                drag.rawToWindow = b.topLeft + local - raw
                drag.pointer = raw + drag.rawToWindow; drag.grab = local; drag.lastTarget = null; drag.warned = false
                drag.id = n.id
            },
            onDrag = { raw ->
                drag.pointer = raw + drag.rawToWindow
                if (s.sort != SortOrder.MANUAL && !drag.warned) { drag.warned = true; Notices.show("Sort by Manual to rearrange") }
                checkSwap()
            },
            onRelease = { moved -> if (!moved) vm.menuFor = n.id; drag.id = null },
            onPress = { pressedId = if (it) n.id else null },
        )

    /** Where a note's card is (for dragging, and for the editor to grow out of); the dragged card follows the finger. */
    fun Modifier.place(n: Note) = onGloballyPositioned { vm.cardBounds[n.id] = it.boundsInWindow() }
        .zIndex(if (drag.id == n.id) 10f else 0f)
        .graphicsLayer {
            if (drag.id == n.id) vm.cardBounds[n.id]?.let { b -> translationX = drag.pointer.x - drag.grab.x - b.left; translationY = drag.pointer.y - drag.grab.y - b.top }
        }

    Box(Modifier.fillMaxSize().background(homeBackground())) {
        Column(Modifier.fillMaxSize().padding(top = statusDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxSize()) {
                Header(vm, folderName, shown.size)
                FolderChips(vm)
                val bottom = (navDp + 120).dp
                if (shown.isEmpty()) Box(Modifier.fillMaxSize()) {
                    if (vm.query.isNotBlank()) EmptyState("search", "No Matches", top = 80.dp) else EmptyState("sticky_note_2", "No Notes Yet", top = 80.dp)
                } else if (s.layout == NotesLayout.GRID) {
                    val grid = rememberLazyStaggeredGridState()
                    AutoScroll(drag, grid, null, ::checkSwap)
                    val pinned = shown.count { it.pinned }
                    LazyVerticalStaggeredGrid(
                        StaggeredGridCells.Adaptive(160.dp), Modifier.fillMaxSize().nestedScroll(hide), grid,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottom),
                        verticalItemSpacing = 12.dp, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pinned > 0 && pinned < shown.size) item(key = "h:pinned", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Pinned") }
                        items(shown.take(pinned), key = { it.id }) { n -> GridItem(vm, n, drag.id == n.id, pressedId == n.id, gesturesFor(n), Modifier.place(n).animateItem(placementSpec = if (drag.id == n.id) null else Motion.spatial())) }
                        if (pinned > 0 && pinned < shown.size) item(key = "h:others", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Others") }
                        items(shown.drop(pinned), key = { it.id }) { n -> GridItem(vm, n, drag.id == n.id, pressedId == n.id, gesturesFor(n), Modifier.place(n).animateItem(placementSpec = if (drag.id == n.id) null else Motion.spatial())) }
                    }
                } else {
                    val list = rememberLazyListState()
                    AutoScroll(drag, null, list, ::checkSwap)
                    val rowH = rowHeight(s.uiPadding, s.fontScale) + 8f
                    LazyColumn(
                        Modifier.fillMaxSize().nestedScroll(hide), list,
                        contentPadding = PaddingValues(start = listSides(), end = listSides(), top = 8.dp, bottom = bottom),
                        verticalArrangement = Arrangement.spacedBy(listGap().dp),
                    ) {
                        items(shown, key = { it.id }) { n ->
                            Box(Modifier.place(n).animateItem(placementSpec = if (drag.id == n.id) null else Motion.spatial())) {
                                val row = @Composable { NoteRowView(n, s.showDates, drag.id == n.id, pressedId == n.id, gesturesFor(n), rowH) }
                                if (s.swipeRows) SwipeActionRow(null, onDelete = { vm.deleteNote(n.id) }, onEdit = { vm.togglePin(n.id) }, editIcon = if (n.pinned) "keep_off" else "keep", editLabel = if (n.pinned) "Unpin" else "Pin") { row() }
                                else row()
                            }
                        }
                    }
                }
            }
        }
        HomePill(vm, pillHidden, onShow = { pillHidden = false }, navDp, imeDp)
    }
}

@Composable
private fun GridItem(vm: NotesViewModel, n: Note, lifted: Boolean, pressed: Boolean, gestures: Modifier, modifier: Modifier) {
    // under All Notes, each card says which folder it's in
    val folder = if (vm.folder == null) vm.notes.folders.firstOrNull { it.id == n.folder }?.name else null
    Box(modifier) { NoteCardView(n, vm.settings.previewLines, vm.settings.showDates, lifted, pressed, folder, gestures) }
}

/** While a note is dragged near the top or bottom edge, the notes scroll to bring more into reach. */
@Composable
private fun AutoScroll(drag: Drag, grid: LazyStaggeredGridState?, list: LazyListState?, onStep: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenH = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edge = with(density) { 96.dp.toPx() }
    val step by rememberUpdatedState(onStep)
    LaunchedEffect(drag.id) {
        while (drag.id != null) {
            val y = drag.pointer.y
            val by = when { y < edge * 2.2f -> -14f; y > screenH - edge -> 14f; else -> 0f }
            if (by != 0f) { grid?.scrollBy(by); list?.scrollBy(by); step() }
            delay(16)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val ui = LocalUi.current
    Txt(text, 13f, ui.cs.primary, ui.bold, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
private fun Header(vm: NotesViewModel, folderName: String?, count: Int) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(folderName ?: "All Notes", 30f, ui.cs.onSurface, ui.extrabold, maxLines = 1)
            Txt(if (count == 1) "1 note" else "$count notes", 13f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 18f)
        }
        FilledTonalIconButton(onClick = { vm.sheet = Sheet.SETTINGS }) { Sym("settings", 24f, ui.cs.onSecondaryContainer, label = "Settings") }
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
        item(key = "all") { Chip("All Notes", live.size, null, vm.folder == null, onClick = { vm.showFolder(null) }) }
        items(vm.notes.folders, key = { it.id }) { f ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Chip(f.name, live.count { it.folder == f.id }, f.color.takeIf { it != com.jlees.minotes.notes.NoteColor.NONE }?.let { swatch(it) }, vm.folder == f.id,
                    onClick = { vm.showFolder(f.id) }, onLongClick = { haptics?.longPress(); menu = true })
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = ui.cs.surfaceContainerHigh) {
                    DropdownMenuItem({ Txt("Rename", 14f, ui.cs.onSurface, ui.semibold) }, { menu = false; vm.ask = Ask.RenameFolder(f.id) }, leadingIcon = { Sym("edit", 20f, ui.cs.onSurfaceVariant) })
                    DropdownMenuItem({ Txt("Delete Folder", 14f, ui.cs.error, ui.semibold) }, { menu = false; vm.deleteFolder(f.id) }, leadingIcon = { Sym("delete", 20f, ui.cs.error) })
                }
            }
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
private fun Chip(label: String, count: Int, dot: androidx.compose.ui.graphics.Color?, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
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
        if (dot != null) Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(dot))
        Txt(label, 14f, fg, ui.semibold, maxLines = 1)
        Txt("$count", 12f, fg.copy(alpha = 0.7f), ui.semibold, Modifier.padding(start = 6.dp))
    }
}
