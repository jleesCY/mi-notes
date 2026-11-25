// The one screen: the home page, the note open over it (growing out of its card), and the sheets, menus and dialogs.
package com.jlees.minotes.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.data.PanArea
import com.jlees.minotes.data.ThemeMode
import com.jlees.minotes.data.pans
import androidx.compose.ui.graphics.luminance
import com.jlees.minotes.editor.EditorScreen
import com.jlees.minotes.home.HomeScreen
import com.jlees.minotes.logic.DYNAMIC_ACCENT
import com.jlees.minotes.logic.GRAY_ACCENT
import com.jlees.minotes.logic.hexColor
import com.jlees.minotes.onboarding.Walkthrough
import com.jlees.minotes.trash.TrashView
import com.jlees.minotes.ui.common.*
import com.jlees.minotes.ui.settings.SettingsView
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(vm: NotesViewModel, activity: Activity) {
    val s = vm.settings
    val dark = when (s.themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val baseScheme = if (s.accentColor == DYNAMIC_ACCENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else rememberDynamicColorScheme(
        seedColor = Color(hexColor(s.accentColor)), isDark = dark,
        style = if (s.accentColor == GRAY_ACCENT) PaletteStyle.Monochrome else PaletteStyle.Vibrant,
    )
    val scheme = if (dark && s.pureBlack) remember(baseScheme) { pureBlack(baseScheme) } else baseScheme
    val ui = UiConfig(scheme, dark, s.cornerRadius, s.fontScale, s.uiPadding, appFont(s.font), pureBlack = dark && s.pureBlack, listStyle = s.listStyle)
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = appTypography(s.font)) {
    CompositionLocalProvider(LocalUi provides ui) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val d = density.density
        val imeInsets = WindowInsets.ime
        val imeDp = { imeInsets.getBottom(density) / d }
        val statusDp = maxOf(WindowInsets.statusBars.getTop(density), WindowInsets.displayCutout.getTop(density)) / d
        val navDp = WindowInsets.navigationBars.getBottom(density) / d

        // the status bar takes the colour of what fills the top of the screen (a note's own colour, a sheet, the home
        // page); its icons are dark on a light colour and light on a dark one
        val topColor = when {
            vm.openId != null -> notePage(vm.notes[vm.openId]?.color ?: com.jlees.minotes.notes.NoteColor.NONE)
            vm.sheet != Sheet.NONE -> ui.cs.surfaceContainerLow
            else -> homeBackground()
        }
        val darkIcons = topColor.luminance() > 0.5f
        LaunchedEffect(s.showStatusBar, ui.isDark, s.rotateScreen, darkIcons) {
            val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ctl.isAppearanceLightStatusBars = darkIcons
            ctl.isAppearanceLightNavigationBars = !ui.isDark
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (s.showStatusBar) ctl.show(WindowInsetsCompat.Type.statusBars()) else ctl.hide(WindowInsetsCompat.Type.statusBars())
            activity.requestedOrientation = if (s.rotateScreen) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // ---- nothing being typed, no keyboard: leaving a note (or opening a sheet) lets go of the text and puts it away
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val rootView = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(vm.openId, vm.sheet, vm.menuFor) {
            if (vm.openId == null || vm.sheet != Sheet.NONE || vm.menuFor != null) {
                rootView.findFocus()?.clearFocus() // the note's text is an Android view: Compose's focus doesn't reach it
                focusManager.clearFocus(force = true)
                ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(rootView.windowToken, 0)
            }
        }

        // ---- the open note: it grows out of its card (or the + button), and shrinks back into it when closed
        val grow = remember { Animatable(0f) }
        vm.noteOpen = { grow.value } // the home bar and the editor's Pill read it as the note grows and shrinks
        var shownId by remember { mutableStateOf<String?>(null) }
        SideEffect { vm.shownNote = shownId } // its card hides on the home page while the note is out of it
        LaunchedEffect(vm.openId) {
            val id = vm.openId
            if (id != null) { shownId = id; grow.snapTo(0f); grow.animateTo(1f, Motion.switcher) }
            else if (shownId != null) { grow.animateTo(0f, Motion.exit(220)); shownId = null }
        }

        // ---- sheets slide in from their side, as their buttons and the Pill's swipes sit: Recently Deleted from the
        // left, Settings from the right. sheetX is the offset in screen widths (-1 left, 0 shown, 1 right).
        val sheetX = remember { Animatable(1f) }
        var sheetShown by remember { mutableStateOf(Sheet.NONE) }
        LaunchedEffect(vm.sheet) {
            // already shown: the Pill's swipe is pulling it in with the finger
            if (vm.sheet != Sheet.NONE && sheetShown != vm.sheet) { sheetShown = vm.sheet; sheetX.snapTo(sideOf(vm.sheet)); sheetX.animateTo(0f, Motion.switcher) }
        }
        fun closeSheet(speed: Float? = null) = scope.launch {
            val side = sideOf(sheetShown)
            if (speed == null) sheetX.animateTo(side, Motion.exit(200)) else sheetX.animateTo(side, Motion.switcher, initialVelocity = side * speed)
            vm.sheet = Sheet.NONE; sheetShown = Sheet.NONE; vm.settingsSearch = ""
        }
        // closed from inside (Settings › About › Gesture Walkthrough sets it to NONE): it slides away as Back would
        LaunchedEffect(vm.sheet) { if (vm.sheet == Sheet.NONE && sheetShown != Sheet.NONE) closeSheet() }
        // a swipe on the home Pill pulls a sheet in with the finger ([p]: 0..1 open), then settles it open or shut
        val dragSheet: (Sheet, Float) -> Unit = { view, p ->
            if (sheetShown != view) { sheetShown = view; vm.sheet = view }
            scope.launch { sheetX.snapTo(sideOf(view) * (1f - p)) }
        }
        val releaseSheet: (Boolean) -> Unit = { open -> if (open) scope.launch { sheetX.animateTo(0f, Motion.switcher) } else closeSheet(0f) }

        // Back closes the top layer first: the menu, a dialog (their own handlers), a sheet, then the note
        BackHandler(vm.sheet != Sheet.NONE || vm.openId != null) {
            when {
                vm.sheet != Sheet.NONE -> closeSheet()
                vm.openId != null -> vm.closeNote()
            }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(1.dp).focusable()) // focus sink: keeps Compose from handing focus to the search field
            CompositionLocalProvider(LocalPan provides s.pans(PanArea.NOTES)) { HomeScreen(vm, statusDp, navDp, imeDp, dragSheet, releaseSheet) }

            shownId?.let { id ->
                val origin = vm.cardBounds[id] ?: vm.openFrom
                val radius = with(density) { ui.radius.coerceAtMost(20).dp.toPx() } // the card's own corners
                val n = vm.notes[id]
                // what the note becomes as it lands: its card (a list row in a table is the page itself)
                val landing = when {
                    s.layout != com.jlees.minotes.data.NotesLayout.LIST -> com.jlees.minotes.ui.noteCard(n?.color ?: com.jlees.minotes.notes.NoteColor.NONE)
                    !ui.tableLists -> ui.cs.surfaceContainerHighest
                    else -> com.jlees.minotes.ui.homeBackground()
                }
                var pagePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                Box(Modifier.fillMaxSize().zIndex(50f).onSizeChanged { pagePx = it }.graphicsLayer {
                    val p = grow.value
                    val w = size.width; val h = size.height
                    val o = origin ?: Rect(w * 0.3f, h * 0.75f, w * 0.7f, h * 0.85f)
                    val s0 = (o.width / w).coerceIn(0.05f, 1f)
                    val sc = lerp(s0, 1f, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = sc; scaleY = sc
                    translationX = lerp(o.left, 0f, p); translationY = lerp(o.top, 0f, p)
                    val clipH = lerp(o.height / s0, h, p)
                    val r = lerp(radius / s0, 0f, p)
                    shape = object : Shape {
                        override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                            Outline.Generic(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, clipH, CornerRadius(r))) })
                    }
                    clip = true
                    // a lifted panel while it moves (its edges show even over a page of its own colour), flat as it lands
                    shadowElevation = 8.dp.toPx() * (p * 5f).coerceAtMost(1f) * (if (p >= 1f) 0f else 1f)
                }) {
                    // the note pans as the notes do (its bar: folder, date and words); its pop-ups as Settings does
                    CompositionLocalProvider(LocalPan provides s.pans(PanArea.NOTES)) { EditorScreen(
                        vm, id, statusDp, navDp, imeDp, onClose = { vm.closeNote() },
                        // swiping its Pill up shrinks it back into its card with the finger, then closes or springs back
                        // the note's bottom edge stays exactly under the finger: how open it must be for that, found by halving
                        onShrink = { dy -> scope.launch { grow.snapTo(opennessFor(pagePx.height + dy * density.density, origin, pagePx)) } },
                        onShrinkRelease = { close -> if (close) vm.closeNote() else scope.launch { grow.animateTo(1f, Motion.switcher) } },
                    ) }
                    // as it shrinks, its text gives way to the card's plain colour (and comes back as it grows)
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((0.7f - grow.value) / 0.4f).coerceIn(0f, 1f) }.background(landing))
                }
                // and, as it lands, a copy of its card (or row) comes in on top, exactly where the real one is: when the
                // note goes, the card under it looks the same, so it settles into its spot rather than fading out
                if (origin != null && n != null) {
                    val o = origin
                    Box(
                        Modifier.zIndex(51f)
                            .offset { androidx.compose.ui.unit.IntOffset(lerp(o.left, 0f, grow.value).toInt(), lerp(o.top, 0f, grow.value).toInt()) }
                            .size(with(density) { o.width.toDp() }, with(density) { o.height.toDp() })
                            .graphicsLayer { alpha = ((0.45f - grow.value) / 0.3f).coerceIn(0f, 1f) },
                    ) {
                        if (s.layout == com.jlees.minotes.data.NotesLayout.LIST) com.jlees.minotes.home.NoteRowView(n, s.showDates, false, false, Modifier, rowHeight(s.uiPadding, s.fontScale) + 8f)
                        else com.jlees.minotes.home.NoteCardView(
                            n, s.previewLines, s.showDates, false, false,
                            if (vm.folder == null) vm.notes.folders.firstOrNull { it.id == n.folder }?.name else null, Modifier,
                            uniform = s.layout == com.jlees.minotes.data.NotesLayout.GRID,
                        )
                    }
                }
                // the toolbar Pill turns into the home bar's as the note shrinks (and back as it grows), every frame; the
                // real Pills hide while it runs (the toolbar's below fully open, the bar's above fully closed)
                MorphingPill(from = { vm.toolPill() }, to = { vm.barPill }, m = { 1f - grow.value }, z = 55f) { ToolbarIcons() }
            }

            // a sheet's header (its title) pans as Settings does; each page inside sets its own below
            if (sheetShown != Sheet.NONE) Box(Modifier.fillMaxSize().zIndex(160f)) { CompositionLocalProvider(LocalPan provides s.pans(PanArea.SETTINGS)) {
                OverlaySheet(
                    sheetX, statusDp, imeDp, { closeSheet() },
                    // swiping its search Pill pushes it back out the way it came, with the finger
                    backTowardEnd = sideOf(sheetShown) > 0,
                    onBackDrag = { p -> scope.launch { sheetX.snapTo(sideOf(sheetShown) * p) } },
                    onBackRelease = { close, speed -> if (close) closeSheet(speed) else scope.launch { sheetX.animateTo(0f, Motion.switcher) } },
                    // its search Pill grows out of the home bar's Pill as it slides in, and back into it as it slides out
                    morphFrom = { vm.barPill },
                    morphContent = { com.jlees.minotes.home.MainBarButtons({}, {}, {}) },
                    header = { Txt(if (sheetShown == Sheet.TRASH) "Recently Deleted" else "Settings", 28f, ui.cs.onSurface, ui.extrabold, maxLines = 1) },
                ) {
                    when (sheetShown) {
                        Sheet.SETTINGS -> CompositionLocalProvider(LocalPan provides s.pans(PanArea.SETTINGS)) { SettingsView(vm) }
                        // the page pans as Settings does; its rows as Recently Deleted's switch says
                        Sheet.TRASH -> CompositionLocalProvider(LocalPan provides s.pans(PanArea.SETTINGS), LocalListPan provides s.pans(PanArea.TRASH)) { TrashView(vm) }
                        Sheet.NONE -> {}
                    }
                }
            } }

            // the menu grows from what opened it: the editor's ⋮ (top right), or the held card
            CompositionLocalProvider(LocalPan provides s.pans(PanArea.NOTE_MENU)) {
                NoteMenu(vm, fromTop = vm.openId != null, statusDp, navDp, origin = if (vm.openId != null) null else vm.menuFor?.let { vm.cardBounds[it]?.center })
            }
            // pop-ups and notices pan as Settings does (the place they belong to is wherever they opened)
            CompositionLocalProvider(LocalPan provides s.pans(PanArea.SETTINGS)) {
                Dialogs(vm)
                com.jlees.minotes.update.WhatsNewDialog(vm)
                UndoBar(Notices.current, onTimeout = { Notices.current = null }, Modifier.align(Alignment.BottomCenter).zIndex(400f).padding(bottom = (maxOf(navDp, 0f) + 92).dp).imePadding())
            }
            Walkthrough(vm)
        }
    }
    }
}


