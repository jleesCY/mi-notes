// The editor's Pill: the tools you reach for most (text format, checklist, list, picture, undo, redo) in one row, and
// Aa opening the full text format panel above them, inside the Pill. It rides on the keyboard; the panel takes the
// keyboard's place while it's open. Swiping the Pill sideways goes back to your notes.
package com.jlees.minotes.editor

import android.text.Layout
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.notes.TextFont
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Text colours: chosen to read on both light and dark pages. 0 is the page's own text colour. */
val TEXT_COLORS = listOf(0, 0xFFE5484D, 0xFFF76B15, 0xFFE2A300, 0xFF30A46C, 0xFF12A594, 0xFF0090FF, 0xFF8E4EC6, 0xFFD6409F, 0xFF8B8D98).map { it.toInt() }
/** Highlights (drawn see-through, see HighlightSpan). 0 is none. */
val HIGHLIGHTS = listOf(0, 0xFFFFD43B, 0xFF69DB7C, 0xFF4DABF7, 0xFFF783AC, 0xFFFFA94D, 0xFFB197FC).map { it.toInt() }
private val SIZES = listOf(0.85f to "S", 1f to "M", 1.25f to "L", 1.5f to "XL", 2f to "2XL")
private val WEIGHT_NAMES = mapOf(100 to "Thin", 200 to "Extra Light", 300 to "Light", 400 to "Regular", 500 to "Medium", 600 to "Semibold", 700 to "Bold", 800 to "Extra Bold", 900 to "Black", 1000 to "Heavy")

@Composable
fun BoxScope.FormatPill(
    editor: NoteEditText, tick: Int, panel: Boolean, setPanel: (Boolean) -> Unit, navDp: Float, imeDp: () -> Float,
    onSize: (Int) -> Unit, onBack: () -> Unit,
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val dx = remember { Animatable(0f) }
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { NoteImages.import(ctx, uri) }
            if (name == null) Notices.show("Couldn't add the picture") else editor.insertImage(name)
        }
    }
    fun hideKeyboard() = ctx.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
    tick // re-read the editor's state whenever it changes

    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().paddingDp(bottom = { maxOf(imeDp(), navDp) })
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp).onSizeChanged { onSize(it.height) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = PILL_MAX_WIDTH.dp).fillMaxWidth()
                .graphicsLayer {
                    translationX = dx.value.dp.toPx()
                    val r = if (ui.radius >= 20) 35.dp.toPx() else (ui.radius * 2).dp.toPx()
                    shape = RoundedCornerShape(minOf(r, size.height / 2)); clip = true; shadowElevation = 10.dp.toPx()
                }
                .background(ui.pillBar)
                // swipe it sideways: back to your notes (it follows the finger, and ticks where letting go goes back)
                .panGesture(
                    enabled = !panel,
                    onMove = { x, y ->
                        if (kotlin.math.abs(x) < kotlin.math.abs(y)) return@panGesture
                        val was = kotlin.math.abs(dx.value) > screenW * 0.1f
                        scope.launch { dx.snapTo(x * 0.5f) }
                        if ((kotlin.math.abs(x * 0.5f) > screenW * 0.1f) != was) haptics?.tick()
                    },
                    onRelease = { x, _, vx, _ ->
                        if (kotlin.math.abs(x) > screenW * 0.2f || (kotlin.math.abs(vx) > 0.7f && kotlin.math.abs(x) > 24f)) onBack()
                        scope.launch { dx.animateTo(0f, Motion.springBack()) }
                    },
                )
                .padding(horizontal = 10.dp),
        ) {
            AnimatedVisibility(
                panel,
                enter = expandVertically(Motion.enter(250), expandFrom = Alignment.Bottom) + fadeIn(Motion.enter(200)),
                exit = shrinkVertically(Motion.exit(150), shrinkTowards = Alignment.Bottom) + fadeOut(Motion.exit(100)),
            ) { FormatPanel(editor, tick) }
            Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool("text_format", "Text format", on = panel) {
                    if (panel) { setPanel(false); editor.requestFocus(); ctx.getSystemService(InputMethodManager::class.java).showSoftInput(editor, 0) }
                    else { hideKeyboard(); setPanel(true) }
                }
                val para = editor.listAt()
                Tool("checklist", "Checklist", on = para?.kind == ListKind.CHECK) { editor.list(ListKind.CHECK) }
                Tool("format_list_bulleted", "Bulleted list", on = para?.kind == ListKind.BULLET) { editor.list(ListKind.BULLET) }
                Tool("add_photo_alternate", "Add picture") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                Tool("undo", "Undo", enabled = editor.canUndo) { editor.undo() }
                Tool("redo", "Redo", enabled = editor.canRedo) { editor.redo() }
            }
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
        Modifier.size(48.dp).pressScale(press).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape).background(bg)
            .alpha(if (enabled) 1f else 0.38f).touchable(enabled, press, onClick),
        contentAlignment = Alignment.Center,
    ) { Sym(icon, 24f, if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface, label = label) }
}

