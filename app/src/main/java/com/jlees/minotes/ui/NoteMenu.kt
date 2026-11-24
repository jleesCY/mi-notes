// A note's menu (hold its card, or ⋮ in the editor), laid out as every mi. item menu: the note at the top, its main
// actions as large tiles, its colour, then tools, and deleting it last.
package com.jlees.minotes.ui

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

/** [fromTop]: opened from the editor's ⋮ (it grows from the top right); else from a held card (it grows from the bottom). */
@Composable
fun NoteMenu(vm: NotesViewModel, fromTop: Boolean, statusDp: Float, navDp: Float) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // what's shown stays while it shrinks away
    var shownId by remember { mutableStateOf<String?>(null) }
    if (vm.menuFor != null) shownId = vm.menuFor
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = vm.menuFor != null
    if (!visible.currentState && !visible.targetState) return
    val n = vm.notes[shownId] ?: return
    val close = { vm.menuFor = null }
    BackHandler(vm.menuFor != null) { close() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(noteToMarkdown(n).toByteArray()) } }.isSuccess }
            Notices.show(if (ok) "Exported" else "Couldn't save the file", done = ok)
        }
    }

    Box(Modifier.fillMaxSize().zIndex(300f)) {
        AnimatedVisibility(visible, enter = fadeIn(Motion.enter(200)), exit = fadeOut(Motion.exit(150))) {
            Box(Modifier.fillMaxSize().background(ui.cs.scrim.copy(alpha = 0.32f)).blockTouches(close))
        }
        AnimatedVisibility(
            visible, Modifier.align(if (fromTop) Alignment.TopEnd else Alignment.BottomCenter)
                .padding(top = if (fromTop) (statusDp + 8).dp else 0.dp, bottom = if (fromTop) 0.dp else (navDp + 16).dp, start = 12.dp, end = 12.dp),
            enter = scaleIn(Motion.fastSpatial(), initialScale = 0.6f, transformOrigin = if (fromTop) TransformOrigin(1f, 0f) else TransformOrigin(0.5f, 1f)) + fadeIn(Motion.enter(150)),
            exit = scaleOut(Motion.exit(150), targetScale = 0.8f, transformOrigin = if (fromTop) TransformOrigin(1f, 0f) else TransformOrigin(0.5f, 1f)) + fadeOut(Motion.exit(120)),
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(minOf(ui.radius, 28).dp)).background(ui.cs.surfaceContainerHigh).blockTouches()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                // 1. the note
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(noteCard(n.color)), contentAlignment = Alignment.Center) { Sym("sticky_note_2", 22f, ui.cs.onSurfaceVariant) }
                    Column(Modifier.weight(1f).padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).touchable { close(); vm.ask = Ask.MoveNote(n.id) }) {
                        Txt(displayTitle(n), 16f, ui.cs.onSurface, ui.bold, maxLines = 1)
                        Txt((vm.notes.folders.firstOrNull { it.id == n.folder }?.name ?: "No Folder") + "  ›", 13f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1)
                    }
                    androidx.compose.material3.IconButton(onClick = { ShareNote.copy(ctx, n); Notices.show("Copied", done = true); close() }) { Sym("content_copy", 22f, ui.cs.onSurfaceVariant, label = "Copy text") }
                }
                // 2. main actions
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigTile(if (n.pinned) "keep_off" else "keep", if (n.pinned) "Unpin" else "Pin", n.pinned) { vm.togglePin(n.id) }
                    BigTile("share", "Share", false) { close(); ShareNote.asText(ctx, n) }
                    BigTile("drive_file_move", "Move", false) { close(); vm.ask = Ask.MoveNote(n.id) }
                    BigTile("edit", "Rename", false) { close(); vm.ask = Ask.RenameNote(n.id) }
                }
                // 3. its colour
                Txt("Note Color", 13f, ui.cs.onSurfaceVariant, ui.bold, Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp))
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
                            else if (on) Sym("check", 20f, Color.Black.copy(alpha = 0.75f), label = c.label)
                        }
                    }
                }
                // 4. tools
                Txt("Tools", 13f, ui.cs.onSurfaceVariant, ui.bold, Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Round("description", "Markdown") { close(); ShareNote.asMarkdown(ctx, n) }
                    Round("download", "Export") { close(); export.launch(DataTransfer.safeName(displayTitle(n)) + ".md") }
                    Round("content_copy", "Duplicate") { vm.duplicate(n.id) }
                    Round("info", "Info") {
                        close()
                        val f = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        val words = wordCount(n.body.text); val chars = n.body.plain.count { !it.isWhitespace() }
                        vm.ask = Ask.Alert(displayTitle(n), "Created ${f.format(Date(n.created))}\nEdited ${f.format(Date(n.edited))}\n$words words · $chars characters")
                    }
                }
                // 5. delete, last
                HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 6.dp), color = ui.cs.outlineVariant)
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
