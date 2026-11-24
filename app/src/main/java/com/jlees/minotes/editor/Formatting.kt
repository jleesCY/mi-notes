// What the format controls do to the text: inline styles (weight, italic, colour, size…) over a selection, and
// paragraph formats (lists, checkboxes, indent, alignment) over the paragraphs a selection touches. Also the clean-up
// that keeps paragraph formats on whole paragraphs as text is typed, split and joined. Tested in FormattingTest.
package com.jlees.minotes.editor

import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.TextFont

/** Inline styles; their values: weight Int (400 regular), size Float (1 = body), colour and highlight Int ARGB (0 = none), font, and true/false for the rest. */
enum class Inline { WEIGHT, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE, FONT }

/** Typing at the end of styled text carries the style on; typing just before it doesn't. */
const val INLINE_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE
/** Paragraph spans take in whatever is typed anywhere in their paragraph, an empty one included. */
const val PARA_FLAGS = Spanned.SPAN_INCLUSIVE_INCLUSIVE
const val MAX_LEVEL = 6

fun defaultOf(k: Inline): Any = when (k) {
    Inline.WEIGHT -> 400; Inline.SIZE -> 1f; Inline.COLOR, Inline.HIGHLIGHT -> 0; Inline.FONT -> TextFont.SANS
    Inline.ITALIC, Inline.UNDERLINE, Inline.STRIKE -> false
}

private fun classOf(k: Inline): Class<*> = when (k) {
    Inline.WEIGHT -> WeightSpan::class.java; Inline.ITALIC -> ItalicSpan::class.java; Inline.UNDERLINE -> UnderlinedSpan::class.java
    Inline.STRIKE -> StrikeSpan::class.java; Inline.COLOR -> ColorSpan::class.java; Inline.HIGHLIGHT -> HighlightSpan::class.java
    Inline.SIZE -> SizeSpan::class.java; Inline.FONT -> FontSpan::class.java
}

/** The span for [k] at [v], or null when [v] is the default (no span needed). */
fun makeSpan(k: Inline, v: Any): Any? = if (v == defaultOf(k)) null else when (k) {
    Inline.WEIGHT -> WeightSpan(v as Int); Inline.ITALIC -> ItalicSpan(); Inline.UNDERLINE -> UnderlinedSpan(); Inline.STRIKE -> StrikeSpan()
    Inline.COLOR -> ColorSpan(v as Int); Inline.HIGHLIGHT -> HighlightSpan(v as Int); Inline.SIZE -> SizeSpan(v as Float); Inline.FONT -> FontSpan(v as TextFont)
}

private fun valueOf(sp: Any): Any = when (sp) {
    is WeightSpan -> sp.weight; is ColorSpan -> sp.foregroundColor; is HighlightSpan -> sp.base; is SizeSpan -> sp.scale; is FontSpan -> sp.font
    else -> true
}

/** Sets [k] to [v] over [s, e): any [k] already there is cut back to outside the range; the default clears it. */
fun applyInline(t: Editable, s: Int, e: Int, k: Inline, v: Any) {
    if (s >= e) return
    for (sp in t.getSpans(s, e, classOf(k))) {
        val a = t.getSpanStart(sp); val b = t.getSpanEnd(sp)
        if (b <= s || a >= e) continue
        val old = valueOf(sp)
        t.removeSpan(sp)
        if (a < s) makeSpan(k, old)?.let { t.setSpan(it, a, s, INLINE_FLAGS) }
        if (b > e) makeSpan(k, old)?.let { t.setSpan(it, e, b, INLINE_FLAGS) }
    }
    makeSpan(k, v)?.let { t.setSpan(it, s, e, INLINE_FLAGS) }
}

/**
 * [k]'s value over [s, e): the one every character has, or null when they differ. An empty selection reads the
 * character before the cursor (what typing would continue), or after it at the very start.
 */
fun inlineValue(t: Spanned, s: Int, e: Int, k: Inline): Any? {
    val (a, b) = if (s < e) s to e else if (s > 0) s - 1 to s else 0 to minOf(1, t.length)
    if (a >= b) return defaultOf(k)
    val spans = t.getSpans(a, b, classOf(k))
    var result: Any? = null
    for (i in a until b) {
        if (t[i] == '\n' && b - a > 1) continue // a selection's line breaks don't count
        val v = spans.lastOrNull { t.getSpanStart(it) <= i && t.getSpanEnd(it) > i }?.let { valueOf(it) } ?: defaultOf(k)
        if (result == null) result = v else if (result != v) return null
    }
    return result ?: defaultOf(k)
}

