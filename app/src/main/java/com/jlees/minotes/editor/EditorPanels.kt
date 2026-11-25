// The panels that come up from the bottom of a note, from its toolbar Pill: the text format panel (Aa) with undo and
// redo, and the + panel (dividers, quotes, code, callouts, scanning). Both are an EditorPanel: a handle, a close
// button, then their controls, with the keyboard kept away while they're open.
package com.jlees.minotes.editor

import com.jlees.minotes.scan.rememberScanner

import android.text.Layout
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.draggable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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

/** Text colours: chosen to read on both light and dark pages. 0 is the page's own text colour. */
val TEXT_COLORS = listOf(0, 0xFFE5484D, 0xFFF76B15, 0xFFE2A300, 0xFF30A46C, 0xFF12A594, 0xFF0090FF, 0xFF8E4EC6, 0xFFD6409F, 0xFF8B8D98).map { it.toInt() }
/** Highlights (drawn see-through, see HighlightSpan). 0 is none. */
val HIGHLIGHTS = listOf(0, 0xFFFFD43B, 0xFF69DB7C, 0xFF4DABF7, 0xFFF783AC, 0xFFFFA94D, 0xFFB197FC).map { it.toInt() }

/**
 * The + panel: a dividing line, or the line you're on as a quote, a code block or a callout (again: back to plain).
 * Each exports to Markdown as its own syntax ("---", "> ", a fenced block, "> [!NOTE]"). Below them, scanning the text
 * of a photo or a picture into the note (scan/).
 */
@Composable
internal fun BoxScope.InsertPanel(editor: NoteEditText, open: Boolean, close: () -> Unit, keyboardBack: () -> Boolean, onSize: (Int) -> Unit) {
    val scanning = remember { booleanArrayOf(false) } // closed for the camera or Files: no keyboard in between
    // each leads straight on to writing: the keyboard comes back for the line after the divider, or in the block
    fun thenType(make: () -> Unit) {
        close(); make()
        editor.requestFocus()
        editor.context.getSystemService(InputMethodManager::class.java).showSoftInput(editor, 0)
    }
    // scanned text goes in at the cursor (kept while the camera or Files is open), and writing carries on after it
    val scan = rememberScanner { text -> thenType { editor.insertLines(text) } }
    EditorPanel(editor, open, close, onSize, ownsKeyboard = false, keyboardBack = { val leftToScan = scanning[0]; scanning[0] = false; !leftToScan && keyboardBack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelTile("horizontal_rule", "Divider") { thenType { editor.insertDivider() } }
                PanelTile("format_quote", "Quote") { thenType { editor.block(BlockKind.QUOTE) } }
                PanelTile("code", "Code") { thenType { editor.block(BlockKind.CODE) } }
                PanelTile("info", "Callout") { thenType { editor.block(BlockKind.CALLOUT) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelTile("photo_camera", "Scan with Camera") { scanning[0] = true; close(); scan.camera() }
                PanelTile("image", "Scan from Files") { scanning[0] = true; close(); scan.files() }
            }
        }
    }
}

/** A large button in an editor panel: a symbol over its name; [danger] in the error colour (deleting). */
@Composable
internal fun RowScope.PanelTile(icon: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier.weight(1f).height(84.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(if (danger) ui.cs.errorContainer else ui.pillField)
            .touchable(true, press, onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Sym(icon, 26f, if (danger) ui.cs.onErrorContainer else ui.cs.onSurface)
        Txt(label, 13f, if (danger) ui.cs.onErrorContainer else ui.cs.onSurface, ui.semibold, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), maxLines = 1)
    }
}

/**
 * The text format panel as a panel up from the bottom (as mi. Browser's Follow Price is): a handle to drag it away,
 * then the controls. While it's open the keyboard stays away, even as the text is tapped or selected; closing it (the
 * handle, Back, or Aa) brings the keyboard back to carry on writing. [onSize]: its height, for the text to stay above it.
 */
@Composable
fun BoxScope.FormatSheet(editor: NoteEditText, tick: Int, open: Boolean, onClose: () -> Unit, onSize: (Int) -> Unit) =
    EditorPanel(editor, open, onClose, onSize, start = { UndoRedo(editor, tick) }) { FormatPanel(editor, tick) }

/**
 * Undo and redo as buttons, in the format panel's top corner: the Pill's swipes (right to undo, left to redo) are the
 * quick way, and these are the way that needs no gesture. Faded when there's nothing to undo (or redo).
 */
@Composable
private fun UndoRedo(editor: NoteEditText, tick: Int) {
    tick // re-read whether there's anything to undo whenever the editor changes
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HistoryButton("undo", "Undo", editor.canUndo) { editor.undo() }
        HistoryButton("redo", "Redo", editor.canRedo) { editor.redo() }
    }
}