/** The editor's Pill tools, in order (their copies: the morphing Pill, the walkthrough's note). */
internal val TOOLBAR_ICONS = listOf("text_format", "format_bold", "checklist", "format_list_bulleted", "format_list_numbered", "add")

/** The editor's Pill tools as icons only, for a Pill that stands in for it. */
@Composable
internal fun ToolbarIcons() = Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { TOOLBAR_ICONS.forEach { Sym(it, 28f, LocalUi.current.cs.onSurface) } }

/**
 * One Pill turning into the home bar's ([to]) from another ([from], window px), [m] of the way (0 [from] … 1 [to]): its
 * place, size, corners, colour and shadow travel, [outgoing] fading out over the first half and the bar's buttons in
 * over the second. Everything is read while laying out and drawing, so it moves every frame of the animation driving
 * [m]; it shows only between the two ends (at either end the real Pill is there), and while both Pills are measured.
 */
@Composable
internal fun MorphingPill(from: () -> Rect?, to: () -> Rect?, m: () -> Float, z: Float, outgoing: @Composable () -> Unit) {
    val ui = LocalUi.current
    val density = LocalDensity.current
    val fromRadius = with(density) { (if (ui.radius >= 20) 35 else ui.radius * 2).dp.toPx() }
    val bar = ui.pillBar; val high = ui.cs.surfaceContainerHigh
    /**
     * Where it is now: [m] of the way between the two Pills; null while either isn't measured. [m] is read first, always:
     * it's what changes every frame, so whatever reads this (its place, its size) is redone every frame, and picks up a
     * Pill measured partway through (the toolbar's, a moment after a note starts opening). Returning before reading it
     * would leave the morph its first size (none) for good.
     */
    fun now(): Rect? {
        val t = m(); val a = from() ?: return null; val b = to() ?: return null
        return Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))
    }
    Box(
        Modifier.zIndex(z)
            .offset { now()?.let { androidx.compose.ui.unit.IntOffset(it.left.toInt(), it.top.toInt()) } ?: androidx.compose.ui.unit.IntOffset.Zero }
            .layout { measurable, _ ->
                val r = now()
                val w = (r?.width ?: 0f).toInt().coerceAtLeast(0); val h = (r?.height ?: 0f).toInt().coerceAtLeast(0)
                val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(w, h) { p.place(0, 0) }
            }
            .graphicsLayer {
                val t = m()
                alpha = if (t > 0.001f && t < 0.999f && now() != null) 1f else 0f
                shape = RoundedCornerShape(lerp(minOf(fromRadius, size.height / 2), size.height / 2, t)); clip = true
                shadowElevation = lerp(10f, 6f, t).dp.toPx()
            }
            .drawBehind { drawRect(androidx.compose.ui.graphics.lerp(bar, high, m())) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.wrapContentSize(unbounded = true).graphicsLayer { alpha = (1f - m() * 2f).coerceIn(0f, 1f) }) { outgoing() }
        Box(Modifier.wrapContentSize(unbounded = true).graphicsLayer { alpha = (m() * 2f - 1f).coerceIn(0f, 1f) }) { com.jlees.minotes.home.MainBarButtons({}, {}, {}) }
    }
}

