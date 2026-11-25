// How a scan's lines become a note's paragraphs. ML Kit gives lines and their boxes, nothing about lists, so this reads
// the structure back:
//  - each line's kind from what's in front of it: a box (ticked or not) a checklist item, a dot or a dash a bullet, "1."
//    a number (the mark seen in the picture, ScanMarks; or its character, when ML Kit kept it: •, -, ☐, [x]…);
//  - a list's lines whose mark wasn't seen take the kind of the ones whose mark was;
//  - a run of short lines with no marks at all (a list written without them) a bulleted list;
//  - indents (where each line's words start) levels; larger lettering, or a colon at the end, a heading;
//  - lines side by side one line ("cell | cell": notes have no tables) or, two columns of a list, one after the other.
// Pure, so it's tested on the JVM (ScanLayoutTest).
package com.jlees.minotes.scan

import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType

/**
 * One line ML Kit read, with its box in the picture's pixels. [textLeft]: where its words start (after a mark that's
 * part of the box); [mark]: the mark seen in front of it, already taken out of [text].
 */
data class ScanLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val textLeft: Int = left, val mark: Mark? = null) {
    val height get() = bottom - top
    val midY get() = (top + bottom) / 2
}

/**
 * A line of the note to be: its words, how far in they start (pixels, from its column's edge), how big its letters are,
 * and the mark seen in front of it. [row]: a table's row, kept as a plain line rather than taken for a list item.
 */
private data class Out(val text: String, val indent: Int, val charWidth: Float, val mark: Mark?, val row: Boolean = false)

/** [lines] as a note body: plain lines, and list items with their kind, level and tick (PARA spans). */
fun layoutScan(lines: List<ScanLine>): RichText {
    val clean = lines.filter { it.text.isNotBlank() }
    if (clean.isEmpty()) return RichText()
    val unit = median(clean.map { it.height }, 0).coerceAtLeast(1)
    return listify(readingOrder(clean, unit))
}

// ---- reading order: rows, then a table or columns when lines sit side by side

private fun ScanLine.out(indentFrom: Int, row: List<ScanLine> = listOf(this)) = Out(
    text = row.joinToString(if (row.size > 1) " | " else "") { it.text.trim() },
    indent = textLeft - indentFrom,
    charWidth = (right - textLeft).toFloat() / text.trim().length.coerceAtLeast(1),
    mark = mark, row = row.size > 1,
)

/** Lines whose middles fall within one another's height are on one row, left to right. */
private fun rows(lines: List<ScanLine>): List<List<ScanLine>> {
    val rows = mutableListOf<MutableList<ScanLine>>()
    for (l in lines.sortedBy { it.top }) {
        val row = rows.lastOrNull()
        if (row != null && row.any { l.midY in it.top..it.bottom || it.midY in l.top..l.bottom }) row += l else rows += mutableListOf(l)
    }
    return rows.map { r -> r.sortedBy { it.left } }
}

private fun readingOrder(lines: List<ScanLine>, unit: Int): List<Out> {
    val rows = rows(lines)
    val edge = lines.minOf { it.textLeft }
    val wide = rows.filter { it.size >= 2 }
    // one line per row (a line ML Kit split in two here and there is just joined back up)
    if (wide.size < 2) return rows.map { r -> r[0].copy(text = r.joinToString(" ") { it.text.trim() }, right = r.last().right).out(edge) }
    // two or more rows of side-by-side lines: a table (figures down the right, as a receipt or a price list has) is
    // one line per row; columns of words are a list in columns, read one column after the other
    if (wide.count { isFigure(it.last().text) } * 2 >= wide.size) return rows.map { r -> r[0].out(edge, r) }
    val columns = mutableListOf<Int>() // each column's left edge
    fun columnOf(l: ScanLine): Int {
        val i = columns.indexOfFirst { kotlin.math.abs(it - l.textLeft) < unit * 3 }
        if (i >= 0) return i
        columns += l.textLeft; return columns.lastIndex
    }
    val placed = lines.sortedBy { it.textLeft }.map { it to columnOf(it) } // left to right, so a column starts at its leftmost line
    val order = columns.indices.sortedBy { columns[it] }
    return placed.sortedWith(compareBy({ order.indexOf(it.second) }, { it.first.top })).map { (l, c) -> l.out(columns[c]) }
}

/** A price, a quantity or a time: what a table's right-hand column holds ("2.40", "$3", "x2", "10:30", "500 g"). */
private fun isFigure(s: String) = s.trim().let { it.length <= 12 && it.any(Char::isDigit) && it.count(Char::isLetter) <= 3 }

// ---- lists

