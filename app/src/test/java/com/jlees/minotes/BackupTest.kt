package com.jlees.minotes

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jlees.minotes.data.Backup
import com.jlees.minotes.data.DataTransfer
import com.jlees.minotes.data.ImportError
import com.jlees.minotes.data.NotesLayout
import com.jlees.minotes.data.Settings
import com.jlees.minotes.data.ThemeMode
import com.jlees.minotes.notes.Folder
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Backups (data/Backup.kt): what goes out comes back, and a file from anywhere can't do harm. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun file(name: String) = File(ctx.cacheDir, name).apply { parentFile?.mkdirs() }

    /** A backup file holding exactly [entries] (name to bytes). */
    private fun zip(vararg entries: Pair<String, ByteArray>): Uri {
        val f = file("crafted.minotesdata")
        ZipOutputStream(f.outputStream()).use { z -> for ((name, bytes) in entries) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return Uri.fromFile(f)
    }

    @Test fun aBackupComesBackAsItWent() {
        NoteImages.file(ctx, "pic.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val note = Note("n1", "Title", RichText("x￼", listOf(Span(1, 2, SpanType.IMAGE, "pic.jpg"))), folder = "f1", created = 1, edited = 2)
        val out = file("b.minotesdata")
        DataTransfer.exportTo(ctx, Uri.fromFile(out), Backup(Settings(themeMode = ThemeMode.LIGHT), listOf(note), listOf(Folder("f1", "Work"))))
        NoteImages.file(ctx, "pic.jpg").delete()

        val back = DataTransfer.import(ctx, Uri.fromFile(out))
        assertEquals(listOf(note), back.notes)
        assertEquals(listOf(Folder("f1", "Work")), back.folders)
        assertEquals(ThemeMode.LIGHT, back.settings.themeMode)
        assertArrayEquals(byteArrayOf(1, 2, 3), NoteImages.file(ctx, "pic.jpg").readBytes()) // its picture came back too
    }

    @Test fun importedSettingsAreRepairedAndUpgraded() {
        // corners no screen can set, and a backup from before Grid became the default (settingsVersion 0, Staggered)
        val back = DataTransfer.import(ctx, zip("data.json" to """{"settings":{"cornerRadius":5,"layout":"grid","settingsVersion":0}}""".toByteArray()))
        assertEquals(28, back.settings.cornerRadius)
        assertEquals(NotesLayout.GRID, back.settings.layout)
    }

    @Test fun aPictureCantBeWrittenOutsideTheImagesFolder() {
        DataTransfer.import(ctx, zip(
            "data.json" to "{}".toByteArray(),
            "images/../evil.txt" to "x".toByteArray(),
            "images/.hidden" to "x".toByteArray(),
        ))
        assertFalse(File(ctx.filesDir, "evil.txt").exists())
        assertFalse(File(NoteImages.dir(ctx), ".hidden").exists())
    }

    @Test fun somethingThatIsntABackupIsSaidSo() {
        val e = assertThrows(ImportError::class.java) { DataTransfer.import(ctx, zip("readme.txt" to "hi".toByteArray())) }
        assertEquals("Not a Backup", e.title)
    }

    @Test fun anEntryPastItsLimitStopsTheImport() {
        val e = assertThrows(ImportError::class.java) {
            DataTransfer.run { ByteArray(5000).inputStream().copyCapped(java.io.ByteArrayOutputStream(), 4096) }
        }
        assertEquals("Not a Backup", e.title)
    }

    @Test fun aFileTooBigToBeANoteIsSkippedNotReadWhole() {
        val small = file("note.md").apply { writeText("# Groceries\n- Milk") }
        assertEquals("# Groceries\n- Milk", DataTransfer.readTextCapped(ctx, Uri.fromFile(small)))
        val big = file("huge.log").apply { writeBytes(ByteArray(5000)) }
        assertEquals(null, DataTransfer.readTextCapped(ctx, Uri.fromFile(big), max = 4096))
    }
}
