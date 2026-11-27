// The home page's main bar (the mi. standard): Recently Deleted at the left edge, Settings at the right, and the Pill
// between them holding search, + (a new note) and ⋮. Swiping the Pill sideways pulls in the page at that side;
// holding it, or tapping search, widens it from its centre into the search field. ⋮ opens a panel growing from it.
package com.jlees.minotes.home

import com.jlees.minotes.scan.Scanner
import com.jlees.minotes.scan.rememberScanner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.notes.visibleNotes
import com.jlees.minotes.ui.common.*
import com.jlees.minotes.ui.settings.ChoiceItem
import com.jlees.minotes.data.pans
import com.jlees.minotes.ui.settings.Item
import com.jlees.minotes.ui.settings.SectionHeader
import com.jlees.minotes.ui.settings.segment
import kotlin.math.abs

@Composable
fun BoxScope.HomePill(vm: NotesViewModel, navDp: Float, imeDp: () -> Float, dragSheet: (Sheet, Float) -> Unit, releaseSheet: (Boolean) -> Unit) {
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    // ⋮'s scans: a scan becomes a new note in the folder showing, as text shared from another app does. Kept here, not in
    // the menu, which is gone by the time the camera or Files answers
    val scan = rememberScanner { vm.newNoteFrom(it) }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current.density
    // searching: the Pill is the search field. Back from a note with a search still in it, it's still a field (but
    // the keyboard stays down until it's tapped)
    var searching by rememberSaveable { mutableStateOf(vm.query.isNotEmpty()) }
    var focusOnOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var swipeTo by remember { mutableStateOf<Sheet?>(null) } // the page a swipe is pulling in
    var swipeOpen by remember { mutableFloatStateOf(0f) }    // and how far (0..1)
    var addBounds by remember { mutableStateOf<Rect?>(null) }
    var dotsX by remember { mutableFloatStateOf(0f) }
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()
    @OptIn(ExperimentalLayoutApi::class) val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(keyboardUp) { if (!keyboardUp && searching) focus.clearFocus() }
    // nothing matches the search: the field shakes its head
    val noMatch = vm.query.isNotBlank() && visibleNotes(vm.notes.notes, vm.folder, vm.query, vm.settings.sort).isEmpty()
    val shake = shakeOffset(noMatch)

    fun search() { menu = false; focusOnOpen = true; searching = true }
    fun endSearch() { vm.query = ""; searching = false; focus.clearFocus() }
    BackHandler(enabled = searching) { endSearch() }
    BackHandler(enabled = menu) { menu = false }

    // ⋮'s panel: a tap outside closes it
    if (menu) Box(Modifier.fillMaxSize().blockTouches { menu = false })
    // it grows out of (and shrinks back into) the ⋮
    val panelW = minOf(screenW - 24f, COMFORTABLE_WIDTH.toFloat())
    val from = TransformOrigin(((dotsX - (screenW - panelW) / 2f) / panelW).coerceIn(0f, 1f), 1f)
    AnimatedVisibility(
        menu, Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(tween(120)) + scaleIn(Motion.noBounce(), initialScale = 0.85f, transformOrigin = from),
        exit = fadeOut(Motion.exit(100)) + scaleOut(Motion.exit(120), targetScale = 0.9f, transformOrigin = from),
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = (navDp + 88).dp).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(ui.radius.coerceAtMost(28).dp))
                .background(ui.cs.surfaceContainerLow, RoundedCornerShape(ui.radius.coerceAtMost(28).dp))
                .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) { CompositionLocalProvider(LocalPan provides vm.settings.pans(com.jlees.minotes.data.PanArea.SETTINGS)) { HomeMenu(vm, scan) { menu = false } } } // pans as Settings does
    }

    // the bar: 16 in from the sides, 12 above the navigation bar (above the keyboard while searching)
    Box(
        // on the keyboard only for its own search: another field's keyboard (a dialog's) leaves the bar where it is
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().paddingDp(bottom = { if (searching) maxOf(imeDp(), navDp) else navDp })
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp).widthIn(max = COMFORTABLE_WIDTH.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slot(!searching, Modifier.align(Alignment.CenterStart), fadeIn() + scaleIn(), fadeOut() + scaleOut()) {
            Box(Modifier.graphicsLayer { edgeIn(vm.noteOpen()) }) { EdgeButton("delete", "Recently Deleted") { menu = false; vm.sheet = Sheet.TRASH } }
        }
        // a Box rather than a Surface: its shape, shadow and colour sit outside the size animation, so the Pill stays
        // round at every width while it grows into the search field
        Box(
            Modifier.onGloballyPositioned { if (!searching) vm.barPill = it.boundsInWindow() }
                // hidden while a note is open or growing/shrinking: the toolbar Pill turning into this one stands in
                .graphicsLayer { alpha = if (vm.noteOpen() > 0.001f) 0f else 1f }
                // (never once a swipe has begun pulling a page in)
                .pillLongPress(!searching && vm.settings.longPressSearch) { if (swipeTo == null && vm.sheet == Sheet.NONE) search() }
                // swipe sideways: the page at that side comes in with the finger (a swipe right, Recently Deleted
                // from the left; left, Settings from the right) and ticks where letting go opens it, as the browser's
                // main bar does. The Pill itself stays put
                .panGesture(
                    enabled = !searching && !menu,
                    onStart = { swipeTo = null; swipeOpen = 0f },
                    onMove = { x, y ->
                        if (swipeTo == null) { if (abs(y) > abs(x)) return@panGesture; swipeTo = if (x > 0) Sheet.TRASH else Sheet.SETTINGS }
                        val p = ((if (swipeTo == Sheet.TRASH) x else -x) / screenW).coerceIn(0f, 1f)
                        if ((p >= OPEN_AT) != (swipeOpen >= OPEN_AT)) haptics?.tick()
                        swipeOpen = p
                        dragSheet(swipeTo!!, p)
                    },
                    onRelease = { x, _, vx, _ ->
                        swipeTo?.let { to ->
                            // where the finger let go (not the last move seen: a busy frame can drop moves); a flick
                            // toward the page opens it too, however short
                            val p = ((if (to == Sheet.TRASH) x else -x) / screenW).coerceIn(0f, 1f)
                            val toward = (if (to == Sheet.TRASH) vx else -vx) * density * 1000f
                            releaseSheet(p >= OPEN_AT || (toward > FLICK_PX_PER_S && p > 0.03f))
                        }
                        swipeTo = null
                    },
                    onTap = null,
                )
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Search notes") { search(); true }) // the long press, for screen readers
                }
                .shadow(6.dp, CircleShape).background(ui.cs.surfaceContainerHigh, CircleShape)
                // grows from its middle: both edges move out to the field's width (and back), the buttons staying put
                .animateContentSize(Motion.noBounce(), alignment = Alignment.Center)
                .then(if (searching) Modifier.fillMaxWidth() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Slot(searching, enter = PILL_IN, exit = PILL_OUT) {
                // a field reports "not focused" once as it appears, before it takes focus: only a loss after having it counts
                var hadFocus by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp).graphicsLayer { translationX = shake.dp.toPx() }, contentAlignment = Alignment.Center) {
                    SheetSearchField(
                        vm.query, { vm.query = it }, placeholder = "Search notes", autoFocus = focusOnOpen,
                        // let go of with nothing typed, the search ends; with a search in it, it stays to browse the results
                        onFocus = { if (it) hadFocus = true else if (hadFocus && vm.query.isBlank()) searching = false },
                        trailing = { IconButton(onClick = ::endSearch) { Sym("close", 22f, ui.cs.onSurfaceVariant, label = "Close search") } },
                    )
                }
            }
            Slot(!searching, enter = PILL_IN, exit = PILL_OUT) {
                MainBarButtons(
                    onSearch = ::search,
                    onNew = { menu = false; vm.newNote(from = addBounds) },
                    onMenu = { menu = !menu },
                    menuOpen = menu,
                    addModifier = Modifier.onGloballyPositioned { addBounds = it.boundsInWindow() },
                    menuModifier = Modifier.onGloballyPositioned { dotsX = it.boundsInWindow().center.x / density },
                )
            }
        }
        Slot(!searching, Modifier.align(Alignment.CenterEnd), fadeIn() + scaleIn(), fadeOut() + scaleOut()) {
            Box(Modifier.graphicsLayer { edgeIn(vm.noteOpen()) }) { EdgeButton("settings", "Settings") { menu = false; vm.sheet = Sheet.SETTINGS } }
        }
    }
}

