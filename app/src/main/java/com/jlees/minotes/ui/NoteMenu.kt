// A note's menu (hold its card, or ⋮ in the editor), laid out as every mi. item menu: the note at the top, its main
// actions as large tiles, its colour, then tools, and deleting it last.
package com.jlees.minotes.ui

import androidx.lifecycle.viewModelScope
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.notes.NoteColor
import com.jlees.minotes.notes.ShareNote
import com.jlees.minotes.notes.displayTitle
import com.jlees.minotes.notes.noteToMarkdown
import com.jlees.minotes.notes.wordCount
import com.jlees.minotes.data.DataTransfer
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * [fromTop]: opened from the editor's ⋮ (it hangs from the top); else from a held card (it sits above the Pill).
 * [origin]: where it grows from, in window px (the ⋮, the card); null: its own top right.
 */
@Composable
fun NoteMenu(vm: NotesViewModel, fromTop: Boolean, statusDp: Float, navDp: Float, origin: androidx.compose.ui.geometry.Offset? = null) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Export's file picker answers long after the menu has closed and stopped drawing, so its launcher is registered
    // up here, before the early return below, and the note is looked up when the answer comes back
    var exporting by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val n = vm.notes[exporting]
        if (uri == null || n == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(noteToMarkdown(n).toByteArray()) } }.isSuccess }
            Notices.show(if (ok) "Exported" else "Couldn't save the file", done = ok)
        }
    }
    // what's shown stays while it shrinks away
    var shownId by remember { mutableStateOf<String?>(null) }
    if (vm.menuFor != null) shownId = vm.menuFor
    val scale = remember { androidx.compose.animation.core.Animatable(0.85f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    var gone by remember { mutableStateOf(true) }
    LaunchedEffect(vm.menuFor != null) {
        if (vm.menuFor != null) {
            gone = false; scale.snapTo(0.85f); alpha.snapTo(0f)
            launch { alpha.animateTo(1f, androidx.compose.animation.core.tween(120)) }
            scale.animateTo(1f, Motion.noBounce())
        } else if (!gone) {
            launch { alpha.animateTo(0f, Motion.exit(100)) }
            scale.animateTo(0.9f, Motion.exit(120))
            gone = true
        }
    }
    if (gone && vm.menuFor == null) return
    val n = vm.notes[shownId] ?: return
    val close = { vm.menuFor = null }
    BackHandler(vm.menuFor != null) { close() }
    var panel by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // a tap outside closes it
    Box(Modifier.fillMaxSize().zIndex(300f).blockTouches(close)
        .padding(top = (statusDp + 16).dp, bottom = (navDp + 88).dp, start = 12.dp, end = 12.dp),
        contentAlignment = if (fromTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
            Column(
                Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxWidth()
                    .onGloballyPositioned { panel = it.boundsInWindow() }
                    .graphicsLayer {
                        // it grows from what opened it: the ⋮ or the card
                        val r = panel
                        transformOrigin = if (r == null || r.width == 0f || r.height == 0f || origin == null) TransformOrigin(1f, 0f)
                            else TransformOrigin(((origin.x - r.left) / r.width).coerceIn(0f, 1f), ((origin.y - r.top) / r.height).coerceIn(0f, 1f))
                        scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
                        shape = RoundedCornerShape(minOf(ui.radius, 28).dp); clip = true; shadowElevation = 10.dp.toPx()
                    }
                    .background(ui.cs.surfaceContainer).blockTouches()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                // 1. the note
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // the note, and its folder (with › : tapping it moves the note)
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).touchable { close(); vm.ask = Ask.MoveNote(n.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(noteCard(n.color)), contentAlignment = Alignment.Center) { Sym("sticky_note_2", 22f, ui.cs.onSurfaceVariant) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Txt(displayTitle(n), 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(vm.notes.folders.firstOrNull { it.id == n.folder }?.name ?: "No Folder", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.weight(1f, fill = false), maxLines = 1)
                                Sym("chevron_right", 16f, ui.cs.onSurfaceVariant)
                            }
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = {
                        if (ShareNote.copy(ctx, n)) Notices.show("Copied", done = true) else Notices.show("Too long to copy. Share it as Markdown instead")
                        close()
                    }) { Sym("content_copy", 22f, ui.cs.onSurfaceVariant, label = "Copy text") }
                }
                // 2. main actions
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigTile(if (n.pinned) "keep_off" else "keep", if (n.pinned) "Unpin" else "Pin", n.pinned) { vm.togglePin(n.id) }
                    BigTile("share", "Share", false) { close(); vm.viewModelScope.launch { ShareNote.asText(ctx, n) } }
                    BigTile("drive_file_move", "Move", false) { close(); vm.ask = Ask.MoveNote(n.id) }
                    BigTile("edit", "Rename", false) { close(); vm.ask = Ask.RenameNote(n.id) }
                }
                // 3. this note: its colour
                MenuHeading("This Note")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(NoteColor.entries) { c ->
                        val on = n.color == c
                        Box(
                            Modifier.size(40.dp).clip(if (on) RoundedCornerShape(30) else CircleShape).background(swatch(c))
                                .then(if (on) Modifier.border(2.dp, ui.cs.onSurface, RoundedCornerShape(30)) else Modifier)
                                .touchable { vm.notes.setColor(n.id, c) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == NoteColor.NONE) Sym("block", 18f, ui.cs.onSurfaceVariant, label = c.label)
                            else if (on) Sym("check", 20f, Color.White, label = c.label)
                        }
                    }
                }
                // 4. tools
                MenuHeading("Tools")
                Row(Modifier.fillMaxWidth()) {
                    Round("description", "Markdown") { close(); vm.viewModelScope.launch { ShareNote.asMarkdown(ctx, n) } }
                    Round("download", "Export") { close(); exporting = n.id; export.launch(DataTransfer.safeName(displayTitle(n)) + ".md") }
                    Round("content_copy", "Duplicate") { vm.duplicate(n.id) }
                    Round("info", "Info") {
                        close()
                        val f = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        val words = wordCount(n.body.text); val chars = n.body.plain.count { !it.isWhitespace() }
                        vm.ask = Ask.Alert(displayTitle(n), "Created ${f.format(Date(n.created))}\nEdited ${f.format(Date(n.edited))}\n$words words · $chars characters")
                    }
                }
                // 5. delete, last
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ui.cs.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).touchable { vm.deleteNote(n.id) }.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("delete", 22f, ui.cs.error, Modifier.padding(end = 16.dp))
                    Txt("Delete Note", 15f, ui.cs.error, ui.semibold)
                }
            }
    }
}

