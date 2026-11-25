// Holds the stores and the app-wide UI state (which note is open, which sheet, which dialog), and coordinates between
// them. Note and folder changes themselves are NotesStore's; this decides when they happen (with Undo, mostly).
package com.jlees.minotes

import com.jlees.minotes.notes.expiredWords
import com.jlees.minotes.notes.expiredCount
import com.jlees.minotes.data.KeepDeleted
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

/** The saved-state key for the open note (see openId). */
private const val OPEN_NOTE = "openNote"

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

class NotesViewModel(app: Application, private val saved: androidx.lifecycle.SavedStateHandle) : AndroidViewModel(app) {
    private val store = JsonStore(File(app.filesDir, "data"))
    val notes = NotesStore(store)

    var settings by mutableStateOf(Settings()); private set
    private val settingsSaver = Saver(300, { settings }) { store.write("settings", Settings.serializer(), it) }
    var ready by mutableStateOf(false); private set

    /** The folder the home page shows; null is All Notes. */
    var folder by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    /** The note open in the editor, and the card it grew out of (window px), to shrink back into. */
    // kept in the saved state too: if Android closes the app in the background (a photo being taken for a scan inside
    // the note), the note opens again where it was, and the scan's answer has somewhere to go
    private var openIdState by mutableStateOf<String?>(saved.get<String>(OPEN_NOTE))
    var openId: String?
        get() = openIdState
        private set(v) { openIdState = v; saved[OPEN_NOTE] = v }
    var openFrom by mutableStateOf<Rect?>(null); private set
    var sheet by mutableStateOf(Sheet.NONE)
    var settingsSearch by mutableStateOf("")
    var ask by mutableStateOf<Ask?>(null)
    /** The note whose menu is open (long press on its card, or ⋮ in the editor). */
    var menuFor by mutableStateOf<String?>(null)
    val updater = com.jlees.minotes.update.Updater(this)
    val whatsNew = com.jlees.minotes.update.WhatsNew(this)

    /** Where each card is on screen (window px), so the editor can grow out of it. */
    val cardBounds = androidx.compose.runtime.mutableStateMapOf<String, Rect>()
    /** Where the home bar's Pill is (window px): Settings' and Recently Deleted's search Pills grow out of it. */
    var barPill: Rect? = null
    /** Where the open note's toolbar Pill is (window px), when the note is fully open: it turns into [barPill] on the way out. */
    var toolPill: () -> Rect? = { null }
    /** How open the note is (0 the home page … 1 the note filling the screen), read while it grows or shrinks. */
    var noteOpen: () -> Float = { 0f }
    /** The note open over the home page, or growing out of / shrinking back into its card: that card hides meanwhile. */
    var shownNote by mutableStateOf<String?>(null)

    private var started = false

