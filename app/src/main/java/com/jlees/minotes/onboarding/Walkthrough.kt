// The gesture walkthrough: a hands-on tour over a light copy of mi. Notes itself, everything where it really is: the home
// page (its header, folders, notes and the main bar at the bottom), an open note, Recently Deleted and Settings sliding
// in from their sides with their search Pills, and the Pill turning into search. It teaches only the gestures: the
// buttons explain themselves. Each step asks for the
// real gesture and the copy responds as the app does; a caption card says what to try, ticking it off as you do.
// Nothing real is touched: no notes or settings change, except the flag that it was seen. Shows once to everyone
// (Settings.walkthroughSeen), and again from Settings › About › Gesture Walkthrough.
package com.jlees.minotes.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.home.EdgeButton
import com.jlees.minotes.home.MainBarButtons
import com.jlees.minotes.home.NoteCardView
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.ui.common.*
import com.jlees.minotes.ui.homeBackground
import com.jlees.minotes.ui.MorphingPill
import com.jlees.minotes.ui.TOOLBAR_ICONS
import com.jlees.minotes.ui.ToolbarIcons
import com.jlees.minotes.ui.settings.Item
import com.jlees.minotes.ui.settings.segment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** One thing a step asks you to try: a line in the caption's checklist. [leaves]: going back out of the step's page. */
private enum class Task(val label: String, val leaves: Boolean = false) {
    UNDO("Swipe right to undo"),
    REDO("Swipe left to redo"),
    RESTORE("Swipe a note right to restore it"),
    LEAVE_TRASH("Swipe the search Pill left to go back", leaves = true),
    LEAVE_SETTINGS("Swipe the search Pill right to go back", leaves = true),
}

/** The two pages the main bar's Pill opens, and the side each comes in from. */
private enum class Page(val title: String, val fromLeft: Boolean) { TRASH("Recently Deleted", true), SETTINGS("Settings", false) }

/** The tour. [inside]: the caption once the step's page is open. [chips]: what the step needs, ticked off as it's done. */
private enum class Step(val title: String, val text: String, val inside: String = "", val chips: List<Task> = emptyList(), val page: Page? = null) {
    HOLD("Hold a Note", "Hold a note to pick it up, then drag it somewhere else. Let go without moving for its menu."),
    TRASH("Recently Deleted", "Swipe the Pill right for Recently Deleted.", "Notes you delete wait here for a while.",
        listOf(Task.RESTORE, Task.LEAVE_TRASH), Page.TRASH),
    SETTINGS("Settings", "Swipe the Pill left for Settings.", "Every setting is here. Inside a section, swipe its Pill right to go up a level.",
        listOf(Task.LEAVE_SETTINGS), Page.SETTINGS),
    SEARCH("Search", "Hold the Pill to search your notes."),
    OPEN("Open a Note", "Tap a note to open it."),
    UNDO("Undo and Redo", "In a note, swipe its Pill right to undo and left to redo.", chips = listOf(Task.UNDO, Task.REDO)),
    BACK("Back to Your Notes", "Swipe the note's Pill up to go back to your notes."),
    DONE("You're All Set", "That's every gesture. Enjoy mi. Notes."),
}

/** Which way the fingertip hint moves. */
private enum class Move { HOLD, UP, LEFT, RIGHT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Walkthrough(vm: NotesViewModel) {
    if (vm.settings.walkthroughSeen) return
    val ui = LocalUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val px = density.density
    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()

    // holding the Pill to search is only taught while that gesture is on (Settings › Gestures)
    val steps = remember(vm.settings.longPressSearch) { Step.entries.filter { it != Step.SEARCH || vm.settings.longPressSearch } }
    var step by remember { mutableStateOf(steps.first()) }
    var stepDone by remember { mutableStateOf(false) }
    val lit = remember { mutableStateListOf<Task>() }
    fun finish() = vm.updateSettings { copy(walkthroughSeen = true) }
    fun advance() { val i = steps.indexOf(step); if (i < steps.lastIndex) { step = steps[i + 1]; stepDone = false; lit.clear() } }
    fun complete() {
        if (stepDone) return
        stepDone = true; haptics?.tick()
        val at = step
        scope.launch { delay(900); if (step == at) advance() }
    }
    fun rowsDone() = step.chips.filterNot { it.leaves }.all { it in lit }
    fun light(t: Task) {
        if (t !in step.chips || t in lit) return
        lit += t; haptics?.tick()
        if (step.chips.all { it in lit }) complete()
    }
    BackHandler { finish() }

