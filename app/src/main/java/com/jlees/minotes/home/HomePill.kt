// The home page's Pill: a search field and ⋮ (its menu grows up inside the Pill), with + beside them for a new note.
// Swiping it sideways goes to the previous or next folder; holding it starts a search. Scrolling the notes down tucks it
// away into a small handle, and scrolling up (or tapping the handle) brings it back.
package com.jlees.minotes.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.data.NotesLayout
import com.jlees.minotes.data.SortOrder
import com.jlees.minotes.notes.nextFolder
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.launch

const val PILL_HEIGHT = 70f

@Composable
fun BoxScope.HomePill(vm: NotesViewModel, hidden: Boolean, onShow: () -> Unit, navDp: Float, imeDp: () -> Float) {
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    var typing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val dx = remember { Animatable(0f) }
    val tuck by animateFloatAsState(if (hidden && !typing && !menu) 1f else 0f, if (hidden) Motion.exit(200) else Motion.spatial(), label = "tuck")
    @OptIn(ExperimentalLayoutApi::class) val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp && typing) focus.clearFocus() }
    var addBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()

    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().paddingDp(bottom = { maxOf(imeDp(), navDp) })
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // the recall handle, while the Pill is tucked away
        if (tuck > 0.5f) Box(
            Modifier.size(width = 96.dp, height = 48.dp).graphicsLayer { alpha = (tuck - 0.5f) * 2f }.clip(CircleShape).touchable(onClick = onShow),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(width = 56.dp, height = 6.dp).clip(CircleShape).background(ui.cs.onSurfaceVariant.copy(alpha = 0.6f))) }

        Column(
            Modifier.widthIn(max = PILL_MAX_WIDTH.dp).fillMaxWidth()
                .graphicsLayer {
                    translationY = tuck * (PILL_HEIGHT + 40).dp.toPx(); alpha = 1f - tuck
                    translationX = dx.value.dp.toPx()
                    val r = if (ui.radius >= 20) 35.dp.toPx() else (ui.radius * 2).dp.toPx()
                    shape = RoundedCornerShape(minOf(r, size.height / 2)); clip = true; shadowElevation = 10.dp.toPx()
                }
                .background(ui.pillBar)
                // swipe sideways: the folder before or after; it follows the finger, ticks where letting go acts, springs back
                .panGesture(
                    enabled = !typing && !menu && vm.settings.swipeFolders,
                    onMove = { x, y ->
                        if (kotlin.math.abs(x) < kotlin.math.abs(y)) return@panGesture
                        val was = kotlin.math.abs(dx.value) > screenW * 0.2f * 0.4f
                        scope.launch { dx.snapTo(x * 0.4f) } // resistance: it moves less than the finger
                        if ((kotlin.math.abs(x * 0.4f) > screenW * 0.2f * 0.4f) != was) haptics?.tick()
                    },
                    onRelease = { x, _, vx, _ ->
                        if (kotlin.math.abs(x) > screenW * 0.2f || kotlin.math.abs(vx) > 0.7f && kotlin.math.abs(x) > 24f) {
                            vm.showFolder(nextFolder(vm.notes.folders, vm.folder, forward = x < 0))
                        }
                        scope.launch { dx.animateTo(0f, Motion.springBack()) }
                    },
                    onTap = null,
                )
                .padding(horizontal = 10.dp),
        ) {
            AnimatedVisibility(
                menu,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(Motion.exit(150), shrinkTowards = Alignment.Bottom) + fadeOut(Motion.exit(100)),
            ) { Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) { PillMenu(vm) { menu = false } } }
            Row(Modifier.fillMaxWidth().height(PILL_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
                // the search field
                Row(
                    Modifier.weight(1f).height((PILL_HEIGHT * 0.7f).dp).background(ui.pillField, RoundedCornerShape((ui.radius * 1.5f).dp)).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
                    BasicTextField(
                        vm.query, { vm.query = it }, Modifier.weight(1f).focusRequester(fr).onFocusChanged { typing = it.isFocused; if (it.isFocused) menu = false }, singleLine = true,
                        textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 16.sp),
                        cursorBrush = SolidColor(ui.cs.primary),
                        decorationBox = { inner -> Box { if (vm.query.isEmpty()) Txt("Search notes", 16f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false, maxLines = 1); inner() } },
                    )
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = ""; fr.requestFocus() }) { Sym("close", 22f, ui.cs.onSurfaceVariant, label = "Clear") }
                    else IconButton(onClick = { menu = !menu; focus.clearFocus() }) { Sym(if (menu) "close" else "more_vert", 24f, ui.cs.onSurfaceVariant, label = if (menu) "Close menu" else "More options") }
                }
                // + : a new note, growing out of this button
                val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box(
                    Modifier.padding(start = 8.dp).pressScale(press).size((PILL_HEIGHT * 0.7f).dp).clip(CircleShape).background(ui.cs.primary)
                        .onGloballyPositioned { addBounds = it.boundsInWindow() }
                        .touchable(source = press) { menu = false; focus.clearFocus(); vm.newNote(from = addBounds) },
                    contentAlignment = Alignment.Center,
                ) { Sym("add", 28f, ui.cs.onPrimary, label = "New note") }
            }
        }
    }
}

/** The Pill's ⋮ menu: new checklist and folder, how notes are laid out and sorted, Recently Deleted. */
@Composable
private fun ColumnScope.PillMenu(vm: NotesViewModel, close: () -> Unit) {
    val ui = LocalUi.current
    val s = vm.settings
    SheetMenuItem("checklist", "New Checklist") { close(); vm.newNote(checklist = true) }
    SheetMenuItem("create_new_folder", "New Folder") { close(); vm.ask = Ask.NewFolder() }
    SheetMenuItem(if (s.layout == NotesLayout.GRID) "view_agenda" else "grid_view", if (s.layout == NotesLayout.GRID) "Show as List" else "Show as Grid") {
        vm.updateSettings { copy(layout = if (layout == NotesLayout.GRID) NotesLayout.LIST else NotesLayout.GRID) }
    }
    Txt("Sort By", 12f, ui.cs.onSurfaceVariant, ui.bold, Modifier.padding(start = 10.dp, top = 8.dp, bottom = 6.dp))
    ConnectedChoice(SortOrder.entries.map { it.name to it.label }, s.sort.name, Modifier.padding(bottom = 6.dp)) { v -> vm.updateSettings { copy(sort = SortOrder.valueOf(v)) } }
    SheetMenuItem("delete", "Recently Deleted") { close(); vm.sheet = Sheet.TRASH }
}
