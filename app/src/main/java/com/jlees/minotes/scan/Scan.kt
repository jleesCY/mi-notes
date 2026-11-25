// Scanning text into a note: a photo from the camera app, or a picture from Files, read on the phone by ML Kit's
// bundled text recognizer. Its model ships inside the APK, and its usage reports to Google are taken out in the
// manifest (the datatransport services), so nothing leaves the phone (PRIVACY.md).
package com.jlees.minotes.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.ui.common.Notices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The text in the picture at [uri], read on the phone, with its lists (layoutScan); empty when there's none.
 *
 * As mi. Budget reads a receipt, a picture that doesn't read clearly as it is is read twice more: as a cleaned-up black
 * and white copy (ImageEnhance: an adaptive threshold that sees past shadows and uneven light, strokes thickened a
 * touch), and as an inverted copy (light writing on a dark board or screen). The pass that reads the most text, most
 * surely, is kept. A clear picture (clearRead) is read once, saving the phone two passes.
 */
suspend fun readText(ctx: Context, uri: Uri): RichText {
    val picture = withContext(Dispatchers.IO) { decode(ctx, uri) }
    val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val passes = mutableListOf(read(reader, picture) to 1f) // each pass's text, and how much smaller its picture was
        if (!clearRead(passes[0].first.textBlocks.flatMap { it.lines }.map { it.confidence })) {
            val small = scaleDown(picture, 1600)
            val w = small.width; val h = small.height
            val gray = withContext(Dispatchers.Default) { IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }.let(ImageEnhance::toGray) }
            if (small !== picture) small.recycle()
            for (invert in listOf(false, true)) {
                val copy = withContext(Dispatchers.Default) { Bitmap.createBitmap(ImageEnhance.toArgb(ImageEnhance.enhance(gray, w, h, invert)), w, h, Bitmap.Config.ARGB_8888) }
                try { passes += read(reader, copy) to picture.width.toFloat() / w }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {} // one clean-up pass failing isn't fatal: the others are still there
                finally { copy.recycle() }
            }
        }
        val (text, k) = passes.maxBy { score(it.first) }
        return withContext(Dispatchers.Default) { layoutScan(straightened(lines(text, picture, k), text)) }
    } finally { reader.close(); picture.recycle() }
}

/** One pass. ML Kit reads the picture until it's done even when nobody's waiting, so the wait isn't cancellable: the
 *  picture is recycled only once it's finished (well under a second), and cancelling counts straight after. */
private suspend fun read(reader: TextRecognizer, picture: Bitmap): Text {
    val t = withContext(NonCancellable) {
        suspendCancellableCoroutine { c -> reader.process(InputImage.fromBitmap(picture, 0)).addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resumeWithException(it) } }
    }
    currentCoroutineContext().ensureActive()
    return t
}

/**
 * Whether a pass read clearly enough to need no clean-up passes: some text, and ML Kit sure of it on average (each line's
 * [confidences], 0–1). A shadowed, faint or light-on-dark picture reads less surely, and gets the other two passes.
 */
fun clearRead(confidences: List<Float>): Boolean {
    val known = confidences.filter { !it.isNaN() && it > 0f }
    return known.isNotEmpty() && known.average() >= 0.8
}

/** How well a pass read: its letters and digits, each counted by how sure ML Kit was of its line. */
private fun score(t: Text): Float = t.textBlocks.flatMap { it.lines }.sumOf { l ->
    val sure = l.confidence.takeIf { !it.isNaN() && it > 0f } ?: 0.5f
    (l.text.count(Char::isLetterOrDigit) * sure).toDouble()
}.toFloat()

private fun scaleDown(b: Bitmap, side: Int): Bitmap {
    val k = side.toFloat() / maxOf(b.width, b.height)
    return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

/**
 * [lines] with their heights straightened for a tilted photo (as mi. Budget does): on a page turned a few degrees a row
 * slopes, so the word at its right end sits lower than the one at its left. Marks are found first, on the picture as is.
 */
private fun straightened(lines: List<ScanLine>, t: Text): List<ScanLine> {
    val angles = t.textBlocks.flatMap { it.lines }.map { it.angle }.filter { !it.isNaN() && kotlin.math.abs(it) < 45f }.sorted()
    val tilt = if (angles.isEmpty()) 0f else angles[angles.size / 2]
    if (kotlin.math.abs(tilt) < 0.5f) return lines
    val slope = kotlin.math.tan(Math.toRadians(tilt.toDouble())).toFloat()
    return lines.map { l -> val dy = ((l.left + l.right) / 2f * slope).toInt(); l.copy(top = l.top - dy, bottom = l.bottom - dy) }
}

/** The picture upright (as its EXIF says), at most [MAX_SIDE] pixels a side (a phone photo is 4000 or more), in memory
 *  whose pixels can be read (for the marks). */
private fun decode(ctx: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val k = maxOf(info.size.width, info.size.height) / MAX_SIDE.toFloat()
        if (k > 1) d.setTargetSize((info.size.width / k).toInt(), (info.size.height / k).toInt())
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val raw = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw java.io.IOException("not a picture")
    val turn = ctx.contentResolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
        }
    } ?: 0
    return if (turn == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
}
private const val MAX_SIDE = 2400 // as mi. Budget: plenty for text, and three passes stay quick