@Composable
private fun HistoryButton(icon: String, label: String, can: Boolean, onClick: () -> Unit) =
    androidx.compose.material3.IconButton(onClick = onClick, enabled = can, modifier = Modifier.size(40.dp).alpha(if (can) 1f else 0.38f)) {
        Sym(icon, 22f, LocalUi.current.cs.onSurfaceVariant, label = label)
    }

/**
 * A panel up from the bottom of the note (the format panel, the + panel): a handle to drag it away and a close button,
 * then [content]. While it's open the keyboard stays away, even as the text is tapped or selected; closing it (the
 * handle, ✕, Back) brings the keyboard back. [onSize]: its height, for the text to stay above it.
 */
@Composable
internal fun BoxScope.EditorPanel(
    editor: NoteEditText, open: Boolean, onClose: () -> Unit, onSize: (Int) -> Unit = {},
    /** False (the + panel): the keyboard goes when it opens, but tapping the text while it's open brings it up. */
    ownsKeyboard: Boolean = true,
    /** Whether closing brings the keyboard back (not when another panel is taking its place, or the camera is opening). */
    keyboardBack: () -> Boolean = { true },
    /** Buttons in the top corner opposite ✕ (the format panel's undo and redo). */
    start: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) } // 1: below the screen, 0: in place
    val drag = remember { Animatable(0f) }  // how far the handle has pulled it down (px)
    var heightPx by remember { mutableFloatStateOf(0f) }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        val imm = ctx.getSystemService(InputMethodManager::class.java)
        if (ownsKeyboard) editor.tapOpensKeyboard = !open // tapping the text while it's open moves the cursor, no keyboard
        // its height again on every opening: onSizeChanged only reports a change, and closing reported 0 (so the text
        // had no room to scroll clear of a panel opened a second time)
        if (open) { wasOpen = true; drag.snapTo(0f); imm.hideSoftInputFromWindow(editor.windowToken, 0); if (heightPx > 0f) onSize(heightPx.toInt()) }
        else if (wasOpen && keyboardBack()) { editor.holdSelection = false; editor.requestFocus(); imm.showSoftInput(editor, 0) }
        slide.animateTo(if (open) 0f else 1f, if (open) Motion.enter(300) else Motion.exit(180))
        if (!open) onSize(0)
    }
    androidx.activity.compose.BackHandler(open) { onClose() }
    if (!open && slide.value >= 1f) return
    Column(
        Modifier.align(Alignment.BottomCenter).zIndex(5f).widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth()
            .onSizeChanged { heightPx = it.height.toFloat(); if (open) onSize(it.height) }
            .graphicsLayer {
                translationY = slide.value * size.height + drag.value
                shape = RoundedCornerShape(topStart = ui.radius.dp, topEnd = ui.radius.dp); clip = true; shadowElevation = 8.dp.toPx()
            }
            .background(ui.cs.surfaceContainerLow)
            .blockTouches() // its own taps stay its own
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)), // above the keyboard while typing a size
    ) {
        // the handle: drag it down to put the panel away (it follows the finger; past a third, or flicked, it closes);
        // or close it with ✕, no gesture needed
        Box(
            Modifier.fillMaxWidth().height(56.dp).draggable(
                androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStopped = { v -> if (drag.value > heightPx / 3f || v > 1500f) onClose() else scope.launch { drag.animateTo(0f, Motion.spatial()) } },
            ),
        ) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(width = 36.dp, height = 4.dp).background(ui.cs.outlineVariant, RoundedCornerShape(2.dp)))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp, top = 8.dp)) { start() }
            // its own room: in from the panel's corner, clear of the handle and the controls below
            androidx.compose.material3.FilledTonalIconButton(onClick = onClose, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 8.dp).size(36.dp)) {
                Sym("close", 20f, ui.cs.onSecondaryContainer, label = "Close")
            }
        }
        Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) { content() }
    }
}

