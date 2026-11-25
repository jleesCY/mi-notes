package com.jlees.minotes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.jlees.minotes.ui.opennessFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jlees.minotes.data.migrated

class NoteShrinkTest {
    private val page = IntSize(1000, 2000)
    private val card = Rect(40f, 600f, 480f, 900f)

    @Test fun theNoteStaysOpenUntilTheFingerMoves() = assertEquals(1f, opennessFor(2000f, card, page), 0.001f)

    @Test fun theNoteIsClosedOnceItsBottomReachesTheCards() = assertEquals(0f, opennessFor(card.bottom, card, page), 0.001f)

    @Test fun theBottomEdgeFollowsTheFingerOneToOne() {
        // halfway up between the screen's bottom and the card's, the note sits where that bottom edge is: open some way,
        // and further up means less open
        val a = opennessFor(1700f, card, page); val b = opennessFor(1400f, card, page)
        assertTrue(a in 0f..1f && b in 0f..1f && b < a)
    }
}

class SettingsMigrationTest {
    @Test fun oldDefaultStaggeredMovesToGrid() =
        assertEquals(com.jlees.minotes.data.NotesLayout.GRID, com.jlees.minotes.data.Settings(layout = com.jlees.minotes.data.NotesLayout.STAGGERED, settingsVersion = 0).migrated().layout)

    @Test fun aListPickedBeforeStaysAList() =
        assertEquals(com.jlees.minotes.data.NotesLayout.LIST, com.jlees.minotes.data.Settings(layout = com.jlees.minotes.data.NotesLayout.LIST, settingsVersion = 0).migrated().layout)

    @Test fun staggeredPickedNowStays() =
        assertEquals(com.jlees.minotes.data.NotesLayout.STAGGERED, com.jlees.minotes.data.Settings(layout = com.jlees.minotes.data.NotesLayout.STAGGERED).migrated().layout)
}
