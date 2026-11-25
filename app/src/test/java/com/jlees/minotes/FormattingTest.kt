package com.jlees.minotes

import android.text.SpannableStringBuilder
import com.jlees.minotes.editor.*
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormattingTest {
    private fun ed(text: String) = SpannableStringBuilder(text)
    private fun kinds(t: SpannableStringBuilder) = paragraphStarts(t, 0, t.length).map { paraAt(t, it)?.kind ?: ListKind.NONE }

    @Test fun inlineStyleSplitsAroundAChange() {
        val t = ed("hello world")
        applyInline(t, 0, 11, Inline.WEIGHT, 700)
        applyInline(t, 3, 8, Inline.WEIGHT, 400)
        assertEquals(700, inlineValue(t, 0, 3, Inline.WEIGHT))
        assertEquals(400, inlineValue(t, 3, 8, Inline.WEIGHT))
        assertEquals(700, inlineValue(t, 8, 11, Inline.WEIGHT))
        assertNull(inlineValue(t, 0, 11, Inline.WEIGHT)) // mixed
    }

    @Test fun cursorReadsTheStyleBeforeIt() {
        val t = ed("ab")
        applyInline(t, 0, 1, Inline.ITALIC, true)
        assertEquals(true, inlineValue(t, 1, 1, Inline.ITALIC))
        assertEquals(false, inlineValue(t, 2, 2, Inline.ITALIC))
    }

    @Test fun typingAtTheEndCarriesTheStyleOn() {
        val t = ed("ab")
        applyInline(t, 0, 2, Inline.COLOR, 0xFFFF0000.toInt())
        t.insert(2, "c")
        assertEquals(0xFFFF0000.toInt(), inlineValue(t, 0, 3, Inline.COLOR))
    }

    @Test fun listTogglesOnAndOffAcrossParagraphs() {
        val t = ed("one\ntwo\nthree")
        toggleList(t, 2, 6, ListKind.BULLET)
        assertEquals(listOf(ListKind.BULLET, ListKind.BULLET, ListKind.NONE), kinds(t))
        toggleList(t, 0, 6, ListKind.BULLET)
        assertEquals(listOf(ListKind.NONE, ListKind.NONE, ListKind.NONE), kinds(t))
    }

    @Test fun enterCarriesTheListIntoTheNewLineUnticked() {
        val t = ed("milk\n") // as in the editor: its own last line break
        setPara(t, 0, ListKind.CHECK, 0, checked = true)
        t.insert(4, "\n")
        normalizeParagraphs(t)
        assertEquals(listOf(ListKind.CHECK, ListKind.CHECK, ListKind.NONE), kinds(t)) // the editor's own last line stays plain
        assertTrue(paraAt(t, 0)!!.checked)
        assertFalse(paraAt(t, 5)!!.checked)
    }

    @Test fun enterInAnEmptyItemEndsTheList() {
        val t = ed("milk\n")
        setPara(t, 0, ListKind.BULLET, 0); setPara(t, 5, ListKind.BULLET, 0)
        t.insert(5, "\n") // Enter on the empty item
        assertTrue(endListOnEmptyEnter(t, 5))
        assertEquals("milk\n", t.toString())
        assertEquals(listOf(ListKind.BULLET, ListKind.NONE), kinds(t))
    }

    @Test fun enterAtTheStartOfAnItemPushesItDown() {
        val t = ed("milk")
        setPara(t, 0, ListKind.BULLET, 0)
        t.insert(0, "\n")
        assertFalse(endListOnEmptyEnter(t, 0))
        normalizeParagraphs(t)
        assertEquals("\nmilk", t.toString())
        assertEquals(ListKind.BULLET, paraAt(t, 1)?.kind)
    }

    @Test fun joiningParagraphsKeepsTheFirstOnesFormat() {
        val t = ed("a\nb")
        setPara(t, 0, ListKind.NUMBER, 0); setPara(t, 2, ListKind.CHECK, 0)
        t.delete(1, 2)
        normalizeParagraphs(t)
        assertEquals(listOf(ListKind.NUMBER), kinds(t))
    }

    @Test fun backspaceTakesTheMarkerThenTheIndentOff() {
        val t = ed("x")
        setPara(t, 0, ListKind.BULLET, 1)
        assertTrue(backspaceAtParagraphStart(t, 0)); assertEquals(ListKind.NONE, paraAt(t, 0)?.kind); assertEquals(1, paraAt(t, 0)?.level)
        assertTrue(backspaceAtParagraphStart(t, 0)); assertNull(paraAt(t, 0))
        assertFalse(backspaceAtParagraphStart(t, 0))
    }

    @Test fun tickedItemsMoveToTheBottom() {
        val t = ed("a\nb\nc\nafter")
        listOf(0, 2, 4).forEach { setPara(t, it, ListKind.CHECK, 0) }
        val now = toggleCheck(t, 0, toBottom = true)
        assertEquals("b\nc\na\nafter", t.toString())
        assertEquals(4, now)
        assertTrue(paraAt(t, 4)!!.checked)
        assertFalse(paraAt(t, 0)!!.checked)
        assertNull(paraAt(t, 6))
    }

    @Test fun numbersCountWithinALevel() {
        val t = ed("a\nb\nsub\nc\nplain\nd")
        listOf(0 to 0, 2 to 0, 4 to 1, 8 to 0, 16 to 0).forEach { (s, l) -> setPara(t, s, ListKind.NUMBER, l) }
        assertEquals(3, numberAt(t, 8, 0))
        assertEquals(1, numberAt(t, 4, 1))
        assertEquals(1, numberAt(t, 16, 0)) // after a plain paragraph, counting restarts
    }

    @Test fun codecRoundTripsEverything() {
        val rt = RichText("Title\nitem\nend", listOf(
            Span(0, 5, SpanType.SIZE, "1.75"), Span(0, 5, SpanType.WEIGHT, "800"),
            Span(6, 10, SpanType.PARA, "check", 1, true), Span(11, 14, SpanType.ALIGN, "center"),
            Span(11, 14, SpanType.HIGHLIGHT, "#FFD43B"), Span(11, 12, SpanType.FONT, "serif"),
        ))
        val t = ed("")
        RichCodec.load(t, rt, { _, _ -> null })
        assertEquals(rt.copy(spans = rt.spans.sortedWith(compareBy({ it.start }, { it.end }, { it.type }, { it.value }))), RichCodec.toRich(t))
    }

    @Test fun styleAppliesToWholeParagraphs() {
        val t = ed("one two\nthree")
        applyStyle(t, 2, 3, TextStyle.HEADING)
        assertEquals(TextStyle.HEADING, TextStyle.of(inlineValue(t, 0, 7, Inline.SIZE), inlineValue(t, 0, 7, Inline.WEIGHT)))
        assertEquals(1f, inlineValue(t, 8, 13, Inline.SIZE))
    }

    @Test fun editorTextEndsWithALineBreakTheNoteDoesNotHave() {
        val rt = RichText("milk\n", listOf(Span(0, 4, SpanType.PARA, "bullet"), Span(5, 5, SpanType.PARA, "bullet")))
        val t = ed("")
        RichCodec.load(t, rt, { _, _ -> null })
        assertEquals("milk\n\n", t.toString())
        // the empty item at the end isn't the last line, so Android draws its bullet
        assertEquals(ListKind.BULLET, paraAt(t, 5)?.kind)
        assertNull(paraAt(t, 6))
        assertEquals(rt, RichCodec.toRich(t))
    }

    @Test fun trailingLineComesBack() {
        val t = ed("abc")
        assertTrue(RichCodec.keepTrailingLine(t)); assertEquals("abc\n", t.toString())
        assertFalse(RichCodec.keepTrailingLine(t))
    }

    @Test fun imageWidthRoundTrips() {
        val rt = RichText("\uFFFC", listOf(Span(0, 1, SpanType.IMAGE, "a.jpg", width = 50)))
        val t = ed("")
        RichCodec.load(t, rt, { _, _ -> android.graphics.drawable.ColorDrawable(0) })
        assertEquals(rt, RichCodec.toRich(t))
    }
}
