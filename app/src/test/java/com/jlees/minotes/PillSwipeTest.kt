package com.jlees.minotes

import com.jlees.minotes.editor.pillSwipeActs
import com.jlees.minotes.editor.pillSwipeContentAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillSwipeTest {
    @Test fun swipeActsPast28dpOrOnAFlick() {
        assertFalse(pillSwipeActs(20f, 0f))
        assertTrue(pillSwipeActs(-30f, 0f))
        assertTrue(pillSwipeActs(10f, 0.3f))  // short, but flicked
        assertFalse(pillSwipeActs(6f, 1f))    // a flick under 8dp is a tap that slipped
    }

    @Test fun contentFadesOnlyWhereThereIsSomewhereToGo() {
        assertEquals(0f, pillSwipeContentAlpha(40f, canRight = true, canLeft = true))
        assertEquals(0.5f, pillSwipeContentAlpha(-16f, canRight = true, canLeft = true))
        assertEquals(1f, pillSwipeContentAlpha(40f, canRight = false, canLeft = true))
    }
}

class SizeStepsTest {
    @Test fun smallSizesStepByOneThenCommonOnes() {
        assertEquals(13, com.jlees.minotes.editor.nextSize(12))
        assertEquals(20, com.jlees.minotes.editor.nextSize(18))
        assertEquals(28, com.jlees.minotes.editor.nextSize(25)) // a typed size steps to the next common one
        assertEquals(24, com.jlees.minotes.editor.prevSize(28))
        assertEquals(68, com.jlees.minotes.editor.nextSize(68)) // the largest stays
        assertEquals(9, com.jlees.minotes.editor.prevSize(9))   // and so does the smallest
    }
}
