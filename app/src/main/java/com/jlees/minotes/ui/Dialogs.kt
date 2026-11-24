// mi. Notes' dialogs: naming notes and folders, moving a note to a folder, and alerts.
package com.jlees.minotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.ui.common.*

@Composable
fun Dialogs(vm: NotesViewModel) {
    val ui = LocalUi.current
    var shown by remember { mutableStateOf<Ask?>(null) }
    if (vm.ask != null) shown = vm.ask
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = vm.ask != null
    if (!visible.currentState && !visible.targetState) return
    val ask = shown ?: return
    val close = { vm.ask = null }
    BackHandler(vm.ask != null) { close() }
    Box(Modifier.fillMaxSize().zIndex(500f).imePadding(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(Motion.enter(200)), exit = fadeOut(Motion.exit(150))) {
            Box(Modifier.fillMaxSize().background(ui.cs.scrim.copy(alpha = 0.4f)).blockTouches(close))
        }
        AnimatedVisibility(visible, enter = scaleIn(Motion.fastSpatial(), initialScale = 0.85f) + fadeIn(Motion.enter(150)), exit = scaleOut(Motion.exit(150), targetScale = 0.9f) + fadeOut(Motion.exit(120))) {
            when (ask) {
                is Ask.RenameNote -> NameDialog("Rename Note", vm.notes[ask.id]?.title.orEmpty(), "Title", close) { vm.notes.rename(ask.id, it) }
                is Ask.NewFolder -> NameDialog("New Folder", "", "Name", close, confirm = "Create", requireText = true) { name ->
                    val f = vm.notes.addFolder(name)
                    if (ask.thenMove != null) vm.moveTo(ask.thenMove, f.id) else vm.showFolder(f.id)
                }
                is Ask.RenameFolder -> NameDialog("Rename Folder", vm.notes.folders.firstOrNull { it.id == ask.id }?.name.orEmpty(), "Name", close, requireText = true) { vm.notes.renameFolder(ask.id, it) }
                is Ask.MoveNote -> MoveDialog(vm, ask.id, close)
                is Ask.Alert -> DialogCard(ask.title, actions = {
                    if (ask.confirm != null) { DialogAction("Cancel") { close() }; DialogAction(ask.confirm, ask.destructive) { close(); ask.onConfirm() } }
                    else DialogAction("OK") { close() }
                }) { Txt(ask.message, 15f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 22f) }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, placeholder: String, close: () -> Unit, confirm: String = "Save", requireText: Boolean = false, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val ok = !requireText || text.isNotBlank()
    DialogCard(title, actions = {
        DialogAction("Cancel") { close() }
        DialogAction(confirm, modifier = Modifier.then(if (ok) Modifier else Modifier)) { if (ok) { close(); onSave(text.trim()) } }
    }) { ClearableField(text, { text = it }, fr, placeholder = placeholder) }
}

@Composable
private fun MoveDialog(vm: NotesViewModel, id: String, close: () -> Unit) {
    val ui = LocalUi.current
    val current = vm.notes[id]?.folder
    DialogCard("Move to Folder", actions = { DialogAction("Cancel") { close() } }) {
        @Composable
        fun Option(icon: String, label: String, on: Boolean, onClick: () -> Unit) = Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).touchable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon, 22f, if (on) ui.cs.primary else ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
            Txt(label, 16f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
            if (on) Sym("check", 20f, ui.cs.primary)
        }
        Option("notes", "No Folder", current == null) { close(); if (current != null) vm.moveTo(id, null) }
        vm.notes.folders.forEach { f -> Option("folder", f.name, current == f.id) { close(); if (current != f.id) vm.moveTo(id, f.id) } }
        Option("create_new_folder", "New Folder", false) { vm.ask = Ask.NewFolder(thenMove = id) }
    }
}
