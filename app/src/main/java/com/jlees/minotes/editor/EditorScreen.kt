// A note open for writing: back and ⋮ at the top, the title, then the body, in the note's colour. The format Pill at
// the bottom rides on the keyboard.
package com.jlees.minotes.editor

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.R
import com.jlees.minotes.data.AppFont
import com.jlees.minotes.home.shortDate
import com.jlees.minotes.notes.wordCount
import com.jlees.minotes.ui.notePage
import com.jlees.minotes.ui.common.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

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
    var tick by remember { mutableIntStateOf(0) } // the editor's state changed: the format controls re-read it
    var panel by remember { mutableStateOf(false) }
    var pillPx by remember { mutableIntStateOf(0) }
    var sheetPx by remember { mutableIntStateOf(0) } // the format panel's height while it's open (0 closed)
    var insertPx by remember { mutableIntStateOf(0) } // the + panel's (its own: one closing as the other opens mustn't zero it)
    val editor = remember(id) {
        NoteEditText(ctx).apply {
            EditorFonts.sans = ResourcesCompat.getFont(ctx, R.font.google_sans_flex) ?: android.graphics.Typeface.DEFAULT
            load(note.body, note.title) // one text: the title is its first line
        }
    }
    // renamed from outside the editor (⋮ › Rename while the note is open): its first line becomes the new name. Typing
    // in it saves the same title it holds, so this does nothing then
    LaunchedEffect(note.title) { if (note.title != editor.committedTitle) editor.setTitle(note.title) }
    // the theme and settings, into the Android view
    val ink = ui.cs.onSurface.toArgb()
    LaunchedEffect(ink, ui.cs.primary, s.editorTextScale, s.font, s.checkedToBottom, s.fontScale) {
        val d = density.density
        ParaSpan.Look.apply { indent = (24 * d).toInt(); marker = (30 * d).toInt(); this.ink = ui.cs.onSurfaceVariant.toArgb(); accent = ui.cs.primary.toArgb(); onAccent = ui.cs.onPrimary.toArgb(); this.density = d }
        editor.apply {
            // the title line: 26sp (times the app's Font Size), as the title was, with room below it
            titlePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26f * s.fontScale, ctx.resources.displayMetrics); titleGap = (8 * d).toInt()
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
            restyleTitle(); invalidate()
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
        editor.onChange = { body -> vm.notes.edit(id, editor.title(), body) }
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
            // a plain new note starts on its title line; a new checklist on its first item
            editor.requestFocus(); editor.setSelection(if (note.body.spans.isEmpty()) 0 else editor.titleEnd() + 1)
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
                        editor.setPadding(pad, (8 * density.density).toInt(), pad, pad)
                        // the Pill's room, or the format panel's while it's open. The page already ends at the keyboard
                        // and a panel rises from the screen's bottom over it, so only the part above the keyboard counts:
                        // counted whole while the keyboard goes down, the note scrolled for room it already had
                        val gap = maxOf(pillPx, maxOf(sheetPx, insertPx) - (imeDp() * density.density).toInt()) + pad
                        v.setPadding(0, 0, 0, gap)
                        // a panel opening over the text: what you selected (or the cursor) glides up into view above it
                        val grew = gap > editor.bottomGap
                        editor.bottomGap = gap
                        if (grew && editor.hasFocus()) editor.keepCursorInView()
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )
                // its "Title" and "Start writing" hints: drawn by the editor (NoteEditText.onDraw)
                }
            }
        }
        FormatSheet(editor, tick, panel, onClose = { panel = false }, onSize = { sheetPx = it })
        FormatPill(editor, tick, panel, { panel = it }, navDp, imeDp, onSize = { pillPx = it }, onInsertSize = { insertPx = it }, onShrink = onShrink, onShrinkRelease = onShrinkRelease,
            hidden = { vm.noteOpen() < 0.999f },
            // measured against the note's own page, so the note growing or shrinking around it doesn't skew it (the
            // page fills the window when open)
            onPlace = { c -> vm.toolPill = { pageCoords?.takeIf { it.isAttached && c.isAttached }?.localBoundingBoxOf(c, clipBounds = false) } })
        tick
    }
}
