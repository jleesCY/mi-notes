// The editor's Pill: the tools you reach for most (Aa, bold, checklist, bullets, numbers, +) in one row. Aa and +
// open their panels up from the bottom (EditorPanels.kt). Swipe the Pill right to undo, left to redo (also buttons in
// the Aa panel, and actions for screen readers), up to go back to your notes.
package com.jlees.minotes.editor

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onGloballyPositioned

import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.launch


@Composable
fun BoxScope.FormatPill(
    editor: NoteEditText, tick: Int, panel: Boolean, setPanel: (Boolean) -> Unit, navDp: Float, imeDp: () -> Float,
    onSize: (Int) -> Unit,
    /** The + panel's height while it's open (0 closed), for the text to scroll clear of it. */
    onInsertSize: (Int) -> Unit = {},
    /** A swipe up: how far the finger has gone up (dp, negative), for the note to shrink with it exactly. */
    onShrink: (dy: Float) -> Unit = {}, onShrinkRelease: (close: Boolean) -> Unit = {},
    /** The note isn't fully open: the Pill hides (a copy above the note turns into the home bar's Pill). */
    hidden: () -> Boolean = { false }, onPlace: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {},
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val dx = remember { Animatable(0f) }
    var swipeArmed by remember { mutableStateOf(false) }
    var swipeUp by remember { mutableStateOf<Boolean?>(null) } // which way this swipe goes, once it's clear
    var shrink by remember { mutableFloatStateOf(0f) }          // how far a swipe up has shrunk the note (0..1)
    var insert by remember { mutableStateOf(false) } // the + panel is open
    // closing it brings the keyboard back, unless it closed for the format panel (which keeps the keyboard away)
    val panelNow by rememberUpdatedState(panel)
    InsertPanel(editor, insert, close = { insert = false }, keyboardBack = { !panelNow }, onSize = onInsertSize)
    fun hideKeyboard() = ctx.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
    // the panel takes the keyboard's place: a keyboard that shows up late (after a long press, say) goes away again.
    // Tapping the text closes the panel first (NoteEditText.onTouchText), so that keyboard stays.
    @OptIn(ExperimentalLayoutApi::class) val keyboardUp = WindowInsets.isImeVisible
    // the panel keeps the keyboard away from the note; a keyboard for something else (its size box) is left be
    LaunchedEffect(keyboardUp, panel) { if (panel && keyboardUp && editor.hasFocus()) hideKeyboard() }
    // the keyboard put away (its hide key, Back): the text lets go too, so no cursor is left blinking. Not when a panel
    // took its place: the panel keeps the selection to act on
    val keyboardWasUp = remember { booleanArrayOf(false) }
    LaunchedEffect(keyboardUp) {
        if (keyboardWasUp[0] && !keyboardUp && !panel && !insert) editor.clearFocus()
        keyboardWasUp[0] = keyboardUp
    }
    // the swipe below outlives this composition: it reads whether the keyboard is up now, not when the note opened
    // (a note opened while typing could otherwise never be swiped up again)
    val keyboardNow by rememberUpdatedState(keyboardUp)
    tick // re-read the editor's state whenever it changes
    // typing: the Pill docks on the keyboard, edge to edge, its bottom corners square and its shadow gone. It follows
    // where the keyboard is going, not whether it's still on screen: going down, the Pill turns back as it starts to,
    // instead of riding it down docked and only turning back once it's gone
    @OptIn(ExperimentalLayoutApi::class)
    val keyboardComing = WindowInsets.imeAnimationTarget.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val dock by androidx.compose.animation.core.animateFloatAsState(if (keyboardComing) 1f else 0f, if (keyboardComing) Motion.enter(200) else Motion.exit(150), label = "dock")

    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().paddingDp(bottom = { maxOf(imeDp(), navDp) })
            .paddingDp(start = { 10f * (1f - dock) }, end = { 10f * (1f - dock) }, bottom = { 10f * (1f - dock) }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            // only the Pill's own height: the editor already ends above the keyboard
            Modifier.widthIn(max = if (dock > 0f) androidx.compose.ui.unit.Dp.Infinity else PILL_MAX_WIDTH.dp).fillMaxWidth().onSizeChanged { onSize(it.height) }
                .onGloballyPositioned { onPlace(it) }
                .graphicsLayer {
                    alpha = if (hidden()) 0f else 1f
                    val r = minOf(if (ui.radius >= 20) 35.dp.toPx() else (ui.radius * 2).dp.toPx(), size.height / 2)
                    val b = r * (1f - dock)
                    shape = RoundedCornerShape(topStart = r, topEnd = r, bottomStart = b, bottomEnd = b); clip = true
                    shadowElevation = 10.dp.toPx() * (1f - dock)
                }
                .background(ui.pillBar)
                // swipe it right to undo, left to redo: the Pill stays put while its tools give way to the undo (or
                // redo) arrow, ticking where letting go acts, and only when there's something to undo (or redo). Swipe
                // it up, keyboard down: the note shrinks back into its card under the finger (as a browser page shrinks
                // into the tab switcher), ticking where letting go closes it. While typing, a swipe up does nothing:
                // the keyboard is for writing. The direction is fixed once the finger passes the touch slop.
                .panGesture(
                    enabled = !panel,
                    onStart = { swipeUp = null; shrink = 0f },
                    onMove = { x, y ->
                        if (swipeUp == null) swipeUp = kotlin.math.abs(y) > kotlin.math.abs(x) && y < 0
                        if (swipeUp == true) {
                            if (keyboardNow) return@panGesture
                            val p = (-y / SHRINK_DRAG).coerceIn(0f, 1f)
                            if ((p > SHRINK_AT) != (shrink > SHRINK_AT)) haptics?.tick()
                            shrink = p
                            onShrink(y)
                            return@panGesture
                        }
                        if (kotlin.math.abs(x) < kotlin.math.abs(y)) return@panGesture
                        scope.launch { dx.snapTo(x) }
                        val armed = (if (x > 0) editor.canUndo else editor.canRedo) && kotlin.math.abs(x) > PILL_SWIPE_ARM
                        if (armed != swipeArmed) { swipeArmed = armed; haptics?.tick() }
                    },
                    onRelease = { x, y, vx, vy ->
                        if (swipeUp == true) {
                            // where it let go (not the last move seen); a flick up closes it too, a flick down keeps it
                            val p = (-y / SHRINK_DRAG).coerceIn(0f, 1f)
                            if (shrink > 0f || !keyboardNow) onShrinkRelease(vy < -0.2f || (vy < 0.2f && p > SHRINK_AT))
                        } else {
                            swipeArmed = false
                            scope.launch { dx.animateTo(0f, Motion.spatial()) }
                            if (kotlin.math.abs(x) > kotlin.math.abs(y) && pillSwipeActs(x, vx)) {
                                if (x > 0 && editor.canUndo) editor.undo() else if (x < 0 && editor.canRedo) editor.redo()
                            }
                        }
                        swipeUp = null
                    },
                )
                // screen readers can't swipe: the Pill's swipes, as actions on it
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Undo") { editor.canUndo.also { if (it) editor.undo() } },
                        CustomAccessibilityAction("Redo") { editor.canRedo.also { if (it) editor.redo() } },
                    )
                }
                .padding(horizontal = 10.dp),
        ) {
            Box { Row(Modifier.fillMaxWidth().height(70.dp).graphicsLayer { alpha = pillSwipeContentAlpha(dx.value, editor.canUndo, editor.canRedo) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool("text_format", "Text format", on = panel) {
                    // opening: the selection is kept for the panel's controls, and the keyboard goes until it closes
                    if (panel) setPanel(false) else { insert = false; editor.holdSelection = true; editor.requestFocus(); editor.hideSelectionMenu(); hideKeyboard(); setPanel(true) }
                }
                val para = editor.listAt()
                // undo and redo are the Pill's sideways swipes, so their place here goes to bold and numbered lists
                val bold = (editor.inline(Inline.WEIGHT) as? Int ?: 400) >= 600
                Tool("format_bold", "Bold", on = bold) { editor.setInline(Inline.WEIGHT, if (bold) 400 else 700) }
                Tool("checklist", "Checklist", on = para?.kind == ListKind.CHECK) { editor.list(ListKind.CHECK) }
                Tool("format_list_bulleted", "Bulleted list", on = para?.kind == ListKind.BULLET) { editor.list(ListKind.BULLET) }
                Tool("format_list_numbered", "Numbered list", on = para?.kind == ListKind.NUMBER) { editor.list(ListKind.NUMBER) }
                Tool("add", "Insert") { setPanel(false); insert = true }
            }
            PillSwipeArrows({ dx.value }, right = if (editor.canUndo) "undo" else null, left = if (editor.canRedo) "redo" else null, Modifier.matchParentSize()) }
        }
    }
}

