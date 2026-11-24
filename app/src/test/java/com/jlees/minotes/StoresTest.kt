package com.jlees.minotes

import com.jlees.minotes.data.JsonStore
import com.jlees.minotes.notes.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class StoresTest {
    private fun store() = NotesStore(JsonStore(Files.createTempDirectory("notes").toFile()))

    @Test fun reorderMovesBeforeOrAfter() {
        val s = store()
        val c = s.create(null); val b = s.create(null); val a = s.create(null) // newest first: a, b, c
        s.reorder(a.id, c.id, after = true)
        assertEquals(listOf(b.id, c.id, a.id), s.notes.map { it.id })
        s.reorder(a.id, b.id, after = false)
        assertEquals(listOf(a.id, b.id, c.id), s.notes.map { it.id })
    }

    @Test fun deleteUnpinsAndRestoreBringsBack() {
        val s = store()
        val n = s.create(null); s.setPinned(n.id, true)
        s.delete(n.id)
        assertEquals(false, s[n.id]!!.pinned)
        s.restore(n.id)
        assertNull(s[n.id]!!.deletedAt)
    }

    @Test fun deletingAFolderKeepsItsNotesAndUndoPutsThemBack() {
        val s = store()
        val f = s.addFolder("Work")
        val n = s.create(f.id)
        val (gone, at) = s.deleteFolder(f.id)!!
        assertNull(s[n.id]!!.folder)
        s.restoreFolder(gone, at, listOf(n.id))
        assertEquals(f.id, s[n.id]!!.folder)
    }

    @Test fun savedNotesLoadBack() {
        val dir = Files.createTempDirectory("notes").toFile()
        val s = NotesStore(JsonStore(dir))
        val n = s.create(null, title = "Hello")
        s.savers.forEach { it.writeNow() }
        val again = NotesStore(JsonStore(dir)).apply { load() }
        assertEquals("Hello", again[n.id]?.title)
    }

    @Test fun backupAddsOnlyNewNotes() {
        val s = store()
        val mine = s.create(null, title = "mine")
        s.addAll(listOf(mine.copy(title = "theirs"), mine.copy(id = "new", title = "new")), emptyList())
        assertEquals("mine", s[mine.id]?.title)
        assertEquals(2, s.notes.size)
    }
}