/** The full text format panel: style, the four classic toggles, weight, size, font, colour, highlight, paragraph. */
@Composable
private fun FormatPanel(e: NoteEditText, tick: Int) {
    tick // re-read the editor's state whenever it changes
    Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)) {
        val size = e.inline(Inline.SIZE); val weight = e.inline(Inline.WEIGHT)
        ConnectedChoice(TextStyle.entries.map { it.name to it.label }, TextStyle.of(size, weight)?.name ?: "") { e.style(TextStyle.valueOf(it)) }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val bold = (weight as? Int ?: 400) >= 600
            Toggle("format_bold", "Bold", bold) { e.setInline(Inline.WEIGHT, if (bold) 400 else 700) }
            Toggle("format_italic", "Italic", e.inline(Inline.ITALIC) == true) { e.setInline(Inline.ITALIC, e.inline(Inline.ITALIC) != true) }
            Toggle("format_underlined", "Underline", e.inline(Inline.UNDERLINE) == true) { e.setInline(Inline.UNDERLINE, e.inline(Inline.UNDERLINE) != true) }
            Toggle("format_strikethrough", "Strikethrough", e.inline(Inline.STRIKE) == true) { e.setInline(Inline.STRIKE, e.inline(Inline.STRIKE) != true) }
            Toggle("format_clear", "Clear formatting", false) { e.clearFormatting() }
        }

        Label("Weight", (weight as? Int)?.let { w -> WEIGHT_NAMES[(w / 100 * 100).coerceIn(100, 1000)] } ?: "Mixed")
        var w by remember(weight) { mutableFloatStateOf(((weight as? Int) ?: 400).toFloat()) }
        Slider(w, { w = it }, valueRange = 100f..900f, steps = 7, onValueChangeFinished = { e.setInline(Inline.WEIGHT, Math.round(w / 100f) * 100) })

        Label("Size", null)
        ConnectedChoice(SIZES.map { it.first.toString() to it.second }, (size as? Float)?.toString() ?: "") { e.setInline(Inline.SIZE, it.toFloat()) }

        Label("Font", null)
        ConnectedChoice(TextFont.entries.map { it.name to it.label }, (e.inline(Inline.FONT) as? TextFont)?.name ?: "") { e.setInline(Inline.FONT, TextFont.valueOf(it)) }

        Label("Color", null)
        Swatches(TEXT_COLORS, e.inline(Inline.COLOR) as? Int, none = "format_color_text") { e.setInline(Inline.COLOR, it) }

        Label("Highlight", null)
        Swatches(HIGHLIGHTS, e.inline(Inline.HIGHLIGHT) as? Int, none = "ink_highlighter", translucent = true) { e.setInline(Inline.HIGHLIGHT, it) }

        Label("Paragraph", null)
        val para = e.listAt(); val align = e.alignment()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Toggle("format_align_left", "Align left", align == Layout.Alignment.ALIGN_NORMAL) { e.align(Layout.Alignment.ALIGN_NORMAL) }
            Toggle("format_align_center", "Align center", align == Layout.Alignment.ALIGN_CENTER) { e.align(Layout.Alignment.ALIGN_CENTER) }
            Toggle("format_align_right", "Align right", align == Layout.Alignment.ALIGN_OPPOSITE) { e.align(Layout.Alignment.ALIGN_OPPOSITE) }
            Toggle("format_indent_decrease", "Decrease indent", false, enabled = (para?.level ?: 0) > 0) { e.indentBy(-1) }
            Toggle("format_indent_increase", "Increase indent", false, enabled = (para?.level ?: 0) < MAX_LEVEL) { e.indentBy(1) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Toggle("format_list_bulleted", "Bulleted list", para?.kind == ListKind.BULLET) { e.list(ListKind.BULLET) }
            Toggle("format_list_numbered", "Numbered list", para?.kind == ListKind.NUMBER) { e.list(ListKind.NUMBER) }
            Toggle("checklist", "Checklist", para?.kind == ListKind.CHECK) { e.list(ListKind.CHECK) }
            Spacer(Modifier.size(48.dp)); Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Label(text: String, value: String?) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)) {
        Txt(text, 12f, ui.cs.onSurfaceVariant, ui.bold, Modifier.weight(1f))
        if (value != null) Txt(value, 12f, ui.cs.onSurfaceVariant, ui.semibold)
    }
}

@Composable
private fun Toggle(icon: String, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else ui.pillField, label = "toggle")
    Box(
        Modifier.size(52.dp).pressScale(press).clip(RoundedCornerShape(if (on) 26.dp else 16.dp)).background(bg).alpha(if (enabled) 1f else 0.38f).touchable(enabled, press, onClick),
        contentAlignment = Alignment.Center,
    ) { Sym(icon, 24f, if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface, label = label) }
}

/** A row of colour swatches; the first (0) is "none", drawn as [none]'s icon. */
@Composable
private fun Swatches(colors: List<Int>, value: Int?, none: String, translucent: Boolean = false, onPick: (Int) -> Unit) {
    val ui = LocalUi.current
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { c ->
            val on = value == c
            Box(
                Modifier.size(40.dp).clip(if (on) RoundedCornerShape(30) else CircleShape)
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
