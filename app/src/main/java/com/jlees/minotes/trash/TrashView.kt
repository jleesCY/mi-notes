// Recently Deleted: notes you deleted, kept for 30 days. Tap one to put it back; swipe it left to delete it for good.
package com.jlees.minotes.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.notes.TRASH_DAYS
import com.jlees.minotes.notes.daysLeft
import com.jlees.minotes.notes.displayTitle
import com.jlees.minotes.notes.trashed
import com.jlees.minotes.ui.noteCard
import com.jlees.minotes.ui.common.*

@Composable
fun TrashView(vm: NotesViewModel) {
    val ui = LocalUi.current
    var query by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val notes = trashed(vm.notes.notes).filter { query.isBlank() || displayTitle(it).contains(query, true) || it.body.plain.contains(query, true) }
    val rowH = rowHeight(vm.settings.uiPadding, vm.settings.fontScale) + 8f
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = listSides(), end = listSides(), top = 8.dp, bottom = SheetPillSpace),
            verticalArrangement = Arrangement.spacedBy(listGap().dp),
        ) {
            item(key = "info") {
                Txt("Notes stay here for $TRASH_DAYS days, then they're gone for good.", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.listInset().padding(start = 4.dp, bottom = 8.dp), lineHeight = 18f)
            }
            if (notes.isEmpty()) item(key = "empty") { EmptyState(if (query.isBlank()) "delete" else "search", if (query.isBlank()) "Nothing Deleted" else "No Matches") }
            items(notes, key = { it.id }) { n ->
                SwipeActionRow(
                    null, onDelete = { vm.deleteForever(n.id) }, Modifier.animateItem(),
                    onEdit = { vm.notes.restore(n.id); Notices.show("Restored", done = true) }, editIcon = "restore_from_trash", editLabel = "Restore",
                    // it can't be undone, so it asks; the row springs back meanwhile (and goes once you say yes)
                    askDelete = { answer ->
                        answer(false)
                        vm.ask = Ask.Alert("Delete Forever?", "\"${displayTitle(n)}\" will be deleted for good. This can't be undone.", "Delete", destructive = true) { vm.deleteForever(n.id) }
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = rowH.dp).listRow().touchable { vm.notes.restore(n.id); Notices.show("Restored", done = true) }.padding(horizontal = 15.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RowIconTile(color = noteCard(n.color)) { Sym("sticky_note_2", 22f, ui.cs.onSurfaceVariant) }
                        Column(Modifier.weight(1f)) {
                            Txt(displayTitle(n), 16f, ui.cs.onSurface, ui.semibold, maxLines = 1)
                            val d = daysLeft(n, now)
                            Txt(if (d == 1) "1 day left" else "$d days left", 13f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 2.dp), lineHeight = 18f)
                        }
                        Sym("restore_from_trash", 22f, ui.cs.onSurfaceVariant, label = "Restore")
                    }
                }
            }
        }
        SheetSearchPill(query, { query = it }, placeholder = "Search deleted notes", menu = { close ->
            SheetMenuItem("restore_from_trash", "Restore All") { close(); trashed(vm.notes.notes).forEach { vm.notes.restore(it.id) }; Notices.show("Restored all", done = true) }
            SheetMenuItem("delete_forever", "Empty Recently Deleted") { close(); vm.emptyTrash() }
        })
    }
}