/**
 * The text format panel, all on one short page (no scrolling, no labels), every row filling the width. Related
 * controls are joined in groups; the icon buttons are all one width (the size box and the named styles size to fit): the paragraph style; bold, italic, underline, strikethrough, then clear; the
 * size, then colour and highlight (their swatches open below); alignment, indent and lists.
 */
@Composable
private fun FormatPanel(e: NoteEditText, tick: Int) {
    tick // re-read the editor's state whenever it changes
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val size = e.inline(Inline.SIZE); val weight = e.inline(Inline.WEIGHT)
        val style = TextStyle.of(size, weight)
        // the styles, as buttons like the rest, in a row that scrolls sideways so each has room for its name
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextStyle.entries.forEachIndexed { i, st -> TextToggle(st.label, style == st, Modifier.widthIn(min = 96.dp), i to TextStyle.entries.size) { e.style(st) } }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Group(4f) {
                val bold = (weight as? Int ?: 400) >= 600
                Toggle("format_bold", "Bold", bold, Modifier.weight(1f), seg = 0 to 4) { e.setInline(Inline.WEIGHT, if (bold) 400 else 700) }
                Toggle("format_italic", "Italic", e.inline(Inline.ITALIC) == true, Modifier.weight(1f), seg = 1 to 4) { e.setInline(Inline.ITALIC, e.inline(Inline.ITALIC) != true) }
                Toggle("format_underlined", "Underline", e.inline(Inline.UNDERLINE) == true, Modifier.weight(1f), seg = 2 to 4) { e.setInline(Inline.UNDERLINE, e.inline(Inline.UNDERLINE) != true) }
                Toggle("format_strikethrough", "Strikethrough", e.inline(Inline.STRIKE) == true, Modifier.weight(1f), seg = 3 to 4) { e.setInline(Inline.STRIKE, e.inline(Inline.STRIKE) != true) }
            }
            // not a style to turn on: drawn as an outline, so it doesn't read as one of the toggles beside it
            Toggle("format_clear", "Clear formatting", false, Modifier.weight(1f), outlined = true) { e.clearFormatting() }
        }

        var colors by remember { mutableStateOf<Inline?>(null) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SizeControl(size as? Float, Modifier.weight(3f)) { e.setInline(Inline.SIZE, it) }
            Group(2f) {
                ColorToggle("format_color_text", "Text color", e.inline(Inline.COLOR) as? Int, colors == Inline.COLOR, Modifier.weight(1f), 0 to 2) { colors = if (colors == Inline.COLOR) null else Inline.COLOR }
                ColorToggle("ink_highlighter", "Highlight", e.inline(Inline.HIGHLIGHT) as? Int, colors == Inline.HIGHLIGHT, Modifier.weight(1f), 1 to 2) { colors = if (colors == Inline.HIGHLIGHT) null else Inline.HIGHLIGHT }
            }
        }

        val para = e.listAt(); val align = e.alignment()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Group(3f) {
                Toggle("format_align_left", "Align left", align == Layout.Alignment.ALIGN_NORMAL, Modifier.weight(1f), seg = 0 to 3) { e.align(Layout.Alignment.ALIGN_NORMAL) }
                Toggle("format_align_center", "Align center", align == Layout.Alignment.ALIGN_CENTER, Modifier.weight(1f), seg = 1 to 3) { e.align(Layout.Alignment.ALIGN_CENTER) }
                Toggle("format_align_right", "Align right", align == Layout.Alignment.ALIGN_OPPOSITE, Modifier.weight(1f), seg = 2 to 3) { e.align(Layout.Alignment.ALIGN_OPPOSITE) }
            }
            Group(2f) {
                Toggle("format_indent_decrease", "Decrease indent", false, Modifier.weight(1f), enabled = (para?.level ?: 0) > 0, seg = 0 to 2) { e.indentBy(-1) }
                Toggle("format_indent_increase", "Increase indent", false, Modifier.weight(1f), enabled = (para?.level ?: 0) < MAX_LEVEL, seg = 1 to 2) { e.indentBy(1) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Group(1f) {
                Toggle("format_list_bulleted", "Bulleted list", para?.kind == ListKind.BULLET, Modifier.weight(1f), seg = 0 to 3) { e.list(ListKind.BULLET) }
                Toggle("format_list_numbered", "Numbered list", para?.kind == ListKind.NUMBER, Modifier.weight(1f), seg = 1 to 3) { e.list(ListKind.NUMBER) }
                Toggle("checklist", "Checklist", para?.kind == ListKind.CHECK, Modifier.weight(1f), seg = 2 to 3) { e.list(ListKind.CHECK) }
            }
        }
        AnimatedVisibility(
            colors != null,
            enter = expandVertically(Motion.noBounce()) + fadeIn(Motion.enter(200)),
            exit = shrinkVertically(Motion.exit(150)) + fadeOut(Motion.exit(100)),
        ) {
            val which = colors ?: Inline.COLOR
            if (which == Inline.COLOR) Swatches(TEXT_COLORS, e.inline(Inline.COLOR) as? Int, none = "format_color_text") { e.setInline(Inline.COLOR, it) }
            else Swatches(HIGHLIGHTS, e.inline(Inline.HIGHLIGHT) as? Int, none = "ink_highlighter", translucent = true) { e.setInline(Inline.HIGHLIGHT, it) }
        }
    }
}

