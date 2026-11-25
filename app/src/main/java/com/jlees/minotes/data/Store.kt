// JSON-file persistence (one file per key in app-private storage).
package com.jlees.minotes.data

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.encodeToStream

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }

/** One JSON file per key in app-private storage, written atomically. */
class JsonStore(private val dir: File) {
    init { dir.mkdirs() }

    /** A file was there but couldn't be read, and was kept aside as "<key>.json.broken-<time>" (the app says so). */
    @Volatile var keptAside = false

    /**
     * The value saved under [key], or null when there's none. A file that's there but can't be read (damaged, or written
     * by something this version can't make sense of) is moved aside rather than left for the next save to overwrite: the
     * app starts without it, and the file stays in the data folder for a later version, or a person, to recover.
     */
    fun <T> read(key: String, s: KSerializer<T>): T? {
        val f = File(dir, "$key.json")
        if (!f.exists()) return null
        return try { json.decodeFromString(s, f.readText()) } catch (_: Exception) {
            f.renameTo(File(dir, "$key.json.broken-${System.currentTimeMillis()}"))
            keptAside = true
            null
        }
    }

    /**
     * A failed write (storage full) keeps the last good file; the next change tries again. Written as a stream, never as
     * one string, so big values (an extension's megabytes of saved data) don't need twice their size in memory. A [key]
     * may have folders ("extension_storage/<id>").
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Synchronized
    fun <T> write(key: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$key.json.tmp")
        try {
            tmp.parentFile?.mkdirs()
            tmp.writeSynced { json.encodeToStream(s, value, it) }
            tmp.renameTo(File(dir, "$key.json"))
        } catch (_: IOException) { tmp.delete() }
    }

}

/**
 * One stored file, owned by the store whose state it saves: written [delayMs] after that state changes (off the main
 * thread) once [launchIn] runs, or right away by [writeNow] (the app is closing). The wait isn't restarted by further
 * changes: state that keeps changing (a download's progress) is still written every [delayMs], not only once it stops.
 */
class Saver<T>(private val delayMs: Long, private val read: () -> T, private val write: (T) -> Unit) {
    /** The value on disk: the one loaded, then the last one written. State is immutable, so the same object is unchanged. */
    @Volatile private var written: Any? = null

    /** Writes the state now if it changed since it was last on disk (leaving the app does this every time). */
    fun writeNow() { val v = read(); if (v !== written) { write(v); written = v } }

    fun launchIn(scope: CoroutineScope) = scope.launch {
        written = read()
        snapshotFlow(read).drop(1).conflate().collect { delay(delayMs); val v = read(); withContext(Dispatchers.IO) { write(v) }; written = v }
    }
}

/**
 * Writes this file and waits until its bytes are on disk before returning: a phone switched off (or an app killed)
 * right after leaves the whole file or none of it, never an empty one that a rename then puts in place of good data.
 */
fun File.writeSynced(write: (java.io.OutputStream) -> Unit) = java.io.FileOutputStream(this).use { fos ->
    val out = fos.buffered()
    write(out)
    out.flush()
    fos.fd.sync()
}