    // ---- the copy's state
    val notes = remember { mutableStateListOf(*GUIDE_NOTES.toTypedArray()) }
    val open = remember { Animatable(0f) }     // the open note: 0 its card … 1 filling the screen (the tour starts on the home page)
    var undone by remember { mutableStateOf(false) } // its last line taken back by an undo
    val pageP = remember { Animatable(0f) }    // the step's page: 0 away … 1 in place
    val search = remember { Animatable(0f) }   // the Pill turning into the search field
    var lifted by remember { mutableStateOf<String?>(null) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    var toolRect by remember { mutableStateOf<Rect?>(null) }
    var pagePillRect by remember { mutableStateOf<Rect?>(null) }
    var rowRect by remember { mutableStateOf<Rect?>(null) }
    var restRect by remember { mutableStateOf<Rect?>(null) }
    var pageOpened by remember { mutableStateOf(false) }
    val cardRects = remember { mutableStateMapOf<String, Rect>() }
    val noteId = "guide_2" // the note the tour opens
    LaunchedEffect(step) { pageP.snapTo(0f); pageOpened = false }

    Box(Modifier.fillMaxSize().zIndex(1000f).background(homeBackground()).blockTouches()) {
        val topPad = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

        // ---- the home page, as the app lays it out
        Column(Modifier.fillMaxSize().padding(top = topPad)) {
            Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
                Txt("All Notes", 30f, ui.cs.onSurface, ui.extrabold, maxLines = 1)
                Txt("${notes.size} notes", 13f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 18f)
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All Notes" to true, "Ideas" to false).forEach { (name, on) ->
                    Box(Modifier.height(40.dp).clip(CircleShape).background(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHigh).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Txt(name, 14f, if (on) ui.cs.onPrimaryContainer else ui.cs.onSurfaceVariant, ui.semibold)
                    }
                }
            }
            // the notes in two columns (Grid), each card as the app draws it
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(0, 1).forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        notes.filterIndexed { i, _ -> i % 2 == col }.forEach { n ->
                            val lift by animateFloatAsState(if (lifted == n.id) 1.02f else 1f, Motion.fastSpatial(), label = "lift")
                            Box(
                                Modifier.onGloballyPositioned { cardRects[n.id] = it.boundsInRoot() }
                                    .graphicsLayer {
                                        scaleX = lift; scaleY = lift
                                        alpha = if (n.id == noteId && open.value > 0f) 0f else 1f // the note is out of its card
                                    }
                                    .combinedClickable(onClick = {
                                        if (step == Step.OPEN && n.id == noteId) scope.launch { open.animateTo(1f, Motion.switcher); complete() }
                                    }, onLongClick = {
                                        if (step == Step.HOLD) { haptics?.longPress(); lifted = n.id; complete(); scope.launch { delay(700); lifted = null } }
                                    }),
                            ) { NoteCardView(n, 4, false, lifted == n.id, false, null, Modifier, uniform = true) }
                        }
                    }
                }
            }
        }

        // ---- the main bar, where it always is: Recently Deleted · ( search  +  ⋮ ) · Settings
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val s = search.value
            // the note or a page over it: the edge buttons come back as it goes (as in the app), and the centre Pill hides
            // while the morphing Pill stands in for it
            val away = maxOf(open.value, pageP.value)
            Box(Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = (1f - s) * (1f - away) }) { EdgeButton("delete", "Recently Deleted") {} }
            Box(
                Modifier.onGloballyPositioned { barRect = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (maxOf(open.value, pageP.value) > 0.001f) 0f else 1f }
                    .pillLongPress(step == Step.SEARCH) {
                        scope.launch { search.animateTo(1f, Motion.noBounce()); complete(); delay(800); search.animateTo(0f, Motion.exit(200)) }
                    }
                    .panGesture(
                        enabled = true,
                        onMove = { x, _ -> step.page?.let { p -> val pull = (if (p.fromLeft) x else -x) / screenW; if (pull > 0f) scope.launch { pageP.snapTo(pull.coerceIn(0f, 1f)) } } },
                        onRelease = { x, _, vx, _ ->
                            val p = step.page ?: return@panGesture
                            val pull = (if (p.fromLeft) x else -x) / screenW
                            val toward = (if (p.fromLeft) vx else -vx) * px * 1000f // px/s
                            val go = pull > 0.2f || (toward > 700f && pull > 0.03f)
                            if (go) pageOpened = true
                            scope.launch { pageP.animateTo(if (go) 1f else 0f, Motion.switcher) }
                        },
                    )
                    .shadow(6.dp, CircleShape).background(ui.cs.surfaceContainerHigh, CircleShape)
                    .then(if (s > 0f) Modifier.fillMaxWidth(lerp(0.5f, 1f, s)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (s < 0.5f) Box(Modifier.graphicsLayer { alpha = 1f - s * 2f }) {
                    MainBarButtons(onSearch = {}, onMenu = {}, onNew = {}) // its buttons explain themselves: the tour is for gestures
                } else Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp).graphicsLayer { alpha = (s - 0.5f) * 2f }, contentAlignment = Alignment.Center) {
                    GuideField("Search notes")
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = (1f - s) * (1f - away) }) { EdgeButton("settings", "Settings") {} }
        }

        // ---- the open note, shrinking back into its card (as the app's notes do) when its Pill is swiped up; sideways, the
        // Pill undoes and redoes, taking its last line back and returning it
        if (open.value > 0f) GuideNote(
            open.value, from = cardRects[noteId], undone = undone,
            onTool = { toolRect = it },
            toolSwipe = Modifier.panGesture(
                enabled = true,
                onMove = { x, y -> if (step == Step.BACK && y < 0 && -y > abs(x)) scope.launch { open.snapTo((1f + y / 220f).coerceIn(0.05f, 1f)) } },
                onRelease = { x, y, _, _ ->
                    when {
                        step == Step.UNDO && abs(x) > abs(y) && x > 28f && !undone -> { undone = true; light(Task.UNDO) }
                        step == Step.UNDO && abs(x) > abs(y) && x < -28f && undone -> { undone = false; light(Task.REDO) }
                        step == Step.BACK -> {
                            val close = -y / 220f > 0.12f
                            scope.launch { open.animateTo(if (close) 0f else 1f, if (close) Motion.exit(220) else Motion.switcher); if (close) complete() }
                        }
                    }
                },
            ),
        )

        // ---- the step's page, sliding in from its side with the finger
        val page = step.page
        if (page != null && pageP.value > 0f) GuidePage(
            page, pageP.value, onPill = { pagePillRect = it }, onRow = { rowRect = it }, onRestore = { light(Task.RESTORE) },
            pillSwipe = Modifier.panGesture(
                enabled = true,
                onMove = { x, _ -> if (rowsDone()) { val pull = (if (page.fromLeft) -x else x) / screenW; scope.launch { pageP.snapTo((1f - pull).coerceIn(0f, 1f)) } } },
                onRelease = { x, _, vx, _ ->
                    if (!rowsDone()) return@panGesture
                    val pull = (if (page.fromLeft) -x else x) / screenW
                    val back = pull > 0.25f || (if (page.fromLeft) -vx else vx) * px * 1000f > 500f
                    scope.launch { pageP.animateTo(if (back) 0f else 1f, Motion.switcher) }
                    if (back) step.chips.firstOrNull { it.leaves }?.let(::light)
                },
            ),
        )

        // where the note's toolbar Pill and the pages' search Pills sit (one place for all, measured once, never drawn)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(70.dp).onGloballyPositioned { restRect = it.boundsInRoot() })
        // ---- one Pill, always: as the note shrinks back (or a page slides in or out), its Pill turns into the bar's
        // (read as it draws, every frame: see MorphingPill)
        MorphingPill(
            from = { restRect }, to = { barRect },
            m = { when { open.value > 0f -> 1f - open.value; pageP.value > 0f -> 1f - pageP.value; else -> 1f } }, z = 7f,
        ) {
            if (open.value > 0f) ToolbarIcons() else GuideField("Search", Modifier.width(with(density) { ((restRect?.width ?: 0f) - 20 * px).toDp() }))
        }

        // ---- dim everything but what to try
        val pageOpen = page != null && pageP.value > 0f
        val spot = when {
            stepDone || step == Step.DONE -> null
            step == Step.UNDO || step == Step.BACK -> toolRect
            step == Step.HOLD -> cardRects[notes.firstOrNull()?.id]
            step == Step.OPEN -> cardRects[noteId]
            step == Step.SEARCH -> barRect
            pageOpen -> null
            else -> barRect
        }
        val dim by animateFloatAsState(if (spot == null) 0f else 0.5f, label = "dim")
        Canvas(Modifier.fillMaxSize().zIndex(8f).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = dim))
            spot?.let { r ->
                val pad = 8.dp.toPx()
                drawRoundRect(Color.Transparent, Offset(r.left - pad, r.top - pad), Size(r.width + pad * 2, r.height + pad * 2), CornerRadius(minOf(r.height / 2 + pad, 28.dp.toPx())), blendMode = BlendMode.Clear)
            }
        }

        // ---- the fingertip, acting out what to do next
        if (!stepDone && step != Step.DONE) {
            val hint: Pair<Rect?, Move> = when (step) {
                Step.UNDO -> toolRect to if (Task.UNDO in lit) Move.LEFT else Move.RIGHT
                Step.BACK -> toolRect to Move.UP
                Step.HOLD -> cardRects[notes.firstOrNull()?.id] to Move.HOLD
                Step.OPEN -> cardRects[noteId] to Move.HOLD
                Step.SEARCH -> barRect to Move.HOLD
                else -> when {
                    page == null -> null to Move.HOLD
                    !pageOpen -> barRect to if (page.fromLeft) Move.RIGHT else Move.LEFT
                    !rowsDone() -> rowRect to Move.RIGHT
                    else -> pagePillRect to if (page.fromLeft) Move.LEFT else Move.RIGHT
                }
            }
            hint.first?.let { FingerHint(hint.second, it) }
        }

        // ---- the caption: at the top, out of the Pill's way; while a page or the note is open it moves down to sit above
        // their Pills, so their headers stay in view
        val low = (pageOpen && pageP.value > 0.5f) || open.value > 0.5f || step == Step.HOLD || step == Step.OPEN || step == Step.DONE // out of the way of what to try
        val bias by animateFloatAsState(if (low) 1f else -1f, spring(0.85f, 300f), label = "caption")
        Box(Modifier.fillMaxSize().zIndex(9f).windowInsetsPadding(WindowInsets.systemBars).padding(top = 8.dp, bottom = 96.dp)) {
            Column(
                Modifier.align(BiasAlignment(0f, bias)).padding(horizontal = 16.dp).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(ui.cs.surfaceContainerHigh).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.forEach { s -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (steps.indexOf(s) <= steps.indexOf(step)) ui.cs.primary else ui.cs.outlineVariant)) }
                    }
                    if (step != Step.DONE) TextButton(onClick = ::finish, Modifier.padding(start = 8.dp)) { Txt("Skip", 15f, ui.cs.primary, ui.semibold) }
                }
                Txt(step.title, 22f, ui.cs.onSurface, ui.extrabold, Modifier.padding(top = 8.dp))
                Txt(if (pageP.value >= 1f && step.inside.isNotEmpty()) step.inside else step.text, 15f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 4.dp), lineHeight = 22f)
                // a page's checklist shows once it's open; going back joins it once its row task is done
                val shown = when {
                    page == null -> step.chips
                    !pageOpened -> emptyList()
                    else -> step.chips.filter { rowsDone() || !it.leaves }
                }
                if (shown.isNotEmpty()) Column(Modifier.padding(top = 12.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) { shown.forEach { Check(it.label, it in lit) } }
                if (step == Step.DONE) Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = ::finish) { Txt("Start Writing", 15f, ui.cs.onPrimary, ui.semibold) }
                } else Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.animation.AnimatedVisibility(
                        stepDone,
                        enter = androidx.compose.animation.scaleIn(spring(0.5f, 500f), initialScale = 0.5f) + androidx.compose.animation.fadeIn(Motion.enter(150)),
                        exit = androidx.compose.animation.fadeOut(Motion.exit(100)),
                    ) { Row(verticalAlignment = Alignment.CenterVertically) { Sym("check", 20f, ui.cs.primary, Modifier.padding(end = 6.dp)); Txt("Nice!", 15f, ui.cs.primary, ui.semibold) } }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        // skipping a step leaves the copy where that step would have
                        scope.launch {
                            when (step) {
                                Step.OPEN -> open.snapTo(1f)
                                Step.UNDO -> undone = false
                                Step.BACK -> open.snapTo(0f)
                                else -> pageP.snapTo(0f)
                            }
                            advance()
                        }
                    }) { Txt("Next", 15f, ui.cs.onSurfaceVariant, ui.semibold) }
                }
            }
        }
    }
}

