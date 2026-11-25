package com.jlees.minotes

import com.jlees.minotes.update.NoteBlock
import com.jlees.minotes.update.Updates
import com.jlees.minotes.update.WhatsNewStep
import com.jlees.minotes.update.parseReleaseNotes
import com.jlees.minotes.update.whatsNewStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun versionsCompareByNumberNotText() {
        assertTrue(Updates.isNewer("v1.10.0", "1.9.3"))
        assertTrue(Updates.isNewer("1.0.1", "1.0.0"))
        assertFalse(Updates.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Updates.isNewer("1.0.0-beta", "1.0.0"))
        assertFalse(Updates.isNewer("0.9", "1.0.0-dev"))
    }

    @Test fun launchCheckWaitsTwelveHoursAfterNothingNew() {
        val h = 3_600_000L
        assertFalse(Updates.dueForCheck(now = 11 * h, lastNothingNew = 0))
        assertTrue(Updates.dueForCheck(now = 12 * h, lastNothingNew = 0))
    }

    @Test fun whatsNewShowsAfterUpdatesNotNewInstalls() {
        assertEquals(WhatsNewStep.RECORD, whatsNewStep(lastSeen = 0, current = 101, walkthroughSeen = false)) // a new install
        assertEquals(WhatsNewStep.SHOW, whatsNewStep(lastSeen = 100, current = 101, walkthroughSeen = true))
        assertEquals(WhatsNewStep.SHOW, whatsNewStep(lastSeen = 0, current = 101, walkthroughSeen = true)) // from before What's New
        assertEquals(WhatsNewStep.NONE, whatsNewStep(lastSeen = 101, current = 101, walkthroughSeen = true))
    }

    @Test fun releaseNotesBecomeBlocksForPeople() {
        val md = "# mi. 1.1.0: pictures you can resize\n\nHold a picture to resize it.\nIt keeps its size.\n\n## Editing\n- **Bold** stays\n- [Links](https://x) keep their text\n\n[All changes since v1.0.0](https://github.com/a/b/compare/v1.0.0...v1.1.0)\napp/build/outputs/apk/release/mi-notes-1.1.0-release.apk"
        val n = parseReleaseNotes("1.1.0", md)
        assertEquals("pictures you can resize", n.tagline)
        assertEquals(listOf(
            NoteBlock.Paragraph("Hold a picture to resize it. It keeps its size."),
            NoteBlock.Heading("Editing"),
            NoteBlock.Bullet("**Bold** stays"),
            NoteBlock.Bullet("Links keep their text"),
        ), n.blocks)
    }
}
