package com.jlees.minotes

import com.jlees.minotes.data.Settings
import com.jlees.minotes.data.SortOrder
import com.jlees.minotes.notes.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesRulesTest {
    private fun note(id: String, title: String = id, text: String = "", folder: String? = null, pinned: Boolean = false, edited: Long = 0, created: Long = 0, deletedAt: Long? = null, spans: List<Span> = emptyList()) =
        Note(id, title, RichText(text, spans), folder, pinned, created = created, edited = edited, deletedAt = deletedAt)

    @Test fun pinnedFirstThenManualOrder() {
        val notes = listOf(note("a"), note("b", pinned = true), note("c"), note("d", pinned = true))
        assertEquals(listOf("b", "d", "a", "c"), visibleNotes(notes, null, "", SortOrder.MANUAL).map { it.id })
    }

    @Test fun sortsByEditedCreatedAndTitle() {
        val notes = listOf(note("x", "banana", edited = 1, created = 3), note("y", "Apple", edited = 3, created = 1), note("z", "cherry", edited = 2, created = 2))
        assertEquals(listOf("y", "z", "x"), visibleNotes(notes, null, "", SortOrder.EDITED).map { it.id })
        assertEquals(listOf("x", "z", "y"), visibleNotes(notes, null, "", SortOrder.CREATED).map { it.id })
        assertEquals(listOf("y", "x", "z"), visibleNotes(notes, null, "", SortOrder.TITLE).map { it.id })
    }

    @Test fun filtersByFolderSearchAndTrash() {
        val notes = listOf(note("a", text = "buy milk", folder = "f"), note("b", "Milk run"), note("c", text = "milk", deletedAt = 5), note("d", text = "eggs", folder = "f"))
        assertEquals(listOf("a", "d"), visibleNotes(notes, "f", "", SortOrder.MANUAL).map { it.id })
        assertEquals(listOf("a", "b"), visibleNotes(notes, null, "MILK", SortOrder.MANUAL).map { it.id })
        assertEquals(listOf("c"), trashed(notes).map { it.id })
    }

    @Test fun trashKeepsThirtyDays() {
        val day = 86_400_000L
        val notes = listOf(note("old", deletedAt = 0), note("new", deletedAt = 25 * day), note("live"))
        assertEquals(listOf("new", "live"), purgeExpired(notes, 31 * day).map { it.id })
        assertEquals(24, daysLeft(notes[1], 31 * day))
        assertEquals(1, daysLeft(note("x", deletedAt = 0), 30 * day - 1))
    }

    @Test fun titleFallsBackToFirstLine() {
        assertEquals("Groceries", displayTitle(note("a", "", "\n  Groceries \nmilk")))
        assertEquals("Untitled", displayTitle(note("a", "", "")))
    }

    @Test fun previewWritesListMarkers() {
        val text = "Shopping\nmilk\neggs\none\ntwo\nnested\nthree\nplain"
        val p = paragraphs(text)
        fun para(i: Int, kind: String, level: Int = 0, checked: Boolean = false) = Span(p[i].first, p[i].second, SpanType.PARA, kind, level, checked)
        val n = note("a", "", text, spans = listOf(para(1, "check", checked = true), para(2, "check"), para(3, "number"), para(4, "number"), para(5, "number", 1), para(6, "number")))
        assertEquals(listOf("☑ milk", "☐ eggs", "1. one", "2. two", "  1. nested", "3. three", "plain"), previewLines(n, 10))
        assertEquals(1 to 2, checklistProgress(n.body))
        assertNull(checklistProgress(RichText("x")))
    }

    @Test fun plainTextKeepsBlankLinesAndTitle() {
        assertEquals("T\n\na\n\nb", plainText(note("x", "T", "a\n\nb")))
    }

    @Test fun foldersWrapRound() {
        val f = listOf(Folder("1", "One"), Folder("2", "Two"))
        assertEquals("1", nextFolder(f, null, true))
        assertNull(nextFolder(f, "2", true))
        assertEquals("2", nextFolder(f, null, false))
    }

    @Test fun settingsRepairDamagedValues() {
        val s = Settings(cornerRadius = 5, fontScale = 9f, previewLines = 0).sanitized()
        assertEquals(28, s.cornerRadius); assertEquals(1.2f, s.fontScale); assertEquals(2, s.previewLines)
    }

    @Test fun wordsAreCounted() = assertEquals(3, wordCount("one  two\n$IMAGE_CHAR three"))
}