/** The panel's buttons are all this tall (the touch target too). */
private val BUTTON_H = 48.dp

/** Buttons that belong together, joined: 2dp apart, rounded only at the group's ends. [weight]: its share of the row. */
@Composable
private fun RowScope.Group(weight: Float, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.weight(weight), horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)

/** A group taking a whole row (the paragraph styles). */
@Composable
private fun ColumnScope.Group(@Suppress("UNUSED_PARAMETER") whole: Float, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)

/** The [i]th of [n] buttons in a group: its outer corners round, the inner ones tight; one that's on rounds out fully. */
private fun segShape(i: Int, n: Int, on: Boolean): RoundedCornerShape {
    if (on) return RoundedCornerShape(24.dp)
    val first = if (i == 0) 14.dp else 4.dp; val last = if (i == n - 1) 14.dp else 4.dp
    return RoundedCornerShape(topStart = first, bottomStart = first, topEnd = last, bottomEnd = last)
}

/** One of the panel's buttons: its fill (the accent's container when on), shape and press, around [content]. */
@Composable
private fun PanelButton(on: Boolean, modifier: Modifier, seg: Pair<Int, Int>?, enabled: Boolean = true, outlined: Boolean = false, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else if (outlined) Color.Transparent else ui.pillField, label = "button")
    val shape = seg?.let { segShape(it.first, it.second, on) } ?: RoundedCornerShape(if (on) 24.dp else 14.dp)
    Box(
        modifier.height(BUTTON_H).pressScale(press).clip(shape).background(bg)
            .then(if (outlined) Modifier.border(1.5.dp, ui.cs.outline, shape) else Modifier)
            .alpha(if (enabled) 1f else 0.38f).touchable(enabled, press, onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable
private fun Toggle(icon: String, label: String, on: Boolean, modifier: Modifier, enabled: Boolean = true, outlined: Boolean = false, seg: Pair<Int, Int>? = null, onClick: () -> Unit) {
    val ui = LocalUi.current
    PanelButton(on, modifier, seg, enabled, outlined, onClick) {
        Sym(icon, 24f, if (on) ui.cs.onPrimaryContainer else if (outlined) ui.cs.onSurfaceVariant else ui.cs.onSurface, label = label)
    }
}

/** A paragraph style's button: its name, in the same button as the panel's icons. */
@Composable
private fun TextToggle(label: String, on: Boolean, modifier: Modifier, seg: Pair<Int, Int>, onClick: () -> Unit) {
    val ui = LocalUi.current
    PanelButton(on, modifier, seg, onClick = onClick) {
        Txt(label, 14f, if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface, ui.semibold, Modifier.padding(horizontal = 18.dp), maxLines = 1, scaled = false)
    }
}

/** A colour's button: its icon over a bar of the colour in use; [open] while its swatches show. */
@Composable
private fun ColorToggle(icon: String, label: String, value: Int?, open: Boolean, modifier: Modifier, seg: Pair<Int, Int>, onClick: () -> Unit) {
    val ui = LocalUi.current
    PanelButton(open, modifier, seg, onClick = onClick) {
        Sym(icon, 22f, if (open) ui.cs.onPrimaryContainer else ui.cs.onSurface, Modifier.offset(y = (-3).dp), label = label)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(width = 20.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
            .background(if (value == null || value == 0) ui.cs.outline else Color(value)))
    }
}

/** The text's exact size in points (the page's own text is 17): − and + step by one, or type a size from 9 to 68. */
@Composable
private fun SizeControl(scale: Float?, modifier: Modifier, onSet: (Float) -> Unit) {
    val ui = LocalUi.current
    val pt = scale?.let { Math.round(it * BASE_PT) }
    var typed by remember(pt) { mutableStateOf(pt?.toString() ?: "") }
    fun set(p: Int) { val v = p.coerceIn(MIN_PT, MAX_PT); typed = v.toString(); onSet(v / BASE_PT.toFloat()) }
    val now = pt ?: BASE_PT
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // − and + step through the usual sizes (one at a time while small, then the common ones)
        Toggle("remove", "Smaller", false, Modifier.weight(0.85f), enabled = now > MIN_PT, seg = 0 to 3) { set(prevSize(now)) }
        // hold the number to go back to the page's own size
        Box(
            Modifier.weight(1.3f).height(BUTTON_H).clip(segShape(1, 3, false)).background(ui.pillField)
                .pillLongPress(true) { set(BASE_PT) },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                typed, { t -> typed = t.filter(Char::isDigit).take(2); typed.toIntOrNull()?.takeIf { it in MIN_PT..MAX_PT }?.let { onSet(it / BASE_PT.toFloat()) } },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { typed.toIntOrNull()?.let(::set) ?: run { typed = pt?.toString() ?: "" } }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ui.cs.primary),
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if (typed.isEmpty()) Txt("–", 17f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false); inner() } },
            )
        }
        Toggle("add", "Larger", false, Modifier.weight(0.85f), enabled = now < MAX_PT, seg = 2 to 3) { set(nextSize(now)) }
    }
}