/** A round tool in the Pill: filled with the accent while [on]. */
@Composable
private fun Tool(icon: String, label: String, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else Color.Transparent, label = "tool")
    val pop = popScale(on)
    Box(
        Modifier.size(52.dp).pressScale(press).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape).background(bg)
            .alpha(if (enabled) 1f else 0.38f).touchable(enabled, press, onClick),
        contentAlignment = Alignment.Center,
    ) { Sym(icon, 28f, if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface, label = label) }
}

/** dp of upward drag that shrinks the note all the way into its card (the browser's page into the tab switcher). */
private const val SHRINK_DRAG = 220f
/** How far (0..1) a swipe up has to go for letting go to close the note. */
private const val SHRINK_AT = 0.12f

// ---- Swiping a Pill sideways (mi. Browser's back/forward). The Pill itself never moves: as the finger goes, its
// contents fade out and an arrow fades in on the side it's heading from (in by 32dp), it ticks once at 28dp where
// letting go acts (and back if the finger returns), and the drag value settles on Motion.spatial(), no bounce.

/** How far (dp) a Pill swipe has to go before letting go acts. */
private const val PILL_SWIPE_ARM = 28f

/** Whether a Pill swipe let go at [dx] (dp) with speed [vx] (dp/ms) acts: past 28dp, or 8dp with a flick. */
internal fun pillSwipeActs(dx: Float, vx: Float) = kotlin.math.abs(dx) > PILL_SWIPE_ARM || (kotlin.math.abs(dx) > 8f && kotlin.math.abs(vx) > 0.25f)

