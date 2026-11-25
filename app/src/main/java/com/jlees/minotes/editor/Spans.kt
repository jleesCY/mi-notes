// The spans the editor draws formatting with. Each saved kind of formatting (notes/Note.kt SpanType) has one class here,
// so reading a note back out of the editor (RichCodec) is a matter of which class a span is. The editor is Android's
// own EditText: it already knows how to type, select, copy and paste styled text, and these spans tell it what to draw.
package com.jlees.minotes.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.text.style.UpdateAppearance
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.TextFont

/**
 * Sets one axis of a variable font (Google Sans Flex: "wght", "ROND") on [p], keeping the others already set, so a
 * weight span and a rounded-font span over the same text both apply whichever runs first.
 */
internal fun setAxis(p: Paint, tag: String, value: Float): Boolean {
    val axes = (p.fontVariationSettings ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("'$tag'") }
    return p.setFontVariationSettings((axes + "'$tag' $value").joinToString(", "))
}

/** Typefaces the spans use; the editor sets [sans] (Google Sans Flex) before any text is drawn. */
object EditorFonts { var sans: Typeface = Typeface.DEFAULT }

class WeightSpan(val weight: Int) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    private fun apply(p: TextPaint) {
        // a typeface without a weight axis (serif, mono) gets the nearest real weight instead
        if (!setAxis(p, "wght", weight.toFloat())) {
            p.typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(p.typeface, weight.coerceIn(1, 1000), p.typeface?.isItalic == true)
                else Typeface.create(p.typeface, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}

class ItalicSpan : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    // Google Sans Flex has no italic: slant it, as Android does for any typeface without one
    private fun apply(p: TextPaint) { p.textSkewX = -0.22f }
}

class FontSpan(val font: TextFont) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    private fun apply(p: TextPaint) {
        when (font) {
            TextFont.SANS -> p.typeface = EditorFonts.sans
            TextFont.ROUNDED -> { p.typeface = EditorFonts.sans; setAxis(p, "ROND", 100f) }
            TextFont.SERIF -> p.typeface = Typeface.SERIF
            TextFont.MONO -> p.typeface = Typeface.MONOSPACE
        }
    }
}

class ColorSpan(color: Int) : ForegroundColorSpan(color)
/** A highlight in colour [base], drawn see-through so the text reads on it in light and dark themes alike. */
class HighlightSpan(val base: Int) : BackgroundColorSpan((base and 0xFFFFFF) or (0x70 shl 24))
class SizeSpan(val scale: Float) : RelativeSizeSpan(scale)
class UnderlinedSpan : UnderlineSpan()
class StrikeSpan : StrikethroughSpan()

/** A paragraph's alignment (centre or end; start is the absence of one). */
class AlignSpan(val align: Layout.Alignment) : AlignmentSpan { override fun getAlignment() = align }

/**
 * A picture in the note, on its one [com.jlees.minotes.notes.IMAGE_CHAR]. [name] is its file in the note's images;
 * [width] its width in percent of the page (0: the full width).
 */
class NoteImageSpan(val name: String, val width: Int, d: Drawable) : ImageSpan(d, ALIGN_BOTTOM) {
    /** Where it was last drawn, in the text layout's coordinates (the resize frame is drawn on exactly this). */
    var drawnX = 0f; private set
    var drawnTop = 0f; private set

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        drawnX = x; drawnTop = (bottom - drawable.bounds.bottom).toFloat() // as ImageSpan places an ALIGN_BOTTOM picture
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }
}

/** Crossed out and faded: a ticked checklist item's text. Not saved: it follows the item's [ParaSpan.checked]. */
class DoneSpan(val faded: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) { tp.isStrikeThruText = true; tp.color = faded }
}

/**
 * A paragraph's list marker and indent: [kind] (bullet, number, checkbox, or none for an indented plain paragraph),
 * [level] of indent, and for a checkbox whether it's [checked]. It leaves room on the left and draws the marker in it.
 */
class ParaSpan(val kind: ListKind, val level: Int, val checked: Boolean = false) : LeadingMarginSpan {
    /** Sizes and colours, set by the editor from the theme and screen density. */
    object Look {
        var indent = 48; var marker = 56; var ink = 0xFFFFFFFF.toInt(); var accent = 0xFFFFCC00.toInt(); var onAccent = 0xFF000000.toInt(); var density = 3f
    }