// ---------------------------------------------------------------- paragraphs

/** The starts of the paragraphs [s, e] touches. */
fun paragraphStarts(t: CharSequence, s: Int, e: Int): List<Int> {
    val out = ArrayList<Int>()
    var p = paragraphStart(t, s)
    val last = paragraphStart(t, e)
    while (true) { out += p; if (p >= last) break; p = paragraphEnd(t, p) + 1 }
    return out
}

/** Sets the list format of the paragraph starting at [start]; no list and no indent removes it. */
fun setPara(t: Editable, start: Int, kind: ListKind, level: Int, checked: Boolean = false) {
    val end = paragraphEnd(t, start)
    t.getSpans(start, end, ParaSpan::class.java).filter { paragraphStart(t, t.getSpanStart(it)) == start }.forEach { t.removeSpan(it) }
    if (kind != ListKind.NONE || level > 0) t.setSpan(ParaSpan(kind, level.coerceIn(0, MAX_LEVEL), checked && kind == ListKind.CHECK), start, end, PARA_FLAGS)
}

/** Turns [kind] on for the paragraphs in [s, e], or off when they all have it already. Indents are kept. */
fun toggleList(t: Editable, s: Int, e: Int, kind: ListKind) {
    val starts = paragraphStarts(t, s, e)
    val allOn = starts.all { paraAt(t, it)?.kind == kind }
    for (p in starts) {
        val cur = paraAt(t, p)
        setPara(t, p, if (allOn) ListKind.NONE else kind, cur?.level ?: 0, checked = !allOn && cur?.kind == kind && cur.checked)
    }
}

/** Indents (or with a negative [delta], outdents) the paragraphs in [s, e]. */
fun indent(t: Editable, s: Int, e: Int, delta: Int) {
    for (p in paragraphStarts(t, s, e)) {
        val cur = paraAt(t, p)
        setPara(t, p, cur?.kind ?: ListKind.NONE, ((cur?.level ?: 0) + delta).coerceIn(0, MAX_LEVEL), cur?.checked == true)
    }
}

/** Aligns the paragraphs in [s, e]; NORMAL (start) removes the alignment. */
fun setAlign(t: Editable, s: Int, e: Int, align: Layout.Alignment) {
    for (p in paragraphStarts(t, s, e)) {
        val end = paragraphEnd(t, p)
        t.getSpans(p, end, AlignSpan::class.java).filter { paragraphStart(t, t.getSpanStart(it)) == p }.forEach { t.removeSpan(it) }
        if (align != Layout.Alignment.ALIGN_NORMAL) t.setSpan(AlignSpan(align), p, end, PARA_FLAGS)
    }
}

fun alignAt(t: Spanned, pos: Int): Layout.Alignment {
    val p = paragraphStart(t, pos)
    return t.getSpans(p, paragraphEnd(t, p), AlignSpan::class.java).firstOrNull { paragraphStart(t, t.getSpanStart(it)) == p }?.align ?: Layout.Alignment.ALIGN_NORMAL
}

/**
 * Ticks or unticks the checklist item starting at [start]. With [toBottom], a ticked item moves below the rest of its
 * checklist (the run of checklist paragraphs it's in). Returns where the item now starts.
 */
fun toggleCheck(t: Editable, start: Int, toBottom: Boolean): Int {
    val p = paraAt(t, start)?.takeIf { it.kind == ListKind.CHECK } ?: return start
    setPara(t, start, ListKind.CHECK, p.level, !p.checked)
    if (!toBottom || p.checked) return start
    var lastStart = start
    while (true) {
        val next = paragraphEnd(t, lastStart) + 1
        if (next > t.length || paraAt(t, next)?.kind != ListKind.CHECK) break
        lastStart = next
    }
    if (lastStart == start) return start
    val end = paragraphEnd(t, start)
    val piece = SpannableStringBuilder(t.subSequence(start, end))
    val blockEnd = paragraphEnd(t, lastStart)
    // put the item after the last one, then take out the original (which sits before it, so the insert point holds)
    t.insert(blockEnd, SpannableStringBuilder("\n").append(piece))
    t.delete(start, end + 1)
    normalizeParagraphs(t)
    // the item's own format, set again: a moved span can be taken over by the line it was inserted after
    val now = blockEnd - (end - start)
    setPara(t, now, ListKind.CHECK, p.level, true)
    return now
}

