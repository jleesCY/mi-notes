package com.jlees.minotes

import com.jlees.minotes.data.Settings
import com.jlees.minotes.data.importedOver
import com.jlees.minotes.data.pans
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

    @Test fun settingsRepairDamagedValues() {
        val s = Settings(cornerRadius = 5, fontScale = 9f, previewLines = 0).sanitized()
        assertEquals(28, s.cornerRadius); assertEquals(1.2f, s.fontScale); assertEquals(2, s.previewLines)
    }

    @Test fun wordsAreCounted() = assertEquals(3, wordCount("one  two\n$IMAGE_CHAR three"))

    @Test fun oldGridSettingLoadsAsStaggered() {
        val s = com.jlees.minotes.data.json.decodeFromString(Settings.serializer(), """{"layout":"grid","hidePillOnScroll":true}""")
        assertEquals(com.jlees.minotes.data.NotesLayout.STAGGERED, s.layout)
    }

    @Test fun recentlyDeletedKeepsAsLongAsTheSettingSays() {
        val day = 86_400_000L
        val notes = listOf(note("a", deletedAt = 0), note("b", deletedAt = 60 * day))
        assertEquals(listOf("b"), purgeExpired(notes, 65 * day, days = 7).map { it.id })
        assertEquals(listOf("a", "b"), purgeExpired(notes, 65 * day, days = 90).map { it.id })
        assertEquals(85, daysLeft(notes[1], 65 * day, days = 90))
    }

    @Test fun panningIsPerPlaceAndOnlyWhenOn() {
        val off = Settings()
        assertEquals(false, off.pans(com.jlees.minotes.data.PanArea.SETTINGS))
        val on = Settings(panLongText = true)
        assertEquals(true, on.pans(com.jlees.minotes.data.PanArea.SETTINGS))
        assertEquals(true, on.pans(com.jlees.minotes.data.PanArea.NOTE_MENU))
        assertEquals(false, on.pans(com.jlees.minotes.data.PanArea.NOTES)) // lists start off
    }

    @Test fun aBackupKeepsWhatBelongsToThisPhone() {
        val mine = Settings(lastFolder = "f", lastUpdateCheck = 5, lastSeenVersion = 100)
        val theirs = Settings(lastFolder = "g", lastUpdateCheck = 9, lastSeenVersion = 90, accentColor = "#34C759")
        val result = theirs.importedOver(mine)
        assertEquals("#34C759", result.accentColor)
        assertEquals(Triple("f", 5L, 100), Triple(result.lastFolder, result.lastUpdateCheck, result.lastSeenVersion))
    }

    @Test fun aShorterKeepPeriodCountsWhatItWouldDelete() {
        val day = 86_400_000L; val now = 100 * day
        val notes = listOf(
            com.jlees.minotes.notes.Note("live"),
            com.jlees.minotes.notes.Note("2 days", deletedAt = now - 2 * day),
            com.jlees.minotes.notes.Note("20 days", deletedAt = now - 20 * day),
            com.jlees.minotes.notes.Note("60 days", deletedAt = now - 60 * day),
        )
        assertEquals(2, com.jlees.minotes.notes.expiredCount(notes, now, 7))
        assertEquals(1, com.jlees.minotes.notes.expiredCount(notes, now, 30))
        assertEquals(0, com.jlees.minotes.notes.expiredCount(notes, now, 90))
        // the same line purgeExpired draws: what it counts is exactly what would go
        assertEquals(notes.size - 2, com.jlees.minotes.notes.purgeExpired(notes, now, 7).size)
        assertEquals("1 note deleted more than 30 days ago", com.jlees.minotes.notes.expiredWords(1, "30 Days"))
        assertEquals("2 notes deleted more than 1 week ago", com.jlees.minotes.notes.expiredWords(2, "1 Week"))
    }
}