    override fun getLeadingMargin(first: Boolean) = level * Look.indent + if (kind == ListKind.NONE) 0 else Look.marker

    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        if (!first || kind == ListKind.NONE || text !is Spanned) return
        val left = x + dir * level * Look.indent
        val d = Look.density
        val saved = p.color; val style = p.style; val stroke = p.strokeWidth
        // the marker sits on the first line's text: centred on its x-height, not the line's (taller, if it holds a picture)
        val mid = baseline - (p.descent() - p.ascent()) * 0.32f
        p.isAntiAlias = true
        when (kind) {
            ListKind.BULLET -> {
                p.color = Look.ink; p.style = Paint.Style.FILL
                val r = 3.2f * d
                if (level % 2 == 1) { p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d }
                c.drawCircle(left + dir * 14f * d, mid, r, p)
            }
            ListKind.NUMBER -> {
                p.color = Look.ink; p.style = Paint.Style.FILL
                val n = numberAt(text, start, level)
                val label = if (level % 2 == 1) "${('a' + (n - 1) % 26)}." else "$n."
                val w = p.measureText(label)
                c.drawText(label, if (dir > 0) left + Look.marker - 10f * d - w else left - Look.marker + 10f * d, baseline.toFloat(), p)
            }
            ListKind.CHECK -> {
                val s = 19f * d
                val l = if (dir > 0) left + 4f * d else left - 4f * d - s
                val box = RectF(l, mid - s / 2, l + s, mid + s / 2)
                if (checked) {
                    p.color = Look.accent; p.style = Paint.Style.FILL
                    c.drawRoundRect(box, 5f * d, 5f * d, p)
                    p.color = Look.onAccent; p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f * d; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
                    c.drawPath(Path().apply { moveTo(box.left + s * 0.24f, box.centerY()); lineTo(box.left + s * 0.43f, box.top + s * 0.70f); lineTo(box.left + s * 0.78f, box.top + s * 0.30f) }, p)
                } else {
                    p.color = Look.ink; p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d
                    box.inset(1f * d, 1f * d)
                    c.drawRoundRect(box, 5f * d, 5f * d, p)
                }
            }
            ListKind.NONE -> {}
        }
        p.color = saved; p.style = style; p.strokeWidth = stroke
    }

    /** Where a checkbox is, from the left of its paragraph's first line (for taps). */
    fun hitWidth() = level * Look.indent + Look.marker
}

/** The start of the paragraph holding [pos]. */
internal fun paragraphStart(text: CharSequence, pos: Int): Int {
    var i = pos.coerceIn(0, text.length)
    while (i > 0 && text[i - 1] != '\n') i--
    return i
}

/** The end (its newline, or the end of the text) of the paragraph holding [pos]. */
internal fun paragraphEnd(text: CharSequence, pos: Int): Int {
    var i = pos.coerceIn(0, text.length)
    while (i < text.length && text[i] != '\n') i++
    return i
}

/** The [ParaSpan] of the paragraph starting at [start], if any. */
internal fun paraAt(text: Spanned, start: Int): ParaSpan? {
    return text.paragraphSpans(start, ParaSpan::class.java).firstOrNull { text.getSpanStart(it) <= start }
}

/**
 * The number a numbered item shows: one more than the numbered item before it at the same level. Deeper items in
 * between don't break the count; anything else (a plain paragraph, another kind of list, a shallower item) restarts it.
 */
internal fun numberAt(text: Spanned, start: Int, level: Int): Int {
    var n = 1
    var s = start
    while (s > 0) {
        s = paragraphStart(text, s - 1)
        val p = paraAt(text, s) ?: break
        if (p.level > level) continue
        if (p.level < level || p.kind != ListKind.NUMBER) break
        n++
    }
    return n
}

/** What [BlockSpan] can make of a paragraph; [id] is how it's saved. */
enum class BlockKind(val id: String) {
    QUOTE(com.jlees.minotes.notes.BlockKinds.QUOTE), CODE(com.jlees.minotes.notes.BlockKinds.CODE), CALLOUT(com.jlees.minotes.notes.BlockKinds.CALLOUT)
}

/**
 * A paragraph set apart: a quote (an accent bar at its side), a code block (monospace, on a faint tint) or a callout
 * (an accent-tinted box with the bar). One per paragraph, as with lists; neighbouring lines of one kind meet as one block.
 */
class BlockSpan(val kind: BlockKind) : MetricAffectingSpan(), LeadingMarginSpan {
    override fun updateMeasureState(tp: TextPaint) { if (kind == BlockKind.CODE) { tp.typeface = Typeface.MONOSPACE; tp.textSize *= 0.9f } }
    override fun updateDrawState(tp: TextPaint) = updateMeasureState(tp)
    override fun getLeadingMargin(first: Boolean) = (16 * ParaSpan.Look.density).toInt()

    // the tint and the bar are drawn with the margin, which Android draws on every line, an empty one too (a line
    // background isn't drawn for an empty line, so a new line in a block would show bare)
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        val saved = p.color; val style = p.style
        p.style = Paint.Style.FILL
        if (kind != BlockKind.QUOTE) {
            p.color = if (kind == BlockKind.CODE) (ParaSpan.Look.ink and 0xFFFFFF) or 0x14000000 else (ParaSpan.Look.accent and 0xFFFFFF) or 0x24000000
            val far = if (dir > 0) (layout?.width ?: c.width).toFloat() else 0f
            c.drawRect(minOf(x.toFloat(), far), top.toFloat(), maxOf(x.toFloat(), far), bottom.toFloat(), p)
        }
        if (kind != BlockKind.CODE) {
            p.color = ParaSpan.Look.accent
            val w = 3 * ParaSpan.Look.density
            c.drawRect(minOf(x.toFloat(), x + dir * w), top.toFloat(), maxOf(x.toFloat(), x + dir * w), bottom.toFloat(), p)
        }
        p.color = saved; p.style = style
    }
}

/** A dividing line across the page, on its one placeholder character. */
class DividerSpan(private val pageWidth: () -> Int) : android.text.style.ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val h = (paint.textSize * 1.2f).toInt()
        if (fm != null) { fm.ascent = -h; fm.top = -h; fm.descent = 0; fm.bottom = 0 }
        return pageWidth()
    }
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val d = ParaSpan.Look.density
        val mid = y - paint.textSize * 0.6f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (ParaSpan.Look.ink and 0x00FFFFFF) or 0x80000000.toInt(); strokeWidth = 1.5f * d }
        canvas.drawLine(x, mid, x + pageWidth(), mid, line)
    }
}
