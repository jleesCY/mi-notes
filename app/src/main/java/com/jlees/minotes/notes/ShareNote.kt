// Sending a note out of mi.: as text (any app), as a Markdown file (notes apps, editors, drives), or to the clipboard.
package com.jlees.minotes.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jlees.minotes.data.DataTransfer
import java.io.File

object ShareNote {
    fun asText(ctx: Context, n: Note) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, displayTitle(n)).putExtra(Intent.EXTRA_TEXT, plainText(n))
        ctx.startActivity(Intent.createChooser(send, null))
    }

    fun asMarkdown(ctx: Context, n: Note) {
        val dir = File(ctx.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
        val f = File(dir, DataTransfer.safeName(displayTitle(n)) + ".md").apply { writeText(noteToMarkdown(n)) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null))
    }

    fun copy(ctx: Context, n: Note) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(displayTitle(n), plainText(n)))
    }
}
