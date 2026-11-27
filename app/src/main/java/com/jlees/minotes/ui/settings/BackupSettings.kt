// Settings › Backup: everything to one .minotes file and back, notes as Markdown files, Markdown and text files in.
package com.jlees.minotes.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jlees.minotes.Ask
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.data.DataTransfer
import com.jlees.minotes.data.ImportError
import com.jlees.minotes.notes.markdownToNote
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

fun backupSection(vm: NotesViewModel) = SettingsSection("data", "Backup & Reset", "save", "Back up, restore, reset", listOf(
    SettingsHeading("Backup"),
    SettingsRow("Back Up", "backup export save file notes pictures settings") { BackupItem(vm) },
    SettingsRow("Restore", "restore import load backup file") { RestoreItem(vm) },
    SettingsHeading("Markdown"),
    SettingsRow("Export as Markdown", "export markdown md files zip obsidian") { MarkdownExportItem(vm) },
    SettingsRow("Import Notes", "import markdown md text txt files") { ImportNotesItem(vm) },
    SettingsHeading("Reset"),
    SettingsRow("Reset All Settings", "defaults reset settings") {
        Item("restart_alt", "Reset All Settings", "Back to defaults; your notes and folders are kept", danger = true, onClick = {
            // the default Keep Deleted Notes can be shorter than yours: then older deleted notes go, and it says so
            val notes = vm.expiringSentence(com.jlees.minotes.data.Settings().keepDeleted) ?: "Your notes and folders stay as they are."
            vm.ask = Ask.Alert("Reset All Settings?", "Every setting goes back to its default. $notes", "Reset", destructive = true) { vm.resetSettings() }
        })
    },
    SettingsRow("Wipe All Data", "erase delete everything wipe") { WipeItem(vm) },
))

@Composable
private fun WipeItem(vm: NotesViewModel) {
    val activity = LocalContext.current as android.app.Activity
    Item("delete", "Wipe All Data", "Delete every note, folder, picture and setting", danger = true, onClick = {
        vm.ask = Ask.Alert("Wipe mi. Notes?", "Every note, folder, picture and setting will be deleted for good, and mi. Notes will start again as if just installed. This can't be undone.", "Wipe App", destructive = true) { vm.wipeAll(activity) }
    })
}

@Composable
private fun BackupItem(vm: NotesViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val b = vm.backup()
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, b) }.exceptionOrNull() }
            Notices.show(if (err == null) "Saved backup" else "Couldn't save the backup", done = err == null)
        }
    }
    Item("upload", "Back Up", "Save your notes and settings to a file", onClick = { saver.launch("mi-notes-${LocalDate.now()}.minotesdata") })
}

@Composable
private fun RestoreItem(vm: NotesViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { DataTransfer.import(ctx, uri) } }
                .onSuccess { b ->
                    val n = b.notes.size
                    // its Keep Deleted Notes can be shorter than yours: then older deleted notes go, and it says so
                    val gone = vm.expiringSentence(b.settings.keepDeleted, b.notes)
                    vm.ask = Ask.Alert("Restore Backup?", "This replaces your settings and adds ${if (n == 1) "1 note" else "$n notes"}. ${gone ?: "Notes you already have stay as they are."}", "Restore", destructive = gone != null) { vm.restore(b) }
                }
                .onFailure { e -> vm.ask = Ask.Alert((e as? ImportError)?.title ?: "Couldn't Import", (e as? ImportError)?.message ?: "This file couldn't be read.") }
        }
    }
    Item("download", "Restore", "Bring back everything from a backup file", onClick = { opener.launch(arrayOf("*/*")) })
}

@Composable
private fun MarkdownExportItem(vm: NotesViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val notes = vm.notes.notes.filter { it.deletedAt == null }; val folders = vm.notes.folders
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportMarkdown(ctx, uri, notes, folders) }.exceptionOrNull() }
            Notices.show(if (err == null) "Exported" else "Couldn't save the file", done = err == null)
        }
    }
    Item("description", "Export as Markdown", "Every note as a .md file, in a zip, for any notes app", onClick = { saver.launch("mi-notes-markdown-${LocalDate.now()}.zip") })
}

@Composable
private fun ImportNotesItem(vm: NotesViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val now = System.currentTimeMillis()
            var tooBig = 0
            val notes = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val text = DataTransfer.readTextCapped(ctx, uri) ?: run { tooBig++; return@runCatching null }
                        markdownToNote(text, nameOf(ctx, uri).substringBeforeLast('.'), UUID.randomUUID().toString(), now)
                    }.getOrNull()
                }
            }
            val skipped = when (tooBig) { 0 -> ""; 1 -> "1 file was too big"; else -> "$tooBig files were too big" }
            when {
                notes.isNotEmpty() -> vm.importNotes(notes, skipped)
                tooBig > 0 -> Notices.show(if (tooBig == 1) "Couldn't import: the file was too big" else "Couldn't import: the files were too big")
                else -> Notices.show("Couldn't read those files")
            }
        }
    }
    Item("add", "Import Notes", "Markdown or text files become notes in the folder showing", onClick = { opener.launch(arrayOf("text/*", "application/octet-stream")) })
}

private fun nameOf(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Imported note"