/** The sample notes on the copy's home page. */
private val GUIDE_NOTES = listOf(
    Note("guide_1", "Welcome to mi. Notes", RichText("Tap, hold and swipe your way around.")),
    Note("guide_2", "Shopping", RichText("Eggs\nMilk\nBread\nCoffee")),
    Note("guide_3", "Ideas", RichText("A notes app with one Pill.")),
    Note("guide_4", "Trip", RichText("Pack the camera and a jacket.")),
)

/** A copy of a new note, [p] of the way out of [from] (+, or its card), with its bar and toolbar Pill. */
@Composable
private fun GuideNote(p: Float, from: Rect?, undone: Boolean, onTool: (Rect) -> Unit, toolSwipe: Modifier) {
    val ui = LocalUi.current
    Box(
        Modifier.fillMaxSize().zIndex(5f).graphicsLayer {
            val w = size.width; val h = size.height
            val o = from ?: Rect(w * 0.4f, h * 0.8f, w * 0.6f, h * 0.9f)
            val s0 = (o.width / w).coerceIn(0.05f, 1f)
            val sc = lerp(s0, 1f, p)
            transformOrigin = TransformOrigin(0f, 0f); scaleX = sc; scaleY = sc
            translationX = lerp(o.left, 0f, p); translationY = lerp(o.top, 0f, p)
            shape = RoundedCornerShape(lerp(20.dp.toPx() / s0, 0f, p).coerceAtLeast(0f)); clip = true // a spring can carry p past 1
            shadowElevation = if (p < 1f) 8.dp.toPx() else 0f
        }.background(homeBackground()).blockTouches().windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { Sym("arrow_back", 24f, ui.cs.onSecondaryContainer) }
                Txt("No Folder", 13f, ui.cs.onSurface, ui.semibold, Modifier.padding(start = 16.dp))
                Txt("  ·  Now  ·  ${if (undone) 3 else 4} words", 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
            }
            Txt("Shopping", 26f, ui.cs.onSurface, ui.extrabold, Modifier.padding(start = 24.dp, top = 8.dp))
            Txt(if (undone) "Eggs\nMilk\nBread" else "Eggs\nMilk\nBread\nCoffee", 17f, ui.cs.onSurface, ui.regular, Modifier.padding(start = 20.dp, top = 12.dp), lineHeight = 26f, scaled = false)
        }
        // its toolbar Pill, where the editor's sits
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(70.dp)
                .onGloballyPositioned { onTool(it.boundsInRoot()) }.then(toolSwipe)
                .graphicsLayer { alpha = if (p < 0.999f) 0f else 1f } // the morphing Pill stands in while it moves
                .shadow(10.dp, RoundedCornerShape(35.dp)).clip(RoundedCornerShape(35.dp)).background(ui.pillBar).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
        ) { TOOLBAR_ICONS.forEach { Sym(it, 28f, ui.cs.onSurface) } }
    }
}