/** The Pill's contents' opacity while dragged [d] dp sideways: gone by 32dp, where there's somewhere to go that way. */
internal fun pillSwipeContentAlpha(d: Float, canRight: Boolean, canLeft: Boolean): Float =
    if ((d > 0f && canRight) || (d < 0f && canLeft)) 1f - (kotlin.math.abs(d) / 32f).coerceIn(0f, 1f) else 1f

/** The arrows a Pill swipe shows, over the Pill ([drag] read at draw time): [right] for a swipe right, at the start. */
@androidx.compose.runtime.Composable
private fun PillSwipeArrows(drag: () -> Float, right: String?, left: String?, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    androidx.compose.foundation.layout.Box(modifier) {
        if (right != null) androidx.compose.foundation.layout.Box(
            Modifier.align(androidx.compose.ui.Alignment.CenterStart).padding(start = 20.dp).graphicsLayer { alpha = (drag() / 32f).coerceIn(0f, 1f) },
        ) { Sym(right, 28f, ui.cs.onSurface) }
        if (left != null) androidx.compose.foundation.layout.Box(
            Modifier.align(androidx.compose.ui.Alignment.CenterEnd).padding(end = 20.dp).graphicsLayer { alpha = (-drag() / 32f).coerceIn(0f, 1f) },
        ) { Sym(left, 28f, ui.cs.onSurface) }
    }
}
