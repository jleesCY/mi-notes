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

object RichCodec {
    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    private fun color(s: String) = s.removePrefix("#").toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

    fun toRich(t: Spanned): RichText {
        val out = ArrayList<Span>()
        for (sp in t.getSpans(0, t.length, Any::class.java)) {
            val a = t.getSpanStart(sp); val b = t.getSpanEnd(sp)
            if (a < 0 || b < a) continue
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
                is NoteImageSpan -> Span(a, b, SpanType.IMAGE, sp.name)
                else -> continue // the keyboard's own spans (composing, spell check), the derived DoneSpan
            }.takeIf { b > a || it.type == SpanType.PARA || it.type == SpanType.ALIGN } ?: continue
        }
        // one order whatever order Android keeps spans in, so an unchanged note compares equal (and isn't marked edited)
        out.sortWith(compareBy<Span>({ it.start }, { it.end }, { it.type }, { it.value }))
        return RichText(t.toString(), out)
    }

    /** Replaces [t]'s text and formatting with [rt]'s. [image] loads a picture by its file name (null: not found). */
    fun load(t: Editable, rt: RichText, image: (String) -> Drawable?) {
        t.getSpans(0, t.length, Any::class.java).filter { isOurs(it) }.forEach { t.removeSpan(it) }
        t.replace(0, t.length, rt.text)
        val n = t.length
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
                SpanType.PARA -> ParaSpan(ListKind.entries.firstOrNull { it.name.equals(s.value, true) } ?: ListKind.NONE, s.level.coerceIn(0, MAX_LEVEL), s.checked)
                SpanType.ALIGN -> AlignSpan(if (s.value == "center") Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_OPPOSITE)
                SpanType.IMAGE -> if (b == a + 1 && t[a] == IMAGE_CHAR) NoteImageSpan(s.value, image(s.value) ?: continue) else continue
            }
            val para = s.type == SpanType.PARA || s.type == SpanType.ALIGN
            if (!para && b <= a) continue
            t.setSpan(sp, a, b, when { para -> PARA_FLAGS; s.type == SpanType.IMAGE -> Spanned.SPAN_EXCLUSIVE_EXCLUSIVE; else -> INLINE_FLAGS })
        }
        normalizeParagraphs(t)
    }

    private fun isOurs(sp: Any) = sp is WeightSpan || sp is ItalicSpan || sp is UnderlinedSpan || sp is StrikeSpan || sp is ColorSpan ||
        sp is HighlightSpan || sp is SizeSpan || sp is FontSpan || sp is ParaSpan || sp is AlignSpan || sp is NoteImageSpan || sp is DoneSpan
}
