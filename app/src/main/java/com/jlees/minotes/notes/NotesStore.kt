// The notes and folders: one store for both, since folders exist only to hold notes. Saved as notes.json and
// folders.json; images live as files in images/ (NoteImages).
package com.jlees.minotes.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jlees.minotes.data.JsonStore
import com.jlees.minotes.data.Saver
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

class NotesStore(private val store: JsonStore) {
    /** Every note, live and deleted, in manual order (the order they were dragged into). */
    var notes by mutableStateOf<List<Note>>(emptyList()); private set
    var folders by mutableStateOf<List<Folder>>(emptyList()); private set

    val savers = listOf(
        // every note in one file: written at most every 1.5 s while typing (leaving the app writes it at once)
        Saver(1500, { notes }) { store.write("notes", ListSerializer(Note.serializer()), it) },
        Saver(400, { folders }) { store.write("folders", ListSerializer(Folder.serializer()), it) },
    )

    /** Off the main thread at start. Notes deleted more than [keepDays] ago are let go here. */
    fun load(now: Long = System.currentTimeMillis(), keepDays: Int = TRASH_DAYS) {
        notes = purgeExpired(store.read("notes", ListSerializer(Note.serializer())).orEmpty(), now, keepDays)
        folders = store.read("folders", ListSerializer(Folder.serializer())).orEmpty()
    }

    operator fun get(id: String?) = notes.firstOrNull { it.id == id }

    /** A new, empty note at the top, in [folder]. */
    fun create(folder: String?, body: RichText = RichText(), title: String = ""): Note {
        val now = System.currentTimeMillis()
        val n = Note(UUID.randomUUID().toString(), title, body, folder, created = now, edited = now)
        notes = listOf(n) + notes
        return n
    }

    /** Saves an edit of note [id]: its title and body, and the edited time. */
    fun edit(id: String, title: String, body: RichText) = update(id) {
        if (it.title == title && it.body == body) it else it.copy(title = title, body = body, edited = System.currentTimeMillis())
    }

    fun setPinned(id: String, pinned: Boolean) = update(id) { it.copy(pinned = pinned) }
    fun setColor(id: String, color: NoteColor) = update(id) { it.copy(color = color) }
    fun rename(id: String, title: String) = update(id) { it.copy(title = title.trim(), edited = System.currentTimeMillis()) }
    fun move(id: String, folder: String?) = update(id) { it.copy(folder = folder) }

    /** To Recently Deleted (pins come off: a restored note comes back unpinned). */
    fun delete(id: String) = update(id) { it.copy(deletedAt = System.currentTimeMillis(), pinned = false) }
    fun restore(id: String) = update(id) { it.copy(deletedAt = null) }
    /** Gone for good. Returns the images it used, for the caller to remove. */
    fun deleteForever(id: String): List<String> { val imgs = get(id)?.body?.images.orEmpty(); notes = notes.filterNot { it.id == id }; return imgs }
    fun emptyTrash(): List<String> { val gone = notes.filter { it.deletedAt != null }; notes = notes - gone.toSet(); return gone.flatMap { it.body.images } }

    /** Lets go of notes deleted more than [keepDays] ago (Keep Deleted Notes was shortened). */
    fun purge(keepDays: Int, now: Long = System.currentTimeMillis()) { notes = purgeExpired(notes, now, keepDays) }

    /** Puts a note back exactly as it was (Undo). */
    fun put(n: Note, index: Int = 0) { notes = notes.filterNot { it.id == n.id }.toMutableList().apply { add(index.coerceIn(0, size), n) } }

    fun duplicate(id: String): Note? {
        val src = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = src.copy(id = UUID.randomUUID().toString(), title = src.title.ifBlank { "" }.let { if (it.isEmpty()) it else "$it (copy)" }, pinned = false, created = now, edited = now)
        notes = notes.toMutableList().apply { add(indexOfFirst { it.id == id } + 1, copy) }
        return copy
    }

    /** Moves note [id] next to [targetId] in manual order: just after it with [after], else just before. */
    fun reorder(id: String, targetId: String, after: Boolean) {
        val n = get(id) ?: return
        if (id == targetId) return
        val rest = notes.filterNot { it.id == id }.toMutableList()
        val t = rest.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: return
        rest.add(if (after) t + 1 else t, n)
        notes = rest
    }

    /** Notes added by a backup or import (new ids are kept apart from yours: one already here is left as it is). */
    fun addAll(added: List<Note>, addedFolders: List<Folder>) {
        val have = notes.map { it.id }.toSet()
        notes = notes + added.filterNot { it.id in have }
        val haveF = folders.map { it.id }.toSet()
        folders = folders + addedFolders.filterNot { it.id in haveF }
    }

    // ---- folders
    fun addFolder(name: String): Folder { val f = Folder(UUID.randomUUID().toString(), name.trim()); folders = folders + f; return f }
    fun renameFolder(id: String, name: String) { folders = folders.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    /** Removes a folder; its notes stay, in no folder. Returns what Undo needs. */
    fun deleteFolder(id: String): Pair<Folder, Int>? {
        val i = folders.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val f = folders[i]
        folders = folders - f
        notes = notes.map { if (it.folder == id) it.copy(folder = null) else it }
        return f to i
    }
    fun restoreFolder(f: Folder, index: Int, noteIds: List<String>) {
        folders = folders.toMutableList().apply { add(index.coerceIn(0, size), f) }
        notes = notes.map { if (it.id in noteIds) it.copy(folder = f.id) else it }
    }

    private fun update(id: String, f: (Note) -> Note) { notes = notes.map { if (it.id == id) f(it) else it } }
}
