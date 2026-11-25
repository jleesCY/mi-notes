// Pictures in notes: each a JPEG in the app's images/ folder, named in its note's IMAGE span. New pictures can't be
// added any more; notes that have them still show them, and backups and exports still carry them.
package com.jlees.minotes.notes

import android.content.Context
import java.io.File

object NoteImages {
    fun dir(ctx: Context) = File(ctx.filesDir, "images").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    fun delete(ctx: Context, names: List<String>) = names.forEach { file(ctx, it).delete() }

    /** Removes image files no note uses any more (left by an edit that deleted a picture). */
    fun clean(ctx: Context, notes: List<Note>) {
        val used = notes.flatMap { it.body.images }.toSet()
        dir(ctx).listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}