/** A heading inside the menu ("This Note", "Tools"). */
@Composable
private fun MenuHeading(text: String) {
    val ui = LocalUi.current
    Txt(text, 13f, ui.cs.primary, ui.bold, Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp))
}

@Composable
private fun RowScope.BigTile(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(if (on) ui.cs.primaryContainer else ui.cs.surfaceContainerHighest, label = "tile")
    val fg = if (on) ui.cs.onPrimaryContainer else ui.cs.onSurface
    val pop = popScale(on)
    Column(
        Modifier.weight(1f).height(72.dp).pressScale(press).clip(RoundedCornerShape(if (on) 28.dp else 18.dp)).background(bg).touchable(source = press, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Sym(icon, 22f, fg, Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
        Txt(label, 13f, fg, ui.semibold, Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

@Composable
private fun RowScope.Round(icon: String, label: String, onClick: () -> Unit) {
    val ui = LocalUi.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier.weight(1f).pressScale(press).clip(RoundedCornerShape(16.dp)).touchable(source = press, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(ui.cs.secondaryContainer), contentAlignment = Alignment.Center) { Sym(icon, 24f, ui.cs.onSecondaryContainer) }
        Txt(label, 12f, ui.cs.onSurface, ui.semibold, Modifier.padding(top = 4.dp), maxLines = 1)
    }
}
