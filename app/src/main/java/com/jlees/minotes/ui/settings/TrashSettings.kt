// Settings › Recently Deleted: how long deleted notes are kept, and emptying it now.
package com.jlees.minotes.ui.settings

import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.notes.trashed

fun trashSection(vm: NotesViewModel) = SettingsSection("deleted", "Recently Deleted", "delete", "How deleted notes are kept", listOf(
    SettingsRow("Keep Deleted Notes", "trash recently deleted days week month keep how long") {
        ChoiceItem("history", "Keep Deleted Notes", "How long deleted notes stay in Recently Deleted", vm.settings.keepDeleted) { vm.setKeepDeleted(it) }
    },
    SettingsRow("Empty Recently Deleted", "trash clear delete forever empty") {
        val n = trashed(vm.notes.notes).size
        Item("delete", "Empty Recently Deleted", if (n == 0) "Nothing deleted" else if (n == 1) "1 note" else "$n notes", danger = true,
            onClick = if (n == 0) null else { { vm.emptyTrash() } })
    },
))
