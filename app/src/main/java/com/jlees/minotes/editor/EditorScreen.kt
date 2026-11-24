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

@Composable
fun EditorScreen(vm: NotesViewModel, id: String, statusDp: Float, navDp: Float, imeDp: () -> Float, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val note = vm.notes[id] ?: return
    val s = vm.settings
    var title by remember(id) { mutableStateOf(note.title) }
    var tick by remember { mutableIntStateOf(0) } // the editor's state changed: the format controls re-read it
    var panel by remember { mutableStateOf(false) }
    var pillPx by remember { mutableIntStateOf(0) }
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
            if (android.os.Build.VERSION.SDK_INT >= 29) textCursorDrawable = GradientDrawable().apply { setColor(ui.cs.primary.toArgb()); setSize((2 * d).toInt(), 1) }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * s.editorTextScale)
            setLineSpacing(5 * d, 1f)
            hint = "Start writing"
            fadedInk = ui.cs.onSurfaceVariant.copy(alpha = 0.6f).toArgb()
            checkedToBottom = s.checkedToBottom
            invalidate()
        }
    }
    DisposableEffect(editor) {
        editor.onChange = { body -> vm.notes.edit(id, title, body) }
        editor.onState = { tick++ }
        editor.onTouchText = { panel = false }
        onDispose { editor.onChange = {}; editor.onState = {}; editor.onTouchText = {} }
    }
    // a new note: straight to the keyboard
    LaunchedEffect(id) {
        if (note.isEmpty) {
            kotlinx.coroutines.delay(300) // once the editor has grown into place
            editor.requestFocus()
            ctx.getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    val current = vm.notes[id] ?: note
    Box(Modifier.fillMaxSize().background(notePage(current.color))) {
        Column(Modifier.fillMaxSize().padding(top = statusDp.dp).paddingDp(bottom = imeDp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onClose) { Sym("arrow_back", 24f, ui.cs.onSecondaryContainer, label = "Back") }
                    Spacer(Modifier.weight(1f))
                    if (current.pinned) Sym("keep", 20f, ui.cs.primary, Modifier.padding(end = 4.dp))
                    androidx.compose.material3.IconButton(onClick = { vm.menuFor = id }, colors = IconButtonDefaults.iconButtonColors()) {
                        Sym("more_vert", 24f, ui.cs.onSurface, label = "Note options")
                    }
                }
                BasicTextField(
                    title, { title = it; vm.notes.edit(id, it, RichCodecHolder.body(editor)) },
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.extrabold, fontSize = (26 * s.fontScale).sp),
                    cursorBrush = SolidColor(ui.cs.primary), maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { editor.requestFocus(); editor.setSelection(0) }),
                    decorationBox = { inner -> Box { if (title.isEmpty()) Txt("Title", 26f, ui.cs.onSurfaceVariant.copy(alpha = 0.6f), ui.extrabold); inner() } },
                )
                val words = remember(current.body) { wordCount(current.body.text) }
                Txt("${shortDate(current.edited)}  ·  ${if (words == 1) "1 word" else "$words words"}", 12f, ui.cs.onSurfaceVariant, ui.semibold,
                    Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp))
                AndroidView(
                    factory = { editor.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
                    update = { v ->
                        // room at the bottom for the Pill, so the last lines can scroll up above it
                        val pad = (20 * density.density).toInt()
                        v.setPadding(pad, (6 * density.density).toInt(), pad, pillPx + pad * 2)
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
        FormatPill(editor, tick, panel, { panel = it }, navDp, imeDp, onSize = { pillPx = it }, onBack = onClose)
    }
}

/** The editor's current body, read when the title changes (so a title edit saves alongside the latest text). */
internal object RichCodecHolder { fun body(e: NoteEditText) = RichCodec.toRich(e.text) }
