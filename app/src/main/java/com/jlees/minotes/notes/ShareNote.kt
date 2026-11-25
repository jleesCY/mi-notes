// Sending a note out of mi.: as text (any app), as a Markdown file (notes apps, editors, drives), or to the clipboard.
package com.jlees.minotes.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jlees.minotes.data.DataTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareNote {
    /**
     * The note's text to any app. A very long note goes as a Markdown file instead: Android refuses to hand over more
     * than about 1 MB in one go (and would crash the app), while a file has no such limit.
     */
    suspend fun asText(ctx: Context, n: Note) {
        val text = plainText(n)
        if (text.length > MAX_SHARED_TEXT) return asMarkdown(ctx, n)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, displayTitle(n)).putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(send, null))
    }

    /** The note as a .md file (written off the main thread), to any app that takes files. */
    suspend fun asMarkdown(ctx: Context, n: Note) {
        val f = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
            File(dir, DataTransfer.safeName(displayTitle(n)) + ".md").apply { writeText(noteToMarkdown(n)) }
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null))
    }

    /** Past this, shared text goes as a file, and copying stops (each character can take 2 bytes or more in the hand-over). */
    const val MAX_SHARED_TEXT = 100_000

    /** The note's text to the clipboard; false (nothing copied) when it's too long for Android to hand over. */
    fun copy(ctx: Context, n: Note): Boolean {
        val text = plainText(n)
        if (text.length > MAX_SHARED_TEXT) return false
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(displayTitle(n), text))
        return true
    }
}