private val NUMBER = Regex("""^\s*(?:\(\d{1,3}\)|\d{1,3}[.)])\s+""")
private val CHECKED = Regex("""^\s*(?:\[\s*[xX✓✔]\s*]|[☑☒✅✓✔])\s*""")
private val UNCHECKED = Regex("""^\s*(?:\[\s*]|[☐□▢◻❑❒])\s*""")
private val BULLET = Regex("""^\s*[•·●○◦▪▫■‣⁃∙*+→»>–—-]\s+""")

/** A line's list kind, whether it's ticked, and its words without the marker. */
private class Item(val text: String, val kind: ListKind, val checked: Boolean = false)

/** The kind [l]'s own marker says: "1." a number (read reliably), else the mark seen, else a marker character kept. */
private fun marked(l: Out): Item? {
    val s = l.text
    return when {
        NUMBER.containsMatchIn(s) -> Item(s.replaceFirst(NUMBER, ""), ListKind.NUMBER)
        l.mark == Mark.BOX -> Item(s, ListKind.CHECK)
        l.mark == Mark.TICKED_BOX -> Item(s, ListKind.CHECK, true)
        l.mark == Mark.BULLET -> Item(s, ListKind.BULLET)
        CHECKED.containsMatchIn(s) -> Item(s.replaceFirst(CHECKED, ""), ListKind.CHECK, true)
        UNCHECKED.containsMatchIn(s) -> Item(s.replaceFirst(UNCHECKED, ""), ListKind.CHECK)
        BULLET.containsMatchIn(s) -> Item(s.replaceFirst(BULLET, ""), ListKind.BULLET)
        else -> null
    }
}

/** Short, with no full stop or colon at the end: a list item rather than a sentence or a heading ("Milk", "2 lemons"). */
private fun itemLike(s: String) = s.length <= 40 && s.trim().split(SPACES).size <= 6 && !s.trimEnd().endsWith(".") && !s.trimEnd().endsWith(":")
private val SPACES = Regex("\\s+")

private fun listify(lines: List<Out>): RichText {
    val items = Array(lines.size) { marked(lines[it]) }
    // headings: unmarked lines ending in a colon, or in clearly larger lettering than the rest; the first line, a
    // page's title, a little larger is enough. (Not by height: ML Kit's boxes grow with the letters below the line, so
    // "pasta" stands taller than "milk".)
    val letters = median(lines.map { it.charWidth }, 0f)
    val heading = BooleanArray(lines.size) { i ->
        val l = lines[i]
        val bigger = if (i == 0) l.charWidth >= median(lines.drop(1).map { it.charWidth }, 0f) * 1.2f else l.charWidth >= letters * 1.45f
        items[i] == null && lines.size > 1 && (l.text.trimEnd().endsWith(":") || bigger)
    }
    // lists: runs of lines that are marked, or short enough to be items, between headings, sentences and table rows
    var i = 0
    while (i < lines.size) {
        var j = i
        while (j < lines.size && !heading[j] && (items[j] != null || (!lines[j].row && itemLike(lines[j].text)))) j++
        if (j > i) {
            val run = i until j
            val kinds = run.mapNotNull { items[it]?.kind }
            // marked lines decide for the unmarked ones (a mark ML Kit missed): the kind most of them are; with no
            // marks at all, three short lines or more are a list written without them, as bullets
            val kind = kinds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: if (run.count() >= 3) ListKind.BULLET else null
            // above a list's first marked line, an unmarked line starting left of its words (in line with the marks)
            // is something over the list, not an item of it
            val firstMarked = run.firstOrNull { items[it] != null }
            val words = if (firstMarked == null) 0 else median(run.filter { items[it] != null }.map { lines[it].indent }, 0)
            val cw = median(run.map { lines[it].charWidth }, 0f)
            if (kind != null) for (k in run) if (items[k] == null && !(firstMarked != null && k < firstMarked && lines[k].indent < words - cw)) items[k] = Item(lines[k].text, kind)
            i = j
        } else i++
    }
    // indents: how far each item's words start in from the least indented item of its list, in letters
    val text = StringBuilder(); val spans = mutableListOf<Span>()
    i = 0
    while (i < lines.size) {
        if (items[i] == null) { text.append(lines[i].text.trim()).append('\n'); i++; continue }
        var j = i
        while (j < lines.size && items[j] != null) j++
        val edge = (i until j).minOf { lines[it].indent }
        val step = median((i until j).map { lines[it].charWidth }, 0f) * 2f
        for (k in i until j) {
            val item = items[k]!!
            val start = text.length
            text.append(item.text.trim())
            val level = if (step > 0f) ((lines[k].indent - edge) / step).toInt().coerceIn(0, 6) else 0
            spans += Span(start, text.length, SpanType.PARA, item.kind.name.lowercase(), level, item.checked)
            text.append('\n')
        }
        i = j
    }
    return RichText(text.toString().trimEnd('\n'), spans)
}

private fun <T : Comparable<T>> median(xs: List<T>, none: T): T = if (xs.isEmpty()) none else xs.sorted()[xs.size / 2]