private const val BASE_PT = 17 // the page's own text size, which a size of 1 is
private const val MIN_PT = 9   // SizeSpan's 0.5 floor, rounded up
private const val MAX_PT = 68  // and its 4× ceiling

/** The sizes − and + step through: every size while small, then the common ones. */
internal val COMMON_SIZES = (MIN_PT..18).toList() + listOf(20, 22, 24, 28, 32, 36, 40, 48, 56, 64, MAX_PT)
/** The next common size above [pt], and the one below. */
internal fun nextSize(pt: Int) = COMMON_SIZES.firstOrNull { it > pt } ?: MAX_PT
internal fun prevSize(pt: Int) = COMMON_SIZES.lastOrNull { it < pt } ?: MIN_PT

/** A row of colour swatches; the first (0) is "none", drawn as [none]'s icon. */
@Composable
private fun Swatches(colors: List<Int>, value: Int?, none: String, translucent: Boolean = false, onPick: (Int) -> Unit) {
    val ui = LocalUi.current
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { c ->
            val on = value == c
            Box(
                Modifier.size(34.dp).clip(if (on) RoundedCornerShape(30) else CircleShape)
                    .background(if (c == 0) ui.pillField else Color(c).copy(alpha = if (translucent) 0.6f else 1f))
                    .then(if (on) Modifier.border(2.dp, ui.cs.onSurface, RoundedCornerShape(30)) else Modifier)
                    .touchable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == 0) Sym(none, 20f, ui.cs.onSurfaceVariant, label = "None")
                else if (on) Sym("check", 20f, Color.White)
            }
        }
    }
}
