// The editor's styled text to a note's saved body (RichText) and back. RichText is plain data (text + spans), so this
// is the one place that knows which span class stands for which kind of formatting.
package com.jlees.minotes.editor

import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import com.jlees.minotes.notes.IMAGE_CHAR
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import com.jlees.minotes.notes.TextFont

/**
 * The editor's text always ends with one extra line break that isn't part of the note: Android never draws a list
 * marker on the last line when it's empty, so without it a new empty bullet or checkbox at the end would show nothing.
 * [RichCodec.load] adds it, [RichCodec.toRich] takes it off, and [RichCodec.keepTrailingLine] puts it back after edits.
 */
object RichCodec {
    /** Puts the trailing line break back if an edit took it away. Returns whether it changed the text. */
    fun keepTrailingLine(t: Editable): Boolean {
        if (t.isNotEmpty() && t[t.length - 1] == '\n') return false
        t.append('\n')
        // formatting that grew over the new line break (styles and paragraphs take in what's added at their end) stops before it
        val end = t.length
        for (sp in t.getSpans(0, end, Any::class.java)) if (isOurs(sp) && t.getSpanEnd(sp) == end) {
            val a = t.getSpanStart(sp); val f = t.getSpanFlags(sp)
            t.removeSpan(sp)
            if (end - 1 > a || sp is ParaSpan || sp is AlignSpan || sp is BlockSpan) t.setSpan(sp, minOf(a, end - 1), end - 1, f)
        }
        return true
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    private fun color(s: String) = s.removePrefix("#").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

    fun toRich(t: Spanned): RichText {
        // the note is the text without the editor's trailing line break (and without anything on the line after it)
        val n = if (t.isNotEmpty() && t[t.length - 1] == '\n') t.length - 1 else t.length
        val out = ArrayList<Span>()
        for (sp in t.getSpans(0, t.length, Any::class.java)) {
            val a = t.getSpanStart(sp); val b = minOf(t.getSpanEnd(sp), n)
            if (a < 0 || a > n || b < a) continue
            out += when (sp) {
                is WeightSpan -> Span(a, b, SpanType.WEIGHT, sp.weight.toString())
                is ItalicSpan -> Span(a, b, SpanType.ITALIC)
                is UnderlinedSpan -> Span(a, b, SpanType.UNDERLINE)
                is StrikeSpan -> Span(a, b, SpanType.STRIKE)
                is ColorSpan -> Span(a, b, SpanType.COLOR, hex(sp.foregroundColor))
                is HighlightSpan -> Span(a, b, SpanType.HIGHLIGHT, hex(sp.base))
                is SizeSpan -> Span(a, b, SpanType.SIZE, sp.scale.toString())
                is FontSpan -> Span(a, b, SpanType.FONT, sp.font.name.lowercase())
                is ParaSpan -> Span(a, b, SpanType.PARA, sp.kind.name.lowercase(), sp.level, sp.checked)
                is AlignSpan -> Span(a, b, SpanType.ALIGN, if (sp.align == Layout.Alignment.ALIGN_CENTER) "center" else "end")
                is NoteImageSpan -> Span(a, b, SpanType.IMAGE, sp.name, width = sp.width)
                is BlockSpan -> Span(a, b, SpanType.BLOCK, sp.kind.id)
                is DividerSpan -> Span(a, b, SpanType.DIVIDER)
                else -> continue // the keyboard's own spans (composing, spell check), the derived DoneSpan
            }.let { if (it.type in PARAS && it.end > it.start && t[it.end - 1] == '\n') it.copy(end = it.end - 1) else it } // saved without its line break
                .takeIf { it.end > it.start || it.type in PARAS } ?: continue
        }
        // one order whatever order Android keeps spans in, so an unchanged note compares equal (and isn't marked edited)
        out.sortWith(compareBy<Span>({ it.start }, { it.end }, { it.type }, { it.value }))
        return RichText(t.subSequence(0, n).toString(), out)
    }

    /**
     * Replaces [t]'s text and formatting with [rt]'s. [image] loads a picture by file name and width (null: not found);
     * [pageWidth] is how wide dividers draw.
     */
    fun load(t: Editable, rt: RichText, image: (String, Int) -> Drawable?, pageWidth: () -> Int = { 1 }) {
        t.getSpans(0, t.length, Any::class.java).filter { isOurs(it) }.forEach { t.removeSpan(it) }
        t.replace(0, t.length, rt.text + "\n")
        val n = t.length - 1
        for (s in rt.spans) {
            val a = s.start.coerceIn(0, n); val b = s.end.coerceIn(a, n)
            val sp: Any = when (s.type) {
                SpanType.WEIGHT -> WeightSpan(s.value.toIntOrNull()?.coerceIn(1, 1000) ?: continue)
                SpanType.ITALIC -> ItalicSpan()
                SpanType.UNDERLINE -> UnderlinedSpan()
                SpanType.STRIKE -> StrikeSpan()
                SpanType.COLOR -> ColorSpan(color(s.value) ?: continue)
                SpanType.HIGHLIGHT -> HighlightSpan(color(s.value) ?: continue)
                SpanType.SIZE -> SizeSpan(s.value.toFloatOrNull()?.coerceIn(0.5f, 4f) ?: continue)
                SpanType.FONT -> FontSpan(TextFont.entries.firstOrNull { it.name.equals(s.value, true) } ?: continue)
                SpanType.PARA -> ParaSpan(ListKind.of(s.value), s.level.coerceIn(0, MAX_LEVEL), s.checked)
                SpanType.ALIGN -> AlignSpan(if (s.value == "center") Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_OPPOSITE)
                SpanType.IMAGE -> if (b == a + 1 && t[a] == IMAGE_CHAR) NoteImageSpan(s.value, s.width, image(s.value, s.width) ?: continue) else continue
                SpanType.BLOCK -> BlockSpan(BlockKind.entries.firstOrNull { it.id == s.value } ?: continue)
                SpanType.UNKNOWN -> continue
                SpanType.DIVIDER -> if (b == a + 1 && t[a] == IMAGE_CHAR) DividerSpan(pageWidth) else continue
            }
            val para = s.type in PARAS
            if (!para && b <= a) continue
            t.setSpan(sp, a, b, when { para -> PARA_FLAGS; s.type in OBJECTS -> Spanned.SPAN_EXCLUSIVE_EXCLUSIVE; else -> INLINE_FLAGS })
        }
        // a placeholder with nothing to draw (a picture whose file is gone, a kind this version doesn't know) would show
        // as a boxed "OBJ": it goes, with its line when it had one to itself
        for (i in n - 1 downTo 0) {
            if (t[i] != IMAGE_CHAR || t.getSpans(i, i + 1, Any::class.java).any { (it is NoteImageSpan || it is DividerSpan) && t.getSpanStart(it) == i }) continue
            val alone = (i == 0 || t[i - 1] == '\n') && t[i + 1] == '\n'
            t.delete(i, if (alone) i + 2 else i + 1)
        }
        normalizeParagraphs(t)
    }

    private fun isOurs(sp: Any) = sp is WeightSpan || sp is ItalicSpan || sp is UnderlinedSpan || sp is StrikeSpan || sp is ColorSpan ||
        sp is HighlightSpan || sp is SizeSpan || sp is FontSpan || sp is ParaSpan || sp is AlignSpan || sp is NoteImageSpan || sp is DoneSpan || sp is BlockSpan || sp is DividerSpan

    /** The kinds that stand on a placeholder character of their own (pictures, dividers). */
    private val OBJECTS = setOf(SpanType.IMAGE, SpanType.DIVIDER)
    /** The kinds that cover whole paragraphs (kept even on an empty one). */
    private val PARAS = setOf(SpanType.PARA, SpanType.ALIGN, SpanType.BLOCK)
}