/** A copy of Recently Deleted or Settings, [p] of the way in from its side, with its search Pill at the bottom. */
@Composable
private fun GuidePage(page: Page, p: Float, onPill: (Rect) -> Unit, onRow: (Rect) -> Unit, onRestore: () -> Unit, pillSwipe: Modifier) {
    val ui = LocalUi.current
    Box(
        Modifier.fillMaxSize().zIndex(6f)
            .graphicsLayer { translationX = (if (page.fromLeft) -1f else 1f) * size.width * (1f - p); shadowElevation = 8.dp.toPx() }
            .background(ui.cs.surfaceContainerLow).blockTouches().windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(page.title, 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f), maxLines = 1)
                Box(Modifier.size(48.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { Sym("close", 24f, ui.cs.onSecondaryContainer) }
            }
            if (page == Page.TRASH) {
                Txt("Notes stay here for 30 days, then they're gone for good.", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 12.dp, end = 12.dp), lineHeight = 18f)
                // the app's own swipe row: right restores, left deletes for good
                SwipeActionRow(
                    64.dp, onDelete = {}, Modifier.padding(top = 16.dp, end = 12.dp).onGloballyPositioned { onRow(it.boundsInRoot()) },
                    onEdit = onRestore, editIcon = "restore_from_trash", editLabel = "Restore", askDelete = { answer -> answer(false) },
                ) {
                    Row(Modifier.fillMaxWidth().height(64.dp).background(ui.cs.surfaceContainerLow).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 15.dp).size(44.dp).clip(CircleShape).background(rowTileColor()), contentAlignment = Alignment.Center) { Sym("sticky_note_2", 22f, ui.cs.onSurfaceVariant) }
                        Column(Modifier.weight(1f)) {
                            Txt("Old list", 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                            Txt("30 days left", 12f, ui.cs.primary, ui.semibold, maxLines = 1)
                        }
                    }
                }
            } else Column(Modifier.padding(top = 16.dp, end = 12.dp)) {
                // Settings' own rows, in their rounded block
                listOf(Triple("palette", "Look & Feel", "Colors, UI and haptics"), Triple("sticky_note_2", "Notes", "How notes appear and behave"), Triple("vibration", "Gestures", "How gestures work throughout mi. Notes"))
                    .forEachIndexed { i, (icon, title, sub) ->
                        Box(Modifier.segment(i, 3)) { Item(icon, title, sub, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) }) }
                    }
            }
        }
        // its search Pill, where the real one sits
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(70.dp)
                .onGloballyPositioned { onPill(it.boundsInRoot()) }.then(pillSwipe)
                .graphicsLayer { alpha = if (p < 0.999f) 0f else 1f } // the morphing Pill stands in while it moves
                .shadow(10.dp, RoundedCornerShape(35.dp)).clip(RoundedCornerShape(35.dp)).background(ui.pillBar).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            GuideField("Search")
        }
    }
}

