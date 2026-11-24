// Notes to and from Markdown, so a note can leave mi. (share, export) and come back in (import) in a format every
// notes app and text editor reads. What Markdown has no syntax for (underline, colour, highlight) goes out as the
// small HTML tags Markdown allows (<u>, <mark>, <span style="color">); weights and sizes round to bold and headings.
// Pure Kotlin, tested in MarkdownTest.
package com.jlees.minotes.notes

private data class Look(val bold: Boolean, val italic: Boolean, val strike: Boolean, val underline: Boolean, val mark: Boolean, val code: Boolean, val color: String?)

/** A note as Markdown: its title as a heading (when it has one), then its body. */
fun noteToMarkdown(n: Note): String {
    val body = toMarkdown(n.body)
    return if (n.title.isBlank()) body else "# ${n.title.trim()}\n\n$body"
}

/** [rt] as Markdown. Images become `![](images/<file>)`, as a backup or export folder holds them. */
fun toMarkdown(rt: RichText): String {
    val text = rt.text
    val out = StringBuilder()
    val numbers = IntArray(8)
    paragraphs(text).forEachIndexed { pi, (s, e) ->
        if (pi > 0) out.append('\n')
        val para = rt.spans.firstOrNull { it.type == SpanType.PARA && it.start <= s && it.end >= s }
        val kind = para?.value?.let { v -> ListKind.entries.firstOrNull { it.name.equals(v, true) } } ?: ListKind.NONE
        val level = (para?.level ?: 0).coerceIn(0, 7)
        if (kind == ListKind.NUMBER) { numbers[level]++; for (l in level + 1 until 8) numbers[l] = 0 } else numbers.fill(0)
        // a paragraph all in one large size reads as a heading
        val size = rt.spans.filter { it.type == SpanType.SIZE && it.start <= s && it.end >= e && e > s }.maxOfOrNull { it.value.toFloatOrNull() ?: 1f } ?: 1f
        val heading = when { kind != ListKind.NONE -> ""; size >= 1.9f -> "# "; size >= 1.4f -> "## "; size >= 1.2f -> "### "; else -> "" }
        out.append(when (kind) {
            ListKind.NONE -> heading
            ListKind.BULLET -> "  ".repeat(level) + "- "
            ListKind.NUMBER -> "   ".repeat(level) + "${numbers[level]}. "
            ListKind.CHECK -> "  ".repeat(level) + if (para?.checked == true) "- [x] " else "- [ ] "
        })
        out.append(inlineMarkdown(rt, s, e, escapeStart = kind == ListKind.NONE && heading.isEmpty(), inHeading = heading.isNotEmpty()))
        // Markdown runs lines of one paragraph together: end plain lines with a hard break, unless a blank line follows
        // (not after a heading, nor before a list item, which start lines of their own)
        if (kind == ListKind.NONE && heading.isEmpty() && e < text.length) {
            val ns = e + 1; val ne = text.indexOf('\n', ns).let { if (it < 0) text.length else it }
            val nextIsList = rt.spans.any { it.type == SpanType.PARA && it.start <= ns && it.end >= ns && !it.value.equals("none", true) }
            if (ne > ns && !nextIsList) out.append("  ")
        }
    }
    return out.toString()
}

private fun inlineMarkdown(rt: RichText, s: Int, e: Int, escapeStart: Boolean, inHeading: Boolean): String {
    fun lookAt(i: Int): Look {
        fun has(t: SpanType) = rt.spans.any { it.type == t && it.start <= i && it.end > i }
        val weight = rt.spans.filter { it.type == SpanType.WEIGHT && it.start <= i && it.end > i }.maxOfOrNull { it.value.toIntOrNull() ?: 400 } ?: 400
        return Look(
            bold = weight >= 600 && !inHeading, italic = has(SpanType.ITALIC), strike = has(SpanType.STRIKE), underline = has(SpanType.UNDERLINE),
            mark = has(SpanType.HIGHLIGHT), code = rt.spans.any { it.type == SpanType.FONT && it.value == "mono" && it.start <= i && it.end > i },
            color = rt.spans.lastOrNull { it.type == SpanType.COLOR && it.start <= i && it.end > i }?.value,
        )
    }
    val sb = StringBuilder()
    var i = s
    while (i < e) {
        if (rt.text[i] == IMAGE_CHAR) {
            rt.spans.firstOrNull { it.type == SpanType.IMAGE && it.start == i }?.let { sb.append("![](images/${it.value})") }
            i++; continue
        }
        val look = lookAt(i)
        var j = i + 1
        while (j < e && rt.text[j] != IMAGE_CHAR && lookAt(j) == look) j++
        val raw = rt.text.substring(i, j)
        var run = if (look.code) raw.replace("`", "'") else escape(raw, atLineStart = escapeStart && i == s)
        // markers hug the words: spaces at a run's ends stay outside them
        val lead = run.takeWhile { it == ' ' }; val trail = run.takeLastWhile { it == ' ' }
        run = run.trim(' ')
        if (run.isNotEmpty()) {
            if (look.code) run = "`$run`"
            if (look.italic) run = "*$run*"
            if (look.bold) run = "**$run**"
            if (look.strike) run = "~~$run~~"
            if (look.underline) run = "<u>$run</u>"
            if (look.mark) run = "<mark>$run</mark>"
            if (look.color != null) run = "<span style=\"color:${look.color}\">$run</span>"
        }
        sb.append(lead).append(run).append(if (run.isEmpty()) "" else trail)
        i = j
    }
    return sb.toString()
}

