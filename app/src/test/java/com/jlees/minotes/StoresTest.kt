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

    @Test fun shorteningKeepDeletedLetsOldNotesGo() {
        val s = store()
        val old = s.create(null); val recent = s.create(null)
        s.delete(old.id); s.delete(recent.id)
        val now = System.currentTimeMillis()
        s.put(s[old.id]!!.copy(deletedAt = now - 10 * 86_400_000L))
        s.purge(keepDays = 7, now = now)
        assertNull(s[old.id])
        assertEquals(recent.id, s[recent.id]?.id)
    }

    @Test fun anUnreadableFileIsKeptAsideNotOverwritten() {
        val dir = Files.createTempDirectory("notes").toFile()
        java.io.File(dir, "notes.json").writeText("[{ this isn't notes")
        val js = JsonStore(dir)
        val s = NotesStore(js)
        s.load()
        assertEquals(emptyList<Any>(), s.notes)
        assertEquals(true, js.keptAside)
        assertEquals(false, java.io.File(dir, "notes.json").exists()) // the next save can't write over it
        assertEquals("[{ this isn't notes", dir.listFiles()!!.single { it.name.startsWith("notes.json.broken-") }.readText())
    }

    @Test fun savingOnLeavingWritesOnlyWhatChanged() {
        var state = listOf("a"); var writes = 0
        val saver = com.jlees.minotes.data.Saver(1000, { state }) { writes++ }
        saver.writeNow(); assertEquals(1, writes)
        saver.writeNow(); assertEquals(1, writes) // unchanged since: nothing written
        state = listOf("a", "b")
        saver.writeNow(); assertEquals(2, writes)
    }

    @Test fun titlesBecomeSafeFileNames() {
        assertEquals("a-b- c", com.jlees.minotes.data.DataTransfer.safeName("a/b: c"))
        assertEquals("Untitled", com.jlees.minotes.data.DataTransfer.safeName("  "))
        assertEquals(60, com.jlees.minotes.data.DataTransfer.safeName("x".repeat(100)).length)
    }
}
