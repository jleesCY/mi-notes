// Backup and restore: one .minotesdata file, a zip holding data.json ({settings, notes, folders}) and images/ (the
// pictures in notes). No cloud, no account: the user puts the file wherever they like. Unknown keys are ignored, so an
// older app reads a newer backup. Markdown export writes each note as a .md file (plus images/) in a zip.
package com.jlees.minotes.data

import android.content.Context
import android.net.Uri
import com.jlees.minotes.notes.Folder
import com.jlees.minotes.notes.Note
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.notes.displayTitle
import com.jlees.minotes.notes.noteToMarkdown
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImportError(val title: String, message: String) : Exception(message)

@Serializable
class Backup(val settings: Settings = Settings(), val notes: List<Note> = emptyList(), val folders: List<Folder> = emptyList())

object DataTransfer {
    fun exportTo(ctx: Context, uri: Uri, backup: Backup) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("data.json")); zip.write(json.encodeToJsonElement(backup).toString().toByteArray()); zip.closeEntry()
                writeImages(ctx, zip, backup.notes)
            }
        } ?: throw ImportError("Couldn't Save", "The file couldn't be opened.")
    }

    /** Reads a .minotes file; its pictures are copied into the app's images straight away (they're named uniquely). */
    fun import(ctx: Context, uri: Uri): Backup {
        var raw: ByteArray? = null
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.substringAfterLast('/')
                    when {
                        e.name == "data.json" -> raw = java.io.ByteArrayOutputStream().also { zip.copyCapped(it, MAX_DATA) }.toByteArray()
                        // only plain names: never a path that could climb out of the images folder
                        e.name.startsWith("images/") && name.isNotEmpty() && !name.startsWith(".") && name == e.name.removePrefix("images/") -> {
                            val f = NoteImages.file(ctx, name)
                            runCatching { f.writeSynced { zip.copyCapped(it, MAX_PICTURE) } }.onFailure { f.delete(); throw it }
                        }
                    }
                }
            }
        }
        val data = raw ?: throw ImportError("Not a Backup", "Choose a file exported from mi. Notes.")
        val obj = runCatching { json.parseToJsonElement(String(data)).jsonObject }.getOrNull()
            ?: throw ImportError("Not a Backup", "This file doesn't hold mi. Notes data.")
        val b = runCatching { json.decodeFromJsonElement<Backup>(obj) }.getOrNull() ?: throw ImportError("Not a Backup", "This file doesn't hold mi. Notes data.")
        return Backup(b.settings.sanitized().migrated(), b.notes.filter { it.id.isNotBlank() }, b.folders.filter { it.id.isNotBlank() })
    }

    /** Every note as Markdown, one .md file each (in a folder per mi. folder), with their pictures in images/. */
    fun exportMarkdown(ctx: Context, uri: Uri, notes: List<Note>, folders: List<Folder>) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val used = HashSet<String>()
                for (n in notes) {
                    val folder = folders.firstOrNull { it.id == n.folder }?.name?.let { safeName(it) + "/" } ?: ""
                    var name = folder + safeName(displayTitle(n))
                    var k = 2
                    while (!used.add(name.lowercase())) name = folder + safeName(displayTitle(n)) + " ${k++}"
                    // a note in a folder reaches the shared images/ one level up
                    val md = noteToMarkdown(n).let { if (folder.isEmpty()) it else it.replace("](images/", "](../images/") }
                    zip.putNextEntry(ZipEntry("$name.md")); zip.write(md.toByteArray()); zip.closeEntry()
                }
                writeImages(ctx, zip, notes)
            }
        } ?: throw ImportError("Couldn't Save", "The file couldn't be opened.")
    }

    private fun writeImages(ctx: Context, zip: ZipOutputStream, notes: List<Note>) {
        for (img in notes.flatMap { it.body.images }.toSet()) {
            val f = NoteImages.file(ctx, img)
            if (!f.exists()) continue
            zip.putNextEntry(ZipEntry("images/$img")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
    }

    // a backup is a file from anywhere: what's in it is read only up to sizes no real one comes near
    private const val MAX_DATA = 64L shl 20     // 64 MB of notes
    private const val MAX_PICTURE = 32L shl 20  // 32 MB a picture
    /** Import Notes: a text or Markdown file up to this becomes a note; past it, it's a log or a disk image, not a note. */
    const val MAX_NOTE_FILE = 2L shl 20         // 2 MB

    /** A picked file's text, or null when it's past [max] bytes (and so skipped, not read whole into memory). */
    fun readTextCapped(ctx: Context, uri: Uri, max: Long = MAX_NOTE_FILE): String? {
        val out = java.io.ByteArrayOutputStream()
        val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("can't open $uri")
        try { input.use { it.copyCapped(out, max) } } catch (_: ImportError) { return null }
        return out.toByteArray().decodeToString()
    }

    /** Copies this entry to [out], stopping with "Not a Backup" past [max] bytes (a damaged or deliberately huge file). */
    internal fun java.io.InputStream.copyCapped(out: java.io.OutputStream, max: Long) {
        val buf = ByteArray(64 * 1024); var total = 0L
        while (true) {
            val n = read(buf); if (n < 0) break
            total += n
            if (total > max) throw ImportError("Not a Backup", "This file is too big to be an mi. Notes backup.")
            out.write(buf, 0, n)
        }
    }

    /** A note's title as a file name: characters file systems refuse are replaced. */
    fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "-").trim().take(60).ifEmpty { "Untitled" }
}

