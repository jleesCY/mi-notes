// Pictures in notes: each a JPEG in the app's images/ folder, named in its note's IMAGE span. Picked pictures are
// copied in (scaled down to at most 1600px), so a note never depends on a photo staying in the gallery.
package com.jlees.minotes.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

object NoteImages {
    private const val MAX_PX = 1600

    fun dir(ctx: Context) = File(ctx.filesDir, "images").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Copies the picture at [uri] in; returns its file name, or null when it can't be read. Off the main thread. */
    fun import(ctx: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = "${UUID.randomUUID()}.jpg"
        file(ctx, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()

    fun delete(ctx: Context, names: List<String>) = names.forEach { file(ctx, it).delete() }

    /** Removes image files no note uses any more (left by an edit that deleted a picture). */
    fun clean(ctx: Context, notes: List<Note>) {
        val used = notes.flatMap { it.body.images }.toSet()
        dir(ctx).listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}