/** A marker on its own at the start of a line ("•", "-", "[]", or one letter that may be a misread box: "D"): its
 *  picture is looked at for a mark. A word ("Oat") never is, though its O looks like a box. */
private fun Text.Element.maybeMark() = boundingBox != null && !text.any(Char::isDigit) && (text.length == 1 || (text.length <= 3 && text.none(Char::isLetter)))

/** One letter, not a word on its own (A, I): what ML Kit makes of a box it doesn't see as a box ("D Spinach"). */
private fun Text.Element.strayLetter() = text.length == 1 && text[0].isLetter() && text !in setOf("A", "a", "I")

/**
 * Every line ML Kit read, with its box, where its words start and the mark in front of it (a line without a box keeps
 * its place in reading order, as a row of its own). A mark ML Kit read as part of the line ("• Milk", "D Milk") is looked
 * at where it is and taken out of the words; one it missed is looked for just left of them.
 */
private fun lines(t: Text, picture: Bitmap, k: Float): List<ScanLine> {
    // the pass's boxes, in the picture's pixels ([k]: how much smaller the pass's copy was)
    fun Rect?.up(): Rect? = this?.let { if (k == 1f) it else Rect((it.left * k).toInt(), (it.top * k).toInt(), (it.right * k).toInt(), (it.bottom * k).toInt()) }
    val all = t.textBlocks.flatMap { it.lines }
    val boxes = all.mapNotNull { it.boundingBox.up() }
    var y = 0
    return all.map { l ->
        val b = l.boundingBox.up() ?: return@map ScanLine(l.text, 0, y + 1, 1, y + 2).also { y += 2 }
        y = b.bottom
        val h = b.height()
        val first = l.elements.firstOrNull()
        if (l.elements.size >= 2 && first != null && first.maybeMark()) {
            val fb = first.boundingBox.up()!!
            val m = markIn(picture, Rect(fb.left - h / 6, b.top - h / 6, fb.right + h / 6, b.bottom + h / 6), h, nearRight = false, except = boxes - b)
            if (m != null) return@map ScanLine(l.text.substringAfter(first.text).trim(), b.left, b.top, b.right, b.bottom, l.elements[1].boundingBox.up()?.left ?: b.left, m)
        }
        val m = markIn(picture, Rect(b.left - h * 5 / 2, b.top - h / 6, b.left - h / 12, b.bottom + h / 6), h, nearRight = true, except = boxes - b)
        // a mark beside the line, and a stray letter starting it: the letter was ML Kit's guess at the mark
        if (m != null && l.elements.size >= 2 && first != null && first.strayLetter())
            return@map ScanLine(l.text.substringAfter(first.text).trim(), b.left, b.top, b.right, b.bottom, l.elements[1].boundingBox.up()?.left ?: b.left, m)
        ScanLine(l.text, b.left, b.top, b.right, b.bottom, b.left, m)
    }
}

/** The mark (findMark) in [r] of [picture], leaving out other lines' words ([except]) that reach into it. */
private fun markIn(picture: Bitmap, r: Rect, lineH: Int, nearRight: Boolean, except: List<Rect>): Mark? {
    if (!r.intersect(0, 0, picture.width, picture.height) || r.width() < 2 || r.height() < 2) return null
    val px = IntArray(r.width() * r.height())
    picture.getPixels(px, 0, r.width(), r.left, r.top, r.width(), r.height())
    val lum = IntArray(px.size) { i ->
        val x = r.left + i % r.width(); val y = r.top + i / r.width()
        if (except.any { it.contains(x, y) }) 255 // paper, as far as the mark's concerned
        else { val c = px[i]; (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000 }
    }
    return findMark(r.width(), r.height(), lum, lineH, nearRight)
}

/** Scanning's two ways in: [camera] takes a photo with the camera app, [files] picks a picture from Files. */
class Scanner(val camera: () -> Unit, val files: () -> Unit)

/** A [Scanner] whose text, when a picture has some, goes to [onText]; no text, or a picture it can't read, says so. */
@Composable
fun rememberScanner(onText: (RichText) -> Unit): Scanner {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onText)
    // the camera app writes its photo here (a FileProvider path, file_paths.xml); it's deleted once read
    val photo = remember { File(ctx.cacheDir, "scan/photo.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", photo) }
    fun read(uri: Uri) = scope.launch {
        Notices.show("Reading text…")
        val text = runCatching { readText(ctx, uri) }.getOrNull()
        if (uri == photoUri) photo.delete()
        when {
            text == null -> Notices.show("Couldn't read that picture")
            text.text.isBlank() -> Notices.show("No text found")
            else -> { Notices.current = null; latest(text) }
        }
    }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) read(photoUri) }
    // Files (the system's document browser: the phone, Downloads, Drive…), not the photo picker that an image/*
    // GetContent turns into on newer Android
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::read) }
    return remember {
        Scanner(
            camera = { runCatching { shoot.launch(photoUri) }.onFailure { Notices.show("No camera app found") } },
            files = { runCatching { pick.launch(arrayOf("image/*")) }.onFailure { Notices.show("No file picker found") } },
        )
    }
}
