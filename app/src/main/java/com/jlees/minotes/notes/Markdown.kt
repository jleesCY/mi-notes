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
    val paras = paragraphs(text)
    paras.forEachIndexed { pi, (s, e) ->
        if (pi > 0) out.append('\n')
        // a divider stands alone, with blank lines around it (Markdown needs them: a "---" right under a line of text
        // would turn it into a heading)
        val block = dividerAt(rt, s, e)
        if (block != null) {
            if (pi > 0 && paras[pi - 1].let { (ps, pe) -> pe > ps }) out.append('\n')
            out.append(block)
            if (pi < paras.lastIndex && paras[pi + 1].let { (ns, ne) -> ne > ns }) out.append('\n')
            numbers.fill(0)
            return@forEachIndexed
        }
        // a code block: its lines as typed, between fences
        val kindHere = blockKind(rt, s)
        val prevKind = if (pi > 0) blockKind(rt, paras[pi - 1].first) else null
        val nextKind = if (pi < paras.lastIndex) blockKind(rt, paras[pi + 1].first) else null
        if (kindHere == BlockKinds.CODE) {
            if (prevKind != BlockKinds.CODE) out.append("```\n")
            out.append(text, s, e)
            if (nextKind != BlockKinds.CODE) out.append("\n```")
            numbers.fill(0)
            return@forEachIndexed
        }
        // a quote or callout: each line marked, a callout's first with its "[!NOTE]" line
        when (kindHere) {
            BlockKinds.QUOTE -> out.append("> ")
            BlockKinds.CALLOUT -> out.append(if (prevKind == BlockKinds.CALLOUT) "> " else "> [!NOTE]\n> ")
        }
        val para = rt.spans.firstOrNull { it.type == SpanType.PARA && it.start <= s && it.end >= s }
        val kind = ListKind.of(para?.value)
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
        if (kind == ListKind.NONE && heading.isEmpty() && e < text.length && nextKind == kindHere) {
            val ns = e + 1; val ne = text.indexOf('\n', ns).let { if (it < 0) text.length else it }
            val nextIsList = rt.spans.any { it.type == SpanType.PARA && it.start <= ns && it.end >= ns && !it.value.equals("none", true) }
            if (ne > ns && !nextIsList) out.append("  ")
        }
        // a quote or callout ends at a blank line (without one, the next line would be read as part of it)
        if (kindHere != null && nextKind != kindHere && pi < paras.lastIndex) out.append('\n')
    }
    return out.toString()
}

/** The paragraph [s]..[e] as a Markdown divider, when it's one (a placeholder of its own); else null. */
private fun dividerAt(rt: RichText, s: Int, e: Int): String? {
    if (e != s + 1 || rt.text[s] != IMAGE_CHAR) return null
    return if (rt.spans.any { it.start == s && it.type == SpanType.DIVIDER }) "---" else null
}

/** The block (quote, code, callout) the paragraph starting at [s] is, or null. */
private fun blockKind(rt: RichText, s: Int): String? = rt.spans.firstOrNull { it.type == SpanType.BLOCK && it.start == s }?.value

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
            // a resized picture keeps its size as an <img> tag (Markdown's own syntax has none)
            rt.spans.firstOrNull { it.type == SpanType.IMAGE && it.start == i }?.let {
                sb.append(if (it.width in 1..99) "<img src=\"images/${it.value}\" width=\"${it.width}%\">" else "![](images/${it.value})")
            }
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
    // (or, a line of "---", a divider; one starting ">" or "```", a quote or code)
    if (atLineStart && MARKDOWN_START.containsMatchIn(b)) b.insert(0, '\\')
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

