package com.jlees.minotes

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** NotesViewModel's own decisions: what survives Android closing the app, and how text from outside becomes a note. */
@RunWith(RobolectricTestRunner::class)
class ViewModelTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun theOpenNoteSurvivesAndroidClosingTheApp() {
        val saved = SavedStateHandle(mapOf("openNote" to "abc"))
        val vm = NotesViewModel(app, saved)
        assertEquals("abc", vm.openId) // the note open before is open again
        vm.closeNote()
        assertNull(saved.get<String>("openNote"))
        vm.open("xyz", null)
        assertEquals("xyz", saved.get<String>("openNote"))
    }

    @Test fun aScanBecomesANoteTitledByItsFirstPlainLine() {
        val vm = NotesViewModel(app, SavedStateHandle())
        vm.newNoteFrom(RichText("Groceries\nMilk\nEggs", listOf(Span(10, 14, SpanType.PARA, "check"), Span(15, 19, SpanType.PARA, "check", checked = true))))
        val n = vm.notes[vm.openId]!!
        assertEquals("Groceries", n.title)
        assertEquals("Milk\nEggs", n.body.text)
        assertEquals(listOf(0 to 4, 5 to 9), n.body.spans.map { it.start to it.end }) // the list moved up with the text
        assertEquals(listOf(false, true), n.body.spans.map { it.checked })
    }

    @Test fun aScanThatStartsWithAListItemHasNoTitle() {
        val vm = NotesViewModel(app, SavedStateHandle())
        val rt = RichText("Milk\nEggs", listOf(Span(0, 4, SpanType.PARA, "bullet"), Span(5, 9, SpanType.PARA, "bullet")))
        vm.newNoteFrom(rt)
        val n = vm.notes[vm.openId]!!
        assertEquals("", n.title)
        assertEquals(rt, n.body)
    }

    private fun vmWithTrash(daysAgo: Int): NotesViewModel {
        val vm = NotesViewModel(app, SavedStateHandle())
        vm.notes.addAll(listOf(com.jlees.minotes.notes.Note("old", deletedAt = System.currentTimeMillis() - daysAgo * 86_400_000L)), emptyList())
        vm.updateSettings { copy(keepDeleted = com.jlees.minotes.data.KeepDeleted.QUARTER) }
        return vm
    }

    @Test fun shorteningKeepDeletedNotesAsksBeforeDeletingAnything() {
        val vm = vmWithTrash(daysAgo = 20)
        vm.setKeepDeleted(com.jlees.minotes.data.KeepDeleted.WEEK)
        val ask = vm.ask as Ask.Alert
        assertEquals(com.jlees.minotes.data.KeepDeleted.QUARTER, vm.settings.keepDeleted) // nothing changed yet
        assertEquals(1, vm.notes.notes.size)
        assertEquals(true, ask.message.startsWith("1 note deleted more than 1 week ago"))
        ask.onConfirm()
        assertEquals(com.jlees.minotes.data.KeepDeleted.WEEK, vm.settings.keepDeleted)
        assertEquals(0, vm.notes.notes.size)
    }

    @Test fun aPeriodThatDeletesNothingAppliesAtOnce() {
        val vm = vmWithTrash(daysAgo = 2)
        vm.setKeepDeleted(com.jlees.minotes.data.KeepDeleted.WEEK)
        assertNull(vm.ask)
        assertEquals(com.jlees.minotes.data.KeepDeleted.WEEK, vm.settings.keepDeleted)
        assertEquals(1, vm.notes.notes.size)
    }

    @Test fun resettingDeletesNowWhatItsShorterDefaultWould() {
        val vm = vmWithTrash(daysAgo = 60)
        assertEquals("1 note deleted more than 30 days ago will be deleted for good.", vm.expiringSentence(com.jlees.minotes.data.Settings().keepDeleted)) // what the reset question says
        vm.resetSettings()
        assertEquals(0, vm.notes.notes.size) // gone now, as it said, not silently at the next start
    }
}