/**
 * How open the note must be ([0..1]) for its bottom edge to sit at [bottom] (px), shrinking toward its card [origin] on a
 * page of [page] px, as the layer above lays it out. Found by halving: the bottom edge only rises as it closes.
 */
internal fun opennessFor(bottom: Float, origin: Rect?, page: androidx.compose.ui.unit.IntSize): Float {
    val w = page.width.toFloat(); val h = page.height.toFloat()
    if (w <= 0f || h <= 0f) return 1f
    val o = origin ?: Rect(w * 0.3f, h * 0.75f, w * 0.7f, h * 0.85f)
    val s0 = (o.width / w).coerceIn(0.05f, 1f)
    fun bottomAt(g: Float) = lerp(o.top, 0f, g) + lerp(o.height / s0, h, g) * lerp(s0, 1f, g)
    var lo = 0f; var hi = 1f
    repeat(24) { val mid = (lo + hi) / 2; if (bottomAt(mid) > bottom) hi = mid else lo = mid }
    return ((lo + hi) / 2).coerceIn(0f, 1f)
}

/** The side a sheet comes in from (and leaves by): -1 the left, 1 the right, matching its button on the main bar. */
private fun sideOf(sheet: Sheet) = if (sheet == Sheet.TRASH) -1f else 1f

/**
 * The OLED variant of a dark scheme: everything that fills the screen is true black; cards, menus, dialogs and the
 * Pill stay a step above it, with a hint of the accent, so they still read as surfaces.
 */
private fun pureBlack(cs: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    fun step(v: Int) = androidx.compose.ui.graphics.lerp(Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v), cs.primary, 0.04f)
    return cs.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black, surfaceContainer = step(0x12), surfaceContainerHigh = step(0x1C),
        surfaceContainerHighest = step(0x26), surfaceBright = step(0x2E), inverseOnSurface = Color.Black,
    )
}
