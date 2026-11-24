// Backup and restore: one .minotes file, a zip holding data.json ({settings, notes, folders}) and images/ (the
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
                        e.name == "data.json" -> raw = zip.readBytes()
                        // only plain names: never a path that could climb out of the images folder
                        e.name.startsWith("images/") && name.isNotEmpty() && !name.startsWith(".") && name == e.name.removePrefix("images/") ->
                            NoteImages.file(ctx, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val data = raw ?: throw ImportError("Not a Backup", "Choose a .minotes file exported from mi. Notes.")
        val obj = runCatching { json.parseToJsonElement(String(data)).jsonObject }.getOrNull() as? JsonObject
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

    /** A note's title as a file name: characters file systems refuse are replaced. */
    fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "-").trim().take(60).ifEmpty { "Untitled" }
}