/** Markdown read back into a note body: headings, lists, checklists, quotes, callouts, code blocks, dividers, bold, italic, strikethrough, code and mi's own tags. */
fun fromMarkdown(md: String): RichText {
    val text = StringBuilder()
    val spans = ArrayList<Span>()
    val all = md.replace("\r\n", "\n").lines()
    // dividers, quotes, callouts and code first: a divider becomes one placeholder line (the blank lines Markdown needs
    // around it go), the others lines that know their block (a quote's blank line after it goes too)
    val lines = ArrayList<Any>() // a Line, or a Span for a divider
    var k = 0
    while (k < all.size) {
        val l = all[k]
        val t = l.trim()
        if (t.startsWith("```")) {
            k++
            while (k < all.size && all[k].trim() != "```") { lines += Line(all[k], BlockKinds.CODE, raw = true); k++ }
            k++ // the closing fence
            continue
        }
        if (t.startsWith(">")) {
            val callout = CALLOUT_LINE.matches(t)
            if (callout) k++
            while (k < all.size && all[k].trim().startsWith(">")) {
                lines += Line(all[k].trim().removePrefix(">").removePrefix(" "), if (callout) BlockKinds.CALLOUT else BlockKinds.QUOTE)
                k++
            }
            if (k < all.size && all[k].isBlank() && k + 1 < all.size) k++
            continue
        }
        if (DIVIDER_LINE.matches(t)) {
            if ((lines.lastOrNull() as? Line)?.let { it.text.isEmpty() && it.block == null } == true) lines.removeAt(lines.lastIndex)
            lines += Span(0, 0, SpanType.DIVIDER)
            k++
            if (k < all.size && all[k].isBlank()) k++
            continue
        }
        lines += Line(l); k++
    }
    lines.forEachIndexed { li, item ->
        if (li > 0) text.append('\n')
        if (item is Span) { spans += item.copy(start = text.length, end = text.length + 1); text.append(IMAGE_CHAR); return@forEachIndexed }
        val ln = item as Line
        val lineStart = text.length
        if (ln.raw) { text.append(ln.text); spans += Span(lineStart, text.length, SpanType.BLOCK, ln.block!!); return@forEachIndexed }
        val rawLine = ln.text
        var line = rawLine.removeSuffix("  ")
        val indent = line.takeWhile { it == ' ' }.length
        line = line.trimStart(' ')
        var heading = 0f
        var para: Span? = null
        val level = (indent / 2).coerceAtMost(6)
        CHECK_ITEM.find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "check", level, checked = m.groupValues[1] != " "); line = line.substring(m.range.last + 1)
        } ?: BULLET_ITEM.find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "bullet", level); line = line.substring(m.range.last + 1)
        } ?: NUMBER_ITEM.find(line)?.let { m ->
            para = Span(0, 0, SpanType.PARA, "number", (indent / 3).coerceAtMost(6)); line = line.substring(m.range.last + 1)
        } ?: HEADING.find(line)?.let { m ->
            heading = when (m.groupValues[1].length) { 1 -> 2f; 2 -> 1.5f; else -> 1.25f }; line = line.substring(m.range.last + 1)
        }
        parseInline(line, text, spans)
        val lineEnd = text.length
        para?.let { spans += it.copy(start = lineStart, end = lineEnd) }
        ln.block?.let { spans += Span(lineStart, lineEnd, SpanType.BLOCK, it) }
        if (heading > 0f && lineEnd > lineStart) {
            spans += Span(lineStart, lineEnd, SpanType.SIZE, heading.toString())
            spans += Span(lineStart, lineEnd, SpanType.WEIGHT, if (heading >= 2f) "800" else "700")
        }
    }
    return RichText(text.toString(), spans)
}

// what a line starts with, in Markdown (built once: an import or export runs them on every line)
/** A start a plain line must not have, or it would read back as Markdown: a heading, a list, a divider, a quote, code. */
private val MARKDOWN_START = Regex("^(#|[-+] |\\d+\\. |-{3,}$|\\*{3,}$|_{3,}$|>|```)")
private val CHECK_ITEM = Regex("^[-*+] \\[([ xX])] ")
private val BULLET_ITEM = Regex("^[-*+] ")
private val NUMBER_ITEM = Regex("^\\d+[.)] ")
private val HEADING = Regex("^(#{1,6}) ")
private val DIVIDER_LINE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
/** A GitHub alert's first line ("> [!NOTE]", or TIP, IMPORTANT, WARNING, CAUTION): a callout. */
private val CALLOUT_LINE = Regex("^>\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*$", RegexOption.IGNORE_CASE)

/** A line of imported text, the block it's in, and whether it's [raw] (code: no Markdown read in it). */
private data class Line(val text: String, val block: String? = null, val raw: Boolean = false)

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