/**
 * Keeps every paragraph format on exactly one whole paragraph, after any edit. Typing grows a paragraph's span; Enter
 * splits a paragraph and its span with it (so a list carries on into the new line, unticked); joining two paragraphs
 * leaves two spans on one, and the first paragraph's wins. Each span first takes the paragraph it starts in, then any
 * other paragraph it spread over that has no format of its own. Where two start together, the longer wins.
 */
fun normalizeParagraphs(t: Editable) {
    for (cls in listOf(ParaSpan::class.java, AlignSpan::class.java)) {
        val all = t.getSpans(0, t.length, cls).map { Triple(it, t.getSpanStart(it), t.getSpanEnd(it)) }
            // a deleted paragraph's span is left squashed to nothing at the next one's start: the next one's own wins
            .sortedWith(compareBy({ it.second }, { -it.third }))
        if (all.isEmpty()) continue
        all.forEach { t.removeSpan(it.first) }
        val placed = HashMap<Int, Any>()
        for ((sp, a, _) in all) { val p = paragraphStart(t, a); if (p !in placed) placed[p] = sp }
        for ((sp, a, b) in all) {
            for (p in paragraphStarts(t, a, b)) if (p !in placed) placed[p] = when (sp) { is ParaSpan -> ParaSpan(sp.kind, sp.level); else -> AlignSpan((sp as AlignSpan).align) }
        }
        for ((p, sp) in placed) t.setSpan(sp, p, paragraphEnd(t, p), PARA_FLAGS)
    }
}

/** Crosses out ticked checklist items' text ([faded]: their text colour). Run after every change. */
fun refreshDone(t: Editable, faded: Int) {
    t.getSpans(0, t.length, DoneSpan::class.java).forEach { t.removeSpan(it) }
    for (p in t.getSpans(0, t.length, ParaSpan::class.java)) {
        if (p.kind != ListKind.CHECK || !p.checked) continue
        val s = t.getSpanStart(p); val e = t.getSpanEnd(p)
        if (e > s) t.setSpan(DoneSpan(faded), s, e, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }
}

/**
 * Enter in an empty list item ends the list there, as in any notes app: [nl] is where a newline was just typed. When
 * the item is empty on both sides of it, the newline comes out again and the item becomes a plain paragraph.
 */
fun endListOnEmptyEnter(t: Editable, nl: Int): Boolean {
    if (nl !in 0 until t.length || t[nl] != '\n') return false
    val s = paragraphStart(t, nl)
    val p = paraAt(t, s) ?: return false
    val afterEnd = paragraphEnd(t, nl + 1)
    if (p.kind == ListKind.NONE || s != nl || afterEnd != nl + 1) return false
    t.delete(nl, nl + 1)
    normalizeParagraphs(t)
    // an indented item steps out a level first; at the outside, the list ends
    if (p.level > 0) setPara(t, s, p.kind, p.level - 1) else setPara(t, s, ListKind.NONE, 0)
    return true
}

/**
 * Backspace at the very start of a list item or indented paragraph takes its marker off (then its indent) instead of
 * joining it to the line above. Returns false when there's nothing to take off and the backspace should go ahead.
 */
fun backspaceAtParagraphStart(t: Editable, cursor: Int): Boolean {
    if (paragraphStart(t, cursor) != cursor) return false
    val p = paraAt(t, cursor) ?: return false
    when {
        p.kind != ListKind.NONE -> setPara(t, cursor, ListKind.NONE, p.level)
        p.level > 0 -> setPara(t, cursor, ListKind.NONE, p.level - 1)
        else -> return false
    }
    return true
}

/** Text style presets (Title, Heading, Subheading, Body): a size and weight for the paragraphs in [s, e]. */
enum class TextStyle(val label: String, val size: Float, val weight: Int) {
    TITLE("Title", 1.75f, 800), HEADING("Heading", 1.4f, 700), SUBHEADING("Subheading", 1.15f, 600), BODY("Body", 1f, 400);
    companion object {
        fun of(size: Any?, weight: Any?) = entries.firstOrNull { it.size == size && it.weight == weight }
    }
}

fun applyStyle(t: Editable, s: Int, e: Int, style: TextStyle) {
    val ps = paragraphStart(t, s); val pe = paragraphEnd(t, e)
    applyInline(t, ps, pe, Inline.SIZE, style.size)
    applyInline(t, ps, pe, Inline.WEIGHT, style.weight)
}