/** The edge buttons, as a note shrinks back into its card ([open] 1 → 0): they appear in place, growing from 0.6. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.edgeIn(open: Float) {
    val a = ((1f - open - 0.2f) / 0.45f).coerceIn(0f, 1f)
    alpha = a; scaleX = 0.6f + 0.4f * a; scaleY = 0.6f + 0.4f * a
}

/** How far a page has to be pulled in for letting go to open it. */
private const val OPEN_AT = 0.2f
/** A release at least this fast toward the page opens it, however far it was pulled. */
private const val FLICK_PX_PER_S = 700f

/** A button outside the Pill at a side of the screen: icon only, straight on the background. */
@Composable
fun EdgeButton(icon: String, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, Modifier.size(52.dp).pressScale(press), interactionSource = press) { Sym(icon, 26f, LocalUi.current.cs.onSurface, label = label) }
}

/** The Pill's buttons: search, + and ⋮. Shared with the walkthrough's copy of the bar. */
@Composable
fun MainBarButtons(onSearch: () -> Unit, onNew: () -> Unit, onMenu: () -> Unit, menuOpen: Boolean = false, addModifier: Modifier = Modifier, menuModifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val pressSearch = remember { MutableInteractionSource() }
    val pressNew = remember { MutableInteractionSource() }
    val pressMenu = remember { MutableInteractionSource() }
    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSearch, Modifier.padding(horizontal = 4.dp).size(52.dp).pressScale(pressSearch), interactionSource = pressSearch) { Sym("search", 24f, ui.cs.onSurface, label = "Search notes") }
        FilledIconButton(
            onClick = onNew, modifier = addModifier.size(56.dp).pressScale(pressNew), interactionSource = pressNew,
            // shaped by the Corners setting, as everything rounded is: a circle at Round, a rounded square at Soft
            shape = androidx.compose.foundation.shape.RoundedCornerShape(ui.radius.dp),
            // the main action in the accent itself (mainAction: the seed as picked), so it stands out in every theme
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ui.mainAction, contentColor = ui.onMainAction),
        ) { Sym("add", 30f, ui.onMainAction, label = "New note") }
        IconButton(onClick = onMenu, menuModifier.padding(horizontal = 4.dp).size(52.dp).pressScale(pressMenu), interactionSource = pressMenu) {
            Sym(if (menuOpen) "close" else "more_vert", 24f, ui.cs.onSurface, label = if (menuOpen) "Close menu" else "Menu")
        }
    }
}

