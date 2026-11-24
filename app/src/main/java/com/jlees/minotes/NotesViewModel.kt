// Holds the stores and the app-wide UI state (which note is open, which sheet, which dialog), and coordinates between
// them. Note and folder changes themselves are NotesStore's; this decides when they happen (with Undo, mostly).
package com.jlees.minotes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jlees.minotes.data.Backup
import com.jlees.minotes.data.JsonStore
import com.jlees.minotes.data.Saver
import com.jlees.minotes.data.Settings
import com.jlees.minotes.data.importedOver
import com.jlees.minotes.data.migrated
import com.jlees.minotes.notes.Folder
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.notes.NotesStore
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import com.jlees.minotes.ui.common.Notices
import com.jlees.minotes.ui.common.Undo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The full-screen sheets over the home page. */
enum class Sheet { NONE, SETTINGS, TRASH }

/** The dialogs mi. Notes asks with. */
sealed interface Ask {
    data class RenameNote(val id: String) : Ask
    /** [thenMove]: the new folder takes this note in once made (from Move to Folder). */
    data class NewFolder(val thenMove: String? = null) : Ask
    data class RenameFolder(val id: String) : Ask
    data class MoveNote(val id: String) : Ask
    data class Alert(val title: String, val message: String, val confirm: String? = null, val destructive: Boolean = false, val onConfirm: () -> Unit = {}) : Ask
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JsonStore(File(app.filesDir, "data"))
    val notes = NotesStore(store)

    var settings by mutableStateOf(Settings()); private set
    private val settingsSaver = Saver(300, { settings }) { store.write("settings", Settings.serializer(), it) }
    var ready by mutableStateOf(false); private set

    /** The folder the home page shows; null is All Notes. */
    var folder by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    /** The note open in the editor, and the card it grew out of (window px), to shrink back into. */
    var openId by mutableStateOf<String?>(null); private set
    var openFrom by mutableStateOf<Rect?>(null); private set
    var sheet by mutableStateOf(Sheet.NONE)
    var settingsSearch by mutableStateOf("")
    var ask by mutableStateOf<Ask?>(null)
    /** The note whose menu is open (long press on its card, or ⋮ in the editor). */
    var menuFor by mutableStateOf<String?>(null)
    /** Where each card is on screen (window px), so the editor can grow out of it. */
    val cardBounds = androidx.compose.runtime.mutableStateMapOf<String, Rect>()

    private var started = false

    /** Loads everything (once per process: the ViewModel outlives a rotation). */
    fun start(shared: String?) {
        if (started) { shared?.let { receiveShared(it) }; return }
        started = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settings = store.read("settings", Settings.serializer())?.sanitized()?.migrated() ?: Settings()
                notes.load()
            }
            folder = settings.lastFolder?.takeIf { id -> notes.folders.any { it.id == id } }
            (notes.savers + settingsSaver).forEach { it.launchIn(viewModelScope) }
            ready = true
            shared?.let { receiveShared(it) }
            withContext(Dispatchers.IO) { NoteImages.clean(getApplication(), notes.notes) }
        }
    }

    fun updateSettings(f: Settings.() -> Settings) { settings = settings.f() }

    fun showFolder(id: String?) { folder = id; if (settings.lastFolder != id) updateSettings { copy(lastFolder = id) } }

    // ---- notes

    /** A new note in the folder showing, opened. [checklist]: it starts as a checklist. */
    fun newNote(checklist: Boolean = false, from: Rect? = null) {
        val body = if (checklist) RichText("", listOf(Span(0, 0, SpanType.PARA, ListKind.CHECK.name.lowercase()))) else RichText()
        open(notes.create(folder, body).id, from)
    }

    fun open(id: String, from: Rect? = cardBounds[id]) { menuFor = null; openFrom = from; openId = id }

    /** Leaving the editor: a note left empty isn't kept. */
    fun closeNote() {
        val id = openId ?: return
        openId = null
        notes[id]?.takeIf { it.isEmpty }?.let { notes.deleteForever(it.id) }
    }

    fun receiveShared(text: String) {
        val lines = text.trim().lines()
        val title = lines.first().take(80).takeIf { lines.size > 1 } ?: ""
        val body = if (title.isEmpty()) text.trim() else lines.drop(1).joinToString("\n").trim()
        val n = notes.create(folder, RichText(body), title)
        open(n.id, null)
    }

    fun deleteNote(id: String) {
        val n = notes[id] ?: return
        val index = notes.notes.indexOf(n)
        if (openId == id) openId = null
        menuFor = null
        if (n.isEmpty) { notes.deleteForever(id); return }
        notes.delete(id)
        Notices.current = Undo("Note deleted") { notes.put(n, index) }
    }

    fun togglePin(id: String) {
        val n = notes[id] ?: return
        notes.setPinned(id, !n.pinned)
        Notices.show(if (n.pinned) "Unpinned" else "Pinned", done = !n.pinned)
    }

    fun duplicate(id: String) { notes.duplicate(id)?.let { Notices.show("Duplicated", done = true) }; menuFor = null }

    fun moveTo(id: String, folderId: String?) {
        val before = notes[id]?.folder
        notes.move(id, folderId)
        val name = notes.folders.firstOrNull { it.id == folderId }?.name ?: "All Notes"
        Notices.current = Undo("Moved to $name") { notes.move(id, before) }
    }

    fun deleteForever(id: String) { NoteImages.delete(getApplication(), notes.deleteForever(id)) }

    fun emptyTrash() {
        ask = Ask.Alert("Empty Recently Deleted?", "These notes will be deleted for good. This can't be undone.", "Empty", destructive = true) {
            NoteImages.delete(getApplication(), notes.emptyTrash())
        }
    }

    // ---- folders

    fun deleteFolder(id: String) {
        val inIt = notes.notes.filter { it.folder == id }.map { it.id }
        val (f, i) = notes.deleteFolder(id) ?: return
        if (folder == id) showFolder(null)
        Notices.current = Undo("Folder deleted") { notes.restoreFolder(f, i, inIt) }
    }

    // ---- backup

    fun backup() = Backup(settings, notes.notes, notes.folders)

    /** A backup read back: its notes are added to yours (one you already have stays as it is); its settings replace yours. */
    fun restore(b: Backup) {
        val before = notes.notes.size
        notes.addAll(b.notes, b.folders)
        settings = b.settings.importedOver(settings)
        val added = notes.notes.size - before
        Notices.show(if (added == 1) "Restored 1 note" else "Restored $added notes", done = true)
    }

    fun importNotes(added: List<Note>) {
        notes.addAll(added.map { it.copy(folder = folder) }, emptyList<Folder>())
        Notices.show(if (added.size == 1) "Imported 1 note" else "Imported ${added.size} notes", done = true)
    }

    fun resetSettings() { settings = Settings(walkthroughSeen = true); Notices.show("Settings reset", done = true) }

    fun saveNow() { (notes.savers + settingsSaver).forEach { it.writeNow() } }
}