private fun escape(s: String, atLineStart: Boolean): String {
    val b = StringBuilder()
    for (c in s) { if (c in "\\*_~`<[") b.append('\\'); b.append(c) }
    // a line starting "#", "-", "+" or "1." would turn into a heading or list
    if (atLineStart && Regex("^(#|[-+] |\\d+\\. )").containsMatchIn(b)) b.insert(0, '\\')
    return b.toString()
}

// ---------------------------------------------------------------- import

/** A Markdown file as a note: a leading "# " heading becomes its title, else [fallbackTitle] (the file's name). */
fun markdownToNote(md: String, fallbackTitle: String, id: String, now: Long): Note {
    val lines = md.replace("\r\n", "\n").trimStart('\n').lines()
    val first = lines.firstOrNull().orEmpty()
    val (title, rest) = if (first.startsWith("# ")) first.removePrefix("# ").trim() to lines.drop(1).dropWhile { it.isBlank() }
        else fallbackTitle to lines
    return Note(id, title, fromMarkdown(rest.joinToString("\n")), created = now, edited = now)
}

/** Markdown read back into a note body: headings, lists, checklists, bold, italic, strikethrough, code and mi's own tags. */
fun fromMarkdown(md: String): RichText {
    val text = StringBuilder()
    val spans = ArrayList<Span>()
    md.replace("\r\n", "\n").lines().forEachIndexed { li, rawLine ->
        if (li > 0) text.append('\n')
        val lineStart = text.length
        var line = rawLine.removeSuffix("  ")
        val indent = line.takeWhile { it == ' ' }.length
        line = line.trimStart(' ')
        var heading = 0f
        var para: Span? = null
        val level = (indent / 2).coerceAtMost(6)
        Regex("^[-*+] \\[([ xX])] ").find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "check", level, checked = m.groupValues[1] != " "); line = line.substring(m.range.last + 1)
        } ?: Regex("^[-*+] ").find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "bullet", level); line = line.substring(m.range.last + 1)
        } ?: Regex("^\\d+[.)] ").find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "number", (indent / 3).coerceAtMost(6)); line = line.substring(m.range.last + 1)
        } ?: Regex("^(#{1,6}) ").find(line)?.let { m ->
            heading = when (m.groupValues[1].length) { 1 -> 2f; 2 -> 1.5f; else -> 1.25f }; line = line.substring(m.range.last + 1)
        }
        parseInline(line, text, spans)
        val lineEnd = text.length
        para?.let { spans += it.copy(start = lineStart, end = lineEnd) }
        if (heading > 0f && lineEnd > lineStart) {
            spans += Span(lineStart, lineEnd, SpanType.SIZE, heading.toString())
            spans += Span(lineStart, lineEnd, SpanType.WEIGHT, if (heading >= 2f) "800" else "700")
        }
    }
    return RichText(text.toString(), spans)
}

private val INLINE = listOf(
    Regex("\\*\\*(.+?)\\*\\*") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.WEIGHT, "700")) },
    Regex("__(.+?)__") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.WEIGHT, "700")) },
    Regex("~~(.+?)~~") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.STRIKE)) },
    Regex("\\*(.+?)\\*") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.ITALIC)) },
    Regex("(?<![\\w])_(.+?)_(?![\\w])") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.ITALIC)) },
    Regex("`(.+?)`") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.FONT, "mono")) },
    Regex("<u>(.+?)</u>") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.UNDERLINE)) },
    Regex("<mark>(.+?)</mark>") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.HIGHLIGHT, "#FFD43B")) },
    Regex("==(.+?)==") to { s: Int, e: Int -> listOf(Span(s, e, SpanType.HIGHLIGHT, "#FFD43B")) },
)
private val COLOR_TAG = Regex("<span style=\"color:\\s*(#[0-9A-Fa-f]{6})\">(.+?)</span>")
private val IMAGE_MD = Regex("!\\[([^\\]]*)]\\([^)]*\\)")

/** Appends [s] to [out] without its inline markers, adding a span for each. Nested markers are read recursively. */
private fun parseInline(s: String, out: StringBuilder, spans: MutableList<Span>) {
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length) { out.append(s[i + 1]); i += 2; continue }
        // the earliest marker from here (ties: the order of INLINE, so ** wins over *)
        var best: Pair<MatchResult, ((Int, Int) -> List<Span>)?>? = null
        for ((re, make) in INLINE) re.find(s, i)?.let { m -> if (best == null || m.range.first < best!!.first.range.first) best = m to make }
        COLOR_TAG.find(s, i)?.let { m -> if (best == null || m.range.first < best!!.first.range.first) best = m to null }
        IMAGE_MD.find(s, i)?.let { m -> if (best == null || m.range.first < best!!.first.range.first) best = m to null }
        val b = best
        val nextEscape = s.indexOf('\\', i).let { if (it < 0) s.length else it }
        if (b == null || nextEscape < b.first.range.first) {
            val stop = minOf(nextEscape, b?.first?.range?.first ?: s.length)
            out.append(s, i, stop); i = stop; continue
        }
        val (m, make) = b
        out.append(s, i, m.range.first)
        val start = out.length
        when {
            m.value.startsWith("![") -> out.append(m.groupValues[1]) // images aren't carried in: their alt text stays
            make == null -> { parseInline(m.groupValues[2], out, spans); spans += Span(start, out.length, SpanType.COLOR, m.groupValues[1].uppercase()) }
            else -> { parseInline(m.groupValues[1], out, spans); spans += make(start, out.length) }
        }
        i = m.range.last + 1
    }
}