/** ⋮'s panel, built as Settings is: rows with a tonal icon circle, in rounded blocks under headings. */
@Composable
private fun ColumnScope.HomeMenu(vm: NotesViewModel, scan: Scanner, close: () -> Unit) {
    val s = vm.settings
    SectionHeader(null, "New")
    Box(Modifier.segment(0, 4)) { Item("checklist", "New Checklist", null, onClick = { close(); vm.newNote(checklist = true) }) }
    Box(Modifier.segment(1, 4)) { Item("create_new_folder", "New Folder", null, onClick = { close(); vm.ask = Ask.NewFolder() }) }
    Box(Modifier.segment(2, 4)) { Item("photo_camera", "Scan with Camera", null, onClick = { close(); scan.camera() }) }
    Box(Modifier.segment(3, 4)) { Item("image", "Scan from Files", null, onClick = { close(); scan.files() }) }
    SectionHeader(null, "View")
    Box(Modifier.segment(0, 2)) { ChoiceItem("grid_view", "Layout", null, s.layout) { vm.updateSettings { copy(layout = it) } } }
    Box(Modifier.segment(1, 2)) { ChoiceItem("sort", "Sort By", null, s.sort) { vm.updateSettings { copy(sort = it) } } }
}

/** A plain AnimatedVisibility, called from here (inside a Column, the Column-only overload would be picked). */
@Composable
private fun Slot(visible: Boolean, modifier: Modifier = Modifier, enter: EnterTransition, exit: ExitTransition, content: @Composable () -> Unit) =
    AnimatedVisibility(visible, modifier, enter = enter, exit = exit) { content() }

// the Pill's buttons and its search field, fading as the other takes over: a quick fade out as the field fills the
// Pill's width, and the Pill shrinks back only once it has gone
private val PILL_IN = fadeIn(tween(200, delayMillis = 60))
private val PILL_OUT = fadeOut(tween(90))
