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

fun backupSection(vm: NotesViewModel) = SettingsSection("data", "Backup", "save", "Export, import, reset", listOf(
    SettingsHeading("Backup"),
    SettingsRow("Back Up Everything", "export backup save file minotes") { BackupItem(vm) },
    SettingsRow("Restore a Backup", "import restore load file minotes") { RestoreItem(vm) },
    SettingsHeading("Markdown"),
    SettingsRow("Export as Markdown", "export markdown md files zip obsidian") { MarkdownExportItem(vm) },
    SettingsRow("Import Notes", "import markdown md text txt files") { ImportNotesItem(vm) },
    SettingsHeading("Reset"),
    SettingsRow("Reset Settings", "defaults") {
        Item("restart_alt", "Reset Settings", "Back to the defaults; your notes stay", danger = true, onClick = {
            vm.ask = Ask.Alert("Reset Settings?", "Every setting goes back to its default. Your notes and folders stay as they are.", "Reset", destructive = true) { vm.resetSettings() }
        })
    },
))

@Composable
private fun BackupItem(vm: NotesViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val b = vm.backup()
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { DataTransfer.exportTo(ctx, uri, b) }.exceptionOrNull() }
            Notices.show(if (err == null) "Backed up" else "Couldn't save the backup", done = err == null)
        }
    }
    Item("upload", "Back Up Everything", "Save your notes, folders, pictures and settings to a file", onClick = { saver.launch("mi-notes-${LocalDate.now()}.minotes") })
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
                    vm.ask = Ask.Alert("Restore Backup?", "This adds ${if (n == 1) "1 note" else "$n notes"} and replaces your settings. Notes you already have stay as they are.", "Restore") { vm.restore(b) }
                }
                .onFailure { e -> vm.ask = Ask.Alert((e as? ImportError)?.title ?: "Couldn't Restore", (e as? ImportError)?.message ?: "This file couldn't be read.") }
        }
    }
    Item("download", "Restore a Backup", "Add notes from a .minotes file", onClick = { opener.launch(arrayOf("*/*")) })
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
            val notes = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
                        markdownToNote(text, nameOf(ctx, uri).substringBeforeLast('.'), UUID.randomUUID().toString(), now)
                    }.getOrNull()
                }
            }
            if (notes.isEmpty()) Notices.show("Couldn't read those files") else vm.importNotes(notes)
        }
    }
    Item("add", "Import Notes", "Markdown or text files become notes in the folder showing", onClick = { opener.launch(arrayOf("text/*", "application/octet-stream")) })
}

private fun nameOf(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Imported note"