/** The copies' search field: the Pill's lighter field with the search symbol and [text]. */
@Composable
private fun GuideField(text: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    val ui = LocalUi.current
    Row(modifier.height(49.dp).background(ui.pillField, RoundedCornerShape((ui.radius * 1.5f).dp)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
        Txt(text, 16f, ui.cs.onSurfaceVariant, ui.semibold, maxLines = 1)
    }
}

/** A translucent fingertip acting out [move] over [at], on a loop. */
@Composable
private fun FingerHint(move: Move, at: Rect) {
    val ui = LocalUi.current
    val t by rememberInfiniteTransition("hint").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), "t")
    val density = LocalDensity.current
    val (x, y) = when (move) { Move.HOLD -> 0f to 0f; Move.UP -> 0f to -110f * t; Move.RIGHT -> 90f * t to 0f; Move.LEFT -> -90f * t to 0f }
    val size = 30.dp
    Box(
        Modifier.zIndex(10f).graphicsLayer {
            val half = size.toPx() / 2
            translationX = at.center.x - half + x * density.density
            translationY = at.center.y - half + y * density.density
            val s = if (move == Move.HOLD) 1f - 0.2f * (if (t < 0.7f) t / 0.7f else 1f) else 1f
            scaleX = s; scaleY = s
            alpha = if (move == Move.HOLD) 0.7f else 0.8f * sin(PI * t).toFloat()
        }.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)).border(2.dp, ui.cs.primary, CircleShape),
    )
}

/** One line of a step's checklist: an empty circle, then a check that pops once it's done. */
@Composable
private fun Check(label: String, on: Boolean) {
    val ui = LocalUi.current
    val circle by animateColorAsState(if (on) ui.cs.primary else Color.Transparent, label = "check")
    val pop = popScale(on)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape).background(circle).border(2.dp, if (on) ui.cs.primary else ui.cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Sym("check", 16f, ui.cs.onPrimary) }
        Txt(label, 14f, if (on) ui.cs.onSurface else ui.cs.onSurfaceVariant, ui.semibold, Modifier.padding(start = 10.dp))
    }
}
