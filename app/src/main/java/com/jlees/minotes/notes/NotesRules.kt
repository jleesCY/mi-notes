// Decisions about the notes collection: which notes show and in what order, what a card previews, when Recently
// Deleted lets go of a note. Plain functions, no Android, all unit-tested (NotesRulesTest).
package com.jlees.minotes.notes

import com.jlees.minotes.data.SortOrder

/** How long Recently Deleted keeps a note before it's gone for good. */
const val TRASH_DAYS = 30
private const val DAY_MS = 86_400_000L

/**
 * The home page's notes: live notes in [folder] (null: all), matching [query] in title or text, pinned first, then in
 * [sort] order. [notes] is in manual order (the order they were dragged into).
 */
fun visibleNotes(notes: List<Note>, folder: String?, query: String, sort: SortOrder): List<Note> {
    val q = query.trim()
    val shown = notes.filter { n ->
        n.deletedAt == null && (folder == null || n.folder == folder) &&
            (q.isEmpty() || n.title.contains(q, ignoreCase = true) || n.body.plain.contains(q, ignoreCase = true))
    }
    val sorted = when (sort) {
        SortOrder.MANUAL -> shown
        SortOrder.EDITED -> shown.sortedByDescending { it.edited }
        SortOrder.CREATED -> shown.sortedByDescending { it.created }
        SortOrder.TITLE -> shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { displayTitle(it) })
    }
    return sorted.filter { it.pinned } + sorted.filterNot { it.pinned }
}

/** The notes in Recently Deleted, newest deletion first. */
fun trashed(notes: List<Note>) = notes.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

/** [notes] without those deleted more than [TRASH_DAYS] ago. */
fun purgeExpired(notes: List<Note>, now: Long) = notes.filter { it.deletedAt == null || now - it.deletedAt < TRASH_DAYS * DAY_MS }

/** Whole days left before a deleted note is gone (at least 1 while it's still there). */
fun daysLeft(note: Note, now: Long): Int {
    val gone = (note.deletedAt ?: return TRASH_DAYS) + TRASH_DAYS * DAY_MS
    return ((gone - now + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
}

/** A note's title, or its first line of text when it has none, or "Untitled". */
fun displayTitle(n: Note): String =
    n.title.trim().ifEmpty { n.body.plain.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(80) ?: "Untitled" }

/**
 * A card's preview: up to [maxLines] lines of the note's text (the first line is skipped when it's standing in as the
 * title), with list markers drawn as text: "•", "1.", "☐" / "☑", indented two spaces a level.
 */
fun previewLines(n: Note, maxLines: Int): List<String> =
    markedLines(n.body).filter { it.isNotBlank() }.drop(if (n.title.isBlank()) 1 else 0).take(maxLines)

/** Every paragraph of [rt] as a line of text, list markers written out, pictures left out. */
fun markedLines(rt: RichText): List<String> {
    val text = rt.text
    var numbers = IntArray(8)
    return paragraphs(text).map { (s, e) ->
        val line = text.substring(s, e).replace(IMAGE_CHAR.toString(), "").trimEnd()
        val para = rt.spans.firstOrNull { it.type == SpanType.PARA && it.start <= s && it.end >= s }
        val kind = para?.value?.let { v -> ListKind.entries.firstOrNull { it.name.equals(v, true) } } ?: ListKind.NONE
        val level = (para?.level ?: 0).coerceIn(0, 7)
        if (kind == ListKind.NUMBER) { numbers[level]++; for (l in level + 1 until 8) numbers[l] = 0 } else numbers = IntArray(8)
        val marker = when (kind) {
            ListKind.BULLET -> "• "; ListKind.NUMBER -> "${numbers[level]}. "
            ListKind.CHECK -> if (para?.checked == true) "☑ " else "☐ "; ListKind.NONE -> ""
        }
        if (line.isBlank() && kind == ListKind.NONE) "" else "  ".repeat(level) + marker + line.trim()
    }
}

/** Checklist progress: (done, total), or null when the note has no checklist. */
fun checklistProgress(body: RichText): Pair<Int, Int>? {
    val checks = body.spans.filter { it.type == SpanType.PARA && it.value.equals(ListKind.CHECK.name, true) }
    return if (checks.isEmpty()) null else checks.count { it.checked } to checks.size
}

/** Each paragraph's [start, end) in [text], the newline not included; an empty text is one empty paragraph. */
fun paragraphs(text: String): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var s = 0
    while (true) {
        val nl = text.indexOf('\n', s)
        if (nl < 0) { out += s to text.length; return out }
        out += s to nl; s = nl + 1
    }
}

/** The folder after (or with [forward] false, before) [current] in All, folder 1, folder 2…; null is All. Wraps round. */
fun nextFolder(folders: List<Folder>, current: String?, forward: Boolean): String? {
    val ids = listOf<String?>(null) + folders.map { it.id }
    val i = ids.indexOf(current).coerceAtLeast(0)
    return ids[(i + if (forward) 1 else -1).mod(ids.size)]
}

/** Words and characters in a note, for its info. */
fun wordCount(text: String) = Regex("\\S+").findAll(text.replace(IMAGE_CHAR, ' ')).count()

/** A note as plain text, for sharing and copying: its title, then its text with list markers written out. */
fun plainText(n: Note): String {
    val body = markedLines(n.body).joinToString("\n").trim('\n')
    return if (n.title.isBlank()) body else "${n.title.trim()}\n\n$body"
}
