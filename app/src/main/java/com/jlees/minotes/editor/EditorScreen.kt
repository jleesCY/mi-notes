// A note open for writing: back and ⋮ at the top, the title, then the body, in the note's colour. The format Pill at
// the bottom rides on the keyboard.
package com.jlees.minotes.editor

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.R
import com.jlees.minotes.data.AppFont
import com.jlees.minotes.home.shortDate
import com.jlees.minotes.notes.wordCount
import com.jlees.minotes.ui.notePage
import com.jlees.minotes.ui.common.*
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.data.pans
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    vm: NotesViewModel, id: String, statusDp: Float, navDp: Float, imeDp: () -> Float, onClose: () -> Unit,
    onShrink: (dy: Float) -> Unit = {}, onShrinkRelease: (Boolean) -> Unit = {},
) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val note = vm.notes[id] ?: return
    val s = vm.settings
    var title by remember(id) { mutableStateOf(note.title) }
    // renamed from outside the title box (⋮ › Rename while the note is open): the box shows the new name, and the next
    // edit saves it rather than writing the old one back. (Typing in the box updates the note too, so this is a no-op then.)
    if (note.title != title) title = note.title
    var tick by remember { mutableIntStateOf(0) } // the editor's state changed: the format controls re-read it
    var panel by remember { mutableStateOf(false) }
    var pillPx by remember { mutableIntStateOf(0) }
    var sheetPx by remember { mutableIntStateOf(0) } // the format panel's height while it's open (0 closed)
    val editor = remember(id) {
        NoteEditText(ctx).apply {
            EditorFonts.sans = ResourcesCompat.getFont(ctx, R.font.google_sans_flex) ?: android.graphics.Typeface.DEFAULT
            load(note.body)
        }
    }
    // the theme and settings, into the Android view
    val ink = ui.cs.onSurface.toArgb()
    LaunchedEffect(ink, ui.cs.primary, s.editorTextScale, s.font, s.checkedToBottom) {
        val d = density.density
        ParaSpan.Look.apply { indent = (24 * d).toInt(); marker = (30 * d).toInt(); this.ink = ui.cs.onSurfaceVariant.toArgb(); accent = ui.cs.primary.toArgb(); onAccent = ui.cs.onPrimary.toArgb(); this.density = d }
        editor.apply {
            typeface = if (s.font == AppFont.SYSTEM) android.graphics.Typeface.DEFAULT else EditorFonts.sans
            if (s.font == AppFont.ROUNDED) fontVariationSettings = "'ROND' 100"
            setTextColor(ink); setHintTextColor(ui.cs.onSurfaceVariant.copy(alpha = 0.7f).toArgb())
            highlightColor = ui.cs.primary.copy(alpha = 0.3f).toArgb()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply { setColor(ui.cs.primary.toArgb()); setSize((2 * d).toInt(), 1) }
                // the cursor's and selection's drag handles in the accent too (Android draws them in its own colour)
                val accent = ui.cs.primary.toArgb()
                setTextSelectHandle(textSelectHandle!!.mutate().apply { setTint(accent) })
                setTextSelectHandleLeft(textSelectHandleLeft!!.mutate().apply { setTint(accent) })
                setTextSelectHandleRight(textSelectHandleRight!!.mutate().apply { setTint(accent) })
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * s.editorTextScale)
            setLineSpacing(5 * d, 1f)
            fadedInk = ui.cs.onSurfaceVariant.copy(alpha = 0.6f).toArgb()
            checkedToBottom = s.checkedToBottom
            invalidate()
        }
    }
    DisposableEffect(editor) {
        // every change re-reads the editor (the "Start writing" line goes the moment you type, not when it's saved)
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) { tick++ }
        }
        editor.addTextChangedListener(watcher)
        editor.onChange = { body -> vm.notes.edit(id, title, body) }
        vm.flushEditor = editor::flush
        editor.onState = { tick++ }
        editor.onTouchText = {} // the format panel stays open while you tap and select text; it closes when you close it
        editor.haptics = haptics
        onDispose { vm.flushEditor = {}; editor.onChange = {}; editor.onState = {}; editor.onTouchText = {}; editor.removeTextChangedListener(watcher) }
    }
    // a new note: straight to the keyboard
    LaunchedEffect(id) {
        if (note.isEmpty) {
            kotlinx.coroutines.delay(300) // once the editor has grown into place
            // asked outright (an "implicit" request is one Android may ignore: now and then a new note had no keyboard),
            // and once more if it isn't up shortly after (the first ask can come before the window is ready for it)
            repeat(2) {
                editor.requestFocus()
                @Suppress("DEPRECATION")
                androidx.core.view.ViewCompat.getWindowInsetsController(editor)?.show(androidx.core.view.WindowInsetsCompat.Type.ime())
                    ?: ctx.getSystemService(InputMethodManager::class.java).showSoftInput(editor, 0)
                kotlinx.coroutines.delay(500)
                if (androidx.core.view.ViewCompat.getRootWindowInsets(editor)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true) return@LaunchedEffect
            }
        }
    }

    val current = vm.notes[id] ?: note
    var pageCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { pageCoords = it }.background(notePage(current.color))) {
        Column(Modifier.fillMaxSize().padding(top = statusDp.dp).paddingDp(bottom = imeDp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onClose) { Sym("arrow_back", 24f, ui.cs.onSecondaryContainer, label = "Back") }
                    // where the note is and how long it is, in the bar, so the title sits right on its text; the folder
                    // taps to move it
                    val words = remember(current.body) { wordCount(current.body.text) }
                    Row(
                        Modifier.weight(1f).padding(start = 8.dp).clip(RoundedCornerShape(12.dp))
                            .touchable { vm.ask = com.jlees.minotes.Ask.MoveNote(id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // one line: the folder (it gives way first when space runs out), then the time and the length
                        Txt(vm.notes.folders.firstOrNull { it.id == current.folder }?.name ?: "No Folder", 13f, ui.cs.onSurface, ui.semibold,
                            Modifier.weight(1f, fill = false), maxLines = 1)
                        Txt("  ·  ${shortDate(current.edited)}  ·  ${if (words == 1) "1 word" else "$words words"}", 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                    }
                    if (current.pinned) Sym("keep", 20f, ui.cs.primary, Modifier.padding(end = 4.dp))
                    androidx.compose.material3.IconButton(onClick = { vm.menuFor = id }, colors = IconButtonDefaults.iconButtonColors()) {
                        Sym("more_vert", 24f, ui.cs.onSurface, label = "Note options")
                    }
                }
                BasicTextField(
                    title, { title = it; vm.notes.edit(id, it, RichCodec.toRich(editor.text)) },
                    // on the body's left line: its 20dp padding, less 1 for the big bold letters' wider side bearing
                    Modifier.fillMaxWidth().padding(start = 19.dp, end = 20.dp, top = 8.dp),
                    textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.extrabold, fontSize = (26 * s.fontScale).sp),
                    cursorBrush = SolidColor(ui.cs.primary), maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { editor.requestFocus(); editor.setSelection(0) }),
                    decorationBox = { inner -> Box { if (title.isEmpty()) Txt("Title", 26f, ui.cs.onSurfaceVariant.copy(alpha = 0.6f), ui.extrabold); inner() } },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                // the text scrolls in a ScrollView that doesn't clip to its padding, so lines pass behind the Pill (only
                // the Pill covers content) instead of being cut off above it, and nothing the editor draws (a picture's
                // frame) leaves the page to sit over the title or its buttons
                val scroller = remember(editor) {
                    object : android.widget.ScrollView(ctx) {
                        // Android smooth-scrolls the focused view "back on screen" whenever this resizes (that's all
                        // ScrollView's onSizeChanged does), measured to the editor's full height, Pill room included:
                        // the note slid away every time the keyboard went. The place stays put instead (the keyboard
                        // coming up keeps the cursor in view below)
                        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {}
                    }.apply {
                        isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false
                        (editor.parent as? android.view.ViewGroup)?.removeView(editor)
                        addView(editor, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                        // the page got shorter (the keyboard coming up, frame by frame): what you're typing stays in view,
                        // above the keyboard and the Pill
                        addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                            if (bottom - top < oldBottom - oldTop && editor.hasFocus()) editor.keepCursorInView()
                        }
                    }
                }
                AndroidView(
                    factory = { scroller.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
                    update = { v ->
                        // room at the bottom for the Pill, so the last lines can scroll up above it
                        val pad = (20 * density.density).toInt()
                        editor.setPadding(pad, (6 * density.density).toInt(), pad, pad)
                        // the Pill's room, or the format panel's while it's open. The page already ends at the keyboard
                        // and a panel rises from the screen's bottom over it, so only the part above the keyboard counts:
                        // counted whole while the keyboard goes down, the note scrolled for room it already had
                        val gap = maxOf(pillPx, sheetPx - (imeDp() * density.density).toInt()) + pad
                        v.setPadding(0, 0, 0, gap)
                        editor.bottomGap = gap
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )
                // the editor always holds a line break of its own (RichCodec), so its hint never shows: this stands in,
                // read from the editor itself on every change
                val empty = run { tick; showsPlaceholder(editor.text) }
                if (empty) Txt("Start writing", 17f * s.editorTextScale, ui.cs.onSurfaceVariant.copy(alpha = 0.7f), ui.regular,
                    Modifier.padding(start = 20.dp, top = 6.dp), scaled = false)
                }
            }
        }
        FormatSheet(editor, tick, panel, onClose = { panel = false }, onSize = { sheetPx = it })
        FormatPill(editor, tick, panel, { panel = it }, navDp, imeDp, onSize = { pillPx = it }, onShrink = onShrink, onShrinkRelease = onShrinkRelease,
            hidden = { vm.noteOpen() < 0.999f },
            // measured against the note's own page, so the note growing or shrinking around it doesn't skew it (the
            // page fills the window when open)
            onPlace = { c -> vm.toolPill = { pageCoords?.takeIf { it.isAttached && c.isAttached }?.localBoundingBoxOf(c, clipBounds = false) } })
        tick
    }
}
