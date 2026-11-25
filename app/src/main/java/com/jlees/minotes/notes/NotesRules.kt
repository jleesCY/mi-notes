// Decisions about the notes collection: which notes show and in what order, what a card previews, when Recently
// Deleted lets go of a note. Plain functions, no Android, all unit-tested (NotesRulesTest).
package com.jlees.minotes.notes

import com.jlees.minotes.data.SortOrder

/** How long Recently Deleted keeps a note by default (Settings › Recently Deleted › Keep Deleted Notes). */
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

/** [notes] without those deleted more than [days] ago. */
fun purgeExpired(notes: List<Note>, now: Long, days: Int = TRASH_DAYS) = notes.filter { it.deletedAt == null || now - it.deletedAt < days * DAY_MS }

/** How many of [notes] a Keep Deleted Notes of [days] would delete for good at [now] (purgeExpired's other side). */
fun expiredCount(notes: List<Note>, now: Long, days: Int): Int = notes.count { it.deletedAt != null && now - it.deletedAt >= days * DAY_MS }

/** What deleting [n] notes past a period of [period] ("1 Week") says: "3 notes deleted more than 1 week ago". */
fun expiredWords(n: Int, period: String) = "${if (n == 1) "1 note" else "$n notes"} deleted more than ${period.lowercase()} ago"

/** Whole days left before a deleted note is gone (at least 1 while it's still there). */
fun daysLeft(note: Note, now: Long, days: Int = TRASH_DAYS): Int {
    val gone = (note.deletedAt ?: return days) + days * DAY_MS
    return ((gone - now + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
}

/** A note's title, or "Untitled": a note without a title never borrows its first line (the title is only what you wrote there). */
fun displayTitle(n: Note): String = n.title.trim().ifEmpty { "Untitled" }

/** A card's title: none for a note without one that has text (its text shows from the top), "Untitled" for a blank note. */
fun cardTitle(n: Note): String? = n.title.trim().ifEmpty { if (n.body.plain.isBlank()) "Untitled" else null }

/**
 * A card's preview: up to [maxLines] lines of the note's text, with list markers drawn as text: "•", "1.", "☐" / "☑",
 * indented two spaces a level.
 */
fun previewLines(n: Note, maxLines: Int): List<String> =
    markedLines(n.body).filter { it.isNotBlank() }.take(maxLines)

/** Every paragraph of [rt] as a line of text, list markers written out, pictures left out. */
fun markedLines(rt: RichText): List<String> {
    val text = rt.text
    var numbers = IntArray(8)
    return paragraphs(text).map { (s, e) ->
        val line = text.substring(s, e).replace(IMAGE_CHAR.toString(), "").trimEnd()
        val para = rt.spans.firstOrNull { it.type == SpanType.PARA && it.start <= s && it.end >= s }
        val kind = ListKind.of(para?.value)
        val level = (para?.level ?: 0).coerceIn(0, 7)
        if (kind == ListKind.NUMBER) { numbers[level]++; for (l in level + 1 until 8) numbers[l] = 0 } else numbers = IntArray(8)
        val marker = when (kind) {
            ListKind.BULLET -> "• "; ListKind.NUMBER -> "${numbers[level]}. "
            ListKind.CHECK -> if (para?.checked == true) "☑ " else "☐ "; ListKind.NONE -> ""
        }
        if (line.isBlank()) "" else "  ".repeat(level) + marker + line.trim() // an empty item is an empty line
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

/** Words and characters in a note, for its info. */
fun wordCount(text: String) = WORD.findAll(text.replace(IMAGE_CHAR, ' ')).count()
private val WORD = Regex("\\S+")

/** A note as plain text, for sharing and copying: its title, then its text with list markers written out. */
fun plainText(n: Note): String {
    val body = markedLines(n.body).joinToString("\n").trim('\n')
    return if (n.title.isBlank()) body else "${n.title.trim()}\n\n$body"
}

// ---- what other apps ask of mi. Notes: shared text, the app icon's New Note shortcut, and minotes:// links for
// automation apps (Tasker and the like). Links only ever add: they can't delete or change what's already written.

/** One request from outside the app. */
sealed interface Incoming {
    /** Text shared from another app: a new note, its first line the title when it reads like one. */
    data class Shared(val text: String) : Incoming
    /** The app icon's New Note shortcut. */
    data object Blank : Incoming
    /** `minotes://new?title=…&text=…`: a new note. */
    data class New(val title: String, val text: String) : Incoming
    /** `minotes://append?title=…&text=…`: [text] added to the end of the note titled [title] (a new note when there's none). */
    data class Append(val title: String, val text: String) : Incoming
}

/** The app icon shortcut's action (MainActivity). */
const val NEW_NOTE_ACTION = "com.jlees.minotes.NEW_NOTE"
private const val MAX_LINK_TITLE = 200

/** What an incoming intent asks for, from its [action], shared [text] and [link] (its data URI); null: nothing for us. */
fun incoming(action: String?, text: String?, link: String?): Incoming? = when (action) {
    "android.intent.action.SEND" -> text?.takeIf { it.isNotBlank() }?.take(ShareNote.MAX_SHARED_TEXT)?.let { Incoming.Shared(it) }
    NEW_NOTE_ACTION -> Incoming.Blank
    "android.intent.action.VIEW" -> link?.let(::linkRequest)
    else -> null
}

private fun linkRequest(link: String): Incoming? {
    val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return null
    if (uri.scheme != "minotes") return null
    val q = uri.rawQuery.orEmpty().split('&').mapNotNull { p ->
        val kv = p.split('=', limit = 2)
        if (kv.size == 2) kv[0] to (runCatching { java.net.URLDecoder.decode(kv[1], "UTF-8") }.getOrNull() ?: return@mapNotNull null) else null
    }.toMap()
    // a title is one line, kept short; text as long as a share can be
    val title = q["title"].orEmpty().replace('\n', ' ').trim().take(MAX_LINK_TITLE)
    val text = q["text"].orEmpty().take(ShareNote.MAX_SHARED_TEXT)
    return when (uri.host) {
        "new" -> Incoming.New(title, text)
        "append" -> if (title.isEmpty() || text.isBlank()) null else Incoming.Append(title, text)
        else -> null
    }
}

/** The live note titled [title] (ignoring case and spaces around it), for a link to add to. */
fun noteTitled(notes: List<Note>, title: String): Note? = notes.firstOrNull { it.deletedAt == null && it.title.trim().equals(title.trim(), ignoreCase = true) }

/** [body] with [text] added on lines of its own at the end; its formatting stays as it was. */
fun appended(body: RichText, text: String): RichText = if (body.text.isEmpty()) RichText(text) else body.copy(text = body.text + "\n" + text)
