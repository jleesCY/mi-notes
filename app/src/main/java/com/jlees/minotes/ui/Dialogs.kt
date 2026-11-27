// mi. Notes' dialogs: naming notes and folders, moving a note to a folder, alerts and confirmations, all in mi.'s
// frame (ModalFrame) and card (DialogCard).
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.delay

/**
 * The frame every dialog sits in: black at 50% behind it (fading in over 200, out over 120), the card scaling in from
 * 0.9 (250) and out to 0.95 (120), kept clear of the system bars, cutouts and keyboard. A tap on the dim closes it
 * only when [dismissOnScrim] (nothing would be lost: alerts, pickers); forms need Cancel. Back always closes it.
 */
@Composable
fun ModalFrame(visible: Boolean, onDismiss: () -> Unit, dismissOnScrim: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    BackHandler(visible) { onDismiss() }
    Box(Modifier.fillMaxSize().zIndex(500f)) {
        AnimatedVisibility(state, enter = fadeIn(Motion.enter(200)), exit = fadeOut(Motion.exit(120))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).blockTouches { if (dismissOnScrim) onDismiss() }
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.animateEnterExit(enter = scaleIn(Motion.enter(250), initialScale = 0.9f), exit = scaleOut(Motion.exit(120), targetScale = 0.95f)),
                    contentAlignment = Alignment.Center, content = content,
                )
            }
        }
    }
}

@Composable
fun Dialogs(vm: NotesViewModel) {
    val ui = LocalUi.current
    // what's shown stays while it animates out
    var shown by remember { mutableStateOf<Ask?>(null) }
    if (vm.ask != null) shown = vm.ask
    val ask = shown ?: return
    val close = { vm.ask = null }
    // a form loses what was typed if the dim closed it; an alert or a picker loses nothing
    ModalFrame(vm.ask != null, close, dismissOnScrim = ask is Ask.Alert || ask is Ask.MoveNote || ask is Ask.FolderMenu) {
        when (ask) {
            is Ask.RenameNote -> NameDialog("Rename Note", vm.notes[ask.id]?.title.orEmpty(), "Title", close) { vm.notes.rename(ask.id, it) }
            is Ask.NewFolder -> NameDialog("New Folder", "", "Name", close, confirm = "Create", requireText = true) { name ->
                val f = vm.notes.addFolder(name)
                if (ask.thenMove != null) vm.moveTo(ask.thenMove, f.id) else vm.showFolder(f.id)
            }
            is Ask.RenameFolder -> NameDialog("Rename Folder", vm.notes.folders.firstOrNull { it.id == ask.id }?.name.orEmpty(), "Name", close, requireText = true) { vm.notes.renameFolder(ask.id, it) }
            is Ask.MoveNote -> MoveDialog(vm, ask.id, close)
            is Ask.FolderMenu -> FolderMenu(vm, ask.id, close)
            is Ask.Alert -> DialogCard(ask.title, actions = {
                if (ask.confirm != null) { DialogAction("Cancel") { close() }; DialogAction(ask.confirm, ask.destructive) { close(); ask.onConfirm() } }
                else DialogAction("Done") { close() }
            }) { Txt(ask.message, 14f, ui.cs.onSurfaceVariant, ui.regular, lineHeight = 20f) }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, placeholder: String, close: () -> Unit, confirm: String = "Save", requireText: Boolean = false, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    // after the dialog's own animation, so the keyboard comes up into a dialog that's already in place
    LaunchedEffect(Unit) { delay(300); fr.requestFocus() }
    val ok = !requireText || text.isNotBlank()
    DialogCard(title, actions = {
        DialogAction("Cancel") { close() }
        DialogAction(confirm) { if (ok) { close(); onSave(text.trim()) } }
    }) { ClearableField(text, { text = it }, fr, placeholder = placeholder) }
}

/** One row of a choice list (mi-brand): 48 tall, 12dp corners, a 22dp symbol, the label; [checked] marks the current
 *  choice with the accent and a check, [danger] colours it as destructive. */
@Composable
private fun ChoiceRow(icon: String, label: String, checked: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).touchable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, 22f, when { danger -> ui.cs.error; checked -> ui.cs.primary; else -> ui.cs.onSurfaceVariant }, Modifier.padding(end = 16.dp))
        Txt(label, 15f, if (danger) ui.cs.error else ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
        if (checked) Sym("check", 20f, ui.cs.primary)
    }
}

/** A held folder chip's choices (mi-brand's long-press choice list): its name and count, then what to do with it. */
@Composable
private fun FolderMenu(vm: NotesViewModel, id: String, close: () -> Unit) {
    val ui = LocalUi.current
    val f = vm.notes.folders.firstOrNull { it.id == id } ?: return
    val count = vm.notes.notes.count { it.deletedAt == null && it.folder == id }
    DialogCard(null, actions = { DialogAction("Cancel") { close() } }) {
        Txt(f.name, 18f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 4.dp), maxLines = 2)
        Txt(if (count == 1) "1 note" else "$count notes", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(bottom = 12.dp), maxLines = 1)
        ChoiceRow("edit", "Rename Folder") { vm.ask = Ask.RenameFolder(id) }
        ChoiceRow("delete", "Delete Folder", danger = true) { close(); vm.deleteFolder(id) }
    }
}

@Composable
private fun MoveDialog(vm: NotesViewModel, id: String, close: () -> Unit) {
    val current = vm.notes[id]?.folder
    DialogCard("Move to Folder", actions = { DialogAction("Cancel") { close() } }) {
        ChoiceRow("notes", "No Folder", checked = current == null) { close(); if (current != null) vm.moveTo(id, null) }
        vm.notes.folders.forEach { f -> ChoiceRow("folder", f.name, checked = current == f.id) { close(); if (current != f.id) vm.moveTo(id, f.id) } }
        ChoiceRow("create_new_folder", "New Folder") { vm.ask = Ask.NewFolder(thenMove = id) }
    }
}