    /** Loads everything (once per process: the ViewModel outlives a rotation). */
    fun start(shared: String?) {
        if (started) { shared?.let { receiveShared(it) }; return }
        started = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settings = store.read("settings", Settings.serializer())?.sanitized()?.migrated() ?: Settings()
                notes.load(keepDays = settings.keepDeleted.days)
            }
            folder = settings.lastFolder?.takeIf { id -> notes.folders.any { it.id == id } }
            if (openId != null && notes[openId] == null) openId = null // (deleted since, by another copy of the data)
            (notes.savers + settingsSaver).forEach { it.launchIn(viewModelScope) }
            ready = true
            if (store.keptAside) Notices.show("Couldn't read some saved data. It was kept aside", long = true)
            whatsNew.checkAtLaunch()
            updater.checkQuietly()
            shared?.let { receiveShared(it) }
            withContext(Dispatchers.IO) {
                NoteImages.clean(getApplication(), notes.notes)
                // an update downloaded last time has been installed (or given up on) by now: its APK needn't stay
                File(getApplication<Application>().cacheDir, "updates").deleteRecursively()
            }
        }
    }

    fun updateSettings(f: Settings.() -> Settings) {
        val before = settings.keepDeleted
        settings = settings.f()
        if (settings.keepDeleted != before) notes.purge(settings.keepDeleted.days)
    }

    // ---- Keep Deleted Notes: a shorter period deletes older notes for good, so whatever shortens it says so first

    /**
     * What a Keep Deleted Notes of [keep] would delete for good now, among your notes and a backup's [extra] ones, as the
     * questions say it ("3 notes deleted more than 1 week ago will be deleted for good."); null when it deletes none.
     */
    fun expiringSentence(keep: KeepDeleted, extra: List<Note> = emptyList()): String? =
        expiredCount(notes.notes + extra.filter { notes[it.id] == null }, System.currentTimeMillis(), keep.days)
            .takeIf { it > 0 }?.let { "${expiredWords(it, keep.label)} will be deleted for good." }

    /** Settings › Recently Deleted › Keep Deleted Notes: at once, or after asking when it would delete notes. */
    fun setKeepDeleted(keep: KeepDeleted) {
        val gone = expiringSentence(keep) ?: return updateSettings { copy(keepDeleted = keep) }
        ask = Ask.Alert("Are you sure?", "$gone This can't be undone.", "Delete", destructive = true) {
            updateSettings { copy(keepDeleted = keep) }
        }
    }

    fun showFolder(id: String?) { folder = id; if (settings.lastFolder != id) updateSettings { copy(lastFolder = id) } }

    // ---- notes

    /** A new note in the folder showing, opened. [checklist]: it starts as a checklist. */
    fun newNote(checklist: Boolean = false, from: Rect? = null) {
        val body = if (checklist) RichText("", listOf(Span(0, 0, SpanType.PARA, ListKind.CHECK.name.lowercase()))) else RichText()
        open(notes.create(folder, body).id, from)
    }

    fun open(id: String, from: Rect? = cardBounds[id]) { menuFor = null; openFrom = from; openId = id }

    /** Leaving the editor. Every note is kept, blank ones too (they show as "Untitled"). */
    fun closeNote() { openId = null }

    /** A scan, or text shared from another app, as a new note in the folder showing, opened: its first line the title
     *  when it's plain, short and more follow. */
    fun newNoteFrom(rt: RichText) {
        val nl = rt.text.indexOf('\n')
        val titled = nl in 1..80 && rt.spans.none { it.type == SpanType.PARA && it.start == 0 }
        val body = if (!titled) rt else RichText(rt.text.substring(nl + 1), rt.spans.map { it.copy(start = it.start - nl - 1, end = it.end - nl - 1) })
        open(notes.create(folder, body, if (titled) rt.text.substring(0, nl) else "").id, null)
    }

    fun receiveShared(text: String) = newNoteFrom(RichText(text.trim()))

    fun deleteNote(id: String) {
        val n = notes[id] ?: return
        val index = notes.notes.indexOf(n)
        if (openId == id) openId = null
        menuFor = null
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
        ask = Ask.Alert("Are you sure?", "Every note in Recently Deleted will be deleted for good. This can't be undone.", "Empty", destructive = true) {
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
        val added = notes.notes.size - before
        settings = b.settings.importedOver(settings)
        notes.purge(settings.keepDeleted.days) // what the import's question said would go, goes now (not silently at the next start)
        Notices.show(if (added == 1) "Imported 1 note" else "Imported $added notes", done = true)
    }

    /** Notes from Import Notes, into the folder showing; [skipped] ("1 file was too big") ends the notice when set. */
    fun importNotes(added: List<Note>, skipped: String = "") {
        notes.addAll(added.map { it.copy(folder = folder) }, emptyList<Folder>())
        val imported = if (added.size == 1) "Imported 1 note" else "Imported ${added.size} notes"
        Notices.show(if (skipped.isEmpty()) imported else "$imported. $skipped", done = skipped.isEmpty())
    }

    /** Back to the defaults; the hidden state (the walkthrough seen, this phone's update and What's New marks, the folder showing) stays. */
    fun resetSettings() {
        val s = settings
        settings = Settings(walkthroughSeen = s.walkthroughSeen, lastFolder = s.lastFolder, lastUpdateCheck = s.lastUpdateCheck, lastSeenVersion = s.lastSeenVersion)
        notes.purge(settings.keepDeleted.days) // a shorter default period: what the question said would go, goes now
        Notices.show("Settings reset", done = true)
    }

    /**
     * Wipe All Data: every note, folder, picture and setting is deleted, and the app starts again fresh (as just
     * installed). It quits at once after deleting, so nothing in memory can be saved back.
     */
    fun wipeAll(activity: android.app.Activity) {
        val app = getApplication<Application>()
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            activity.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }

    /** The open note's editor hands over what was typed in its last moment (set by EditorScreen while a note is open). */
    var flushEditor: () -> Unit = {}

    /** Leaving the app: the editor's last keystrokes first, then whatever changed since it was last on disk. */
    fun saveNow() { flushEditor(); (notes.savers + settingsSaver).forEach { it.writeNow() } }
}
