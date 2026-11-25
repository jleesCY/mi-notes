package com.jlees.minotes

import android.content.Intent
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.ShareNote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Sharing a note (notes/ShareNote.kt): text for most, a file for the very long ones Android won't hand over as text. */
@RunWith(RobolectricTestRunner::class)
class ShareNoteTest {
    // shared from the open screen, as the note menu does
    private val app = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
    private fun sharedIntent(): Intent {
        val chooser = shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION") return chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    }

    @Test fun aNoteIsSharedAsItsText() {
        runBlocking { ShareNote.asText(app, Note("n", "Groceries", RichText("Milk"))) }
        val send = sharedIntent()
        assertEquals("text/plain", send.type)
        assertNotNull(send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun aVeryLongNoteIsSharedAsAFileInstead() {
        // as text it would pass Android's ~1 MB limit on handing data to another app, and crash
        runBlocking { ShareNote.asText(app, Note("n", "Big", RichText("x".repeat(ShareNote.MAX_SHARED_TEXT + 1)))) }
        val send = sharedIntent()
        assertEquals("text/markdown", send.type)
        assertNull(send.getStringExtra(Intent.EXTRA_TEXT))
        @Suppress("DEPRECATION") assertNotNull(send.getParcelableExtra(Intent.EXTRA_STREAM))
    }

    @Test fun aNoteTooLongForTheClipboardIsntCopied() {
        val clip = app.getSystemService(android.content.ClipboardManager::class.java)
        assertEquals(true, ShareNote.copy(app, Note("n", "Short", RichText("Milk"))))
        assertEquals("Short\n\nMilk", clip.primaryClip!!.getItemAt(0).text.toString())
        // past the limit, Android would refuse the hand-over (and crash the app): nothing is copied, and it says so
        assertEquals(false, ShareNote.copy(app, Note("n", "Big", RichText("x".repeat(ShareNote.MAX_SHARED_TEXT + 1)))))
        assertEquals("Short\n\nMilk", clip.primaryClip!!.getItemAt(0).text.toString())
    }
}
