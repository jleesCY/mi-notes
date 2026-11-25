package com.jlees.minotes

import com.jlees.minotes.notes.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test fun exportsInlineStylesAndLists() {
        val text = "Big\nbold and italic\ntodo\ndone\nitem"
        val p = paragraphs(text)
        val rt = RichText(text, listOf(
            Span(p[0].first, p[0].second, SpanType.SIZE, "2.0"),
            Span(p[1].first, p[1].first + 4, SpanType.WEIGHT, "700"),
            Span(p[1].first + 9, p[1].second, SpanType.ITALIC),
            Span(p[2].first, p[2].second, SpanType.PARA, "check"),
            Span(p[3].first, p[3].second, SpanType.PARA, "check", checked = true),
            Span(p[4].first, p[4].second, SpanType.PARA, "bullet", 1),
        ))
        assertEquals("# Big\n**bold** and *italic*\n- [ ] todo\n- [x] done\n  - item", toMarkdown(rt))
    }

    @Test fun escapesMarkdownCharacters() {
        assertEquals("2 \\* 3  \n\\- not a list", toMarkdown(RichText("2 * 3\n- not a list")))
    }

    @Test fun underlineColourAndHighlightUseHtml() {
        val rt = RichText("abc", listOf(Span(0, 1, SpanType.UNDERLINE), Span(1, 2, SpanType.HIGHLIGHT, "#FFD43B"), Span(2, 3, SpanType.COLOR, "#E5484D")))
        assertEquals("<u>a</u><mark>b</mark><span style=\"color:#E5484D\">c</span>", toMarkdown(rt))
    }

    @Test fun importsListsHeadingsAndStyles() {
        val rt = fromMarkdown("## Plan\n- [x] **done** thing\n  - sub\n1. first\nsome ~~old~~ `code` <u>u</u>")
        assertEquals("Plan\ndone thing\nsub\nfirst\nsome old code u", rt.text)
        fun has(type: SpanType, covered: String, value: String? = null) = assertTrue("$type over '$covered'", rt.spans.any { s ->
            s.type == type && rt.text.substring(s.start, s.end) == covered && (value == null || s.value == value)
        })
        has(SpanType.SIZE, "Plan", "1.5")
        has(SpanType.WEIGHT, "done", "700")
        has(SpanType.STRIKE, "old"); has(SpanType.FONT, "code", "mono"); has(SpanType.UNDERLINE, "u")
        val paras = rt.spans.filter { it.type == SpanType.PARA }
        assertEquals(listOf("check" to 0, "bullet" to 1, "number" to 0), paras.map { it.value to it.level })
        assertTrue(paras[0].checked)
    }

    @Test fun roundTripsANote() {
        val n = markdownToNote("# Trip\n\n- [ ] passport\n- [x] **tickets**\n\nNotes here", "file", "id", 1)
        assertEquals("Trip", n.title)
        val back = markdownToNote(noteToMarkdown(n), "file", "id", 1)
        assertEquals(n.title, back.title)
        assertEquals(n.body.text, back.body.text)
        assertEquals(n.body.spans.sortedBy { it.start }, back.body.spans.sortedBy { it.start })
    }

    @Test fun untitledFileUsesItsName() = assertEquals("groceries", markdownToNote("milk", "groceries", "id", 1).title)

    @Test fun escapedMarkersStayText() = assertEquals("a*b*c", fromMarkdown("a\\*b\\*c").text)

    @Test fun resizedPicturesKeepTheirWidth() {
        assertEquals("<img src=\"images/a.jpg\" width=\"50%\">", toMarkdown(RichText("\uFFFC", listOf(Span(0, 1, SpanType.IMAGE, "a.jpg", width = 50)))))
        assertEquals("![](images/a.jpg)", toMarkdown(RichText("\uFFFC", listOf(Span(0, 1, SpanType.IMAGE, "a.jpg")))))
    }

    @Test fun aQuoteGoesOutMarkedAndComesBack() {
        val rt = RichText("a\nq1\nq2\nb", listOf(Span(2, 4, SpanType.BLOCK, BlockKinds.QUOTE), Span(5, 7, SpanType.BLOCK, BlockKinds.QUOTE)))
        assertEquals("a\n> q1  \n> q2\n\nb", toMarkdown(rt)) // the blank line ends the quote, so "b" isn't read into it
        assertEquals(rt, fromMarkdown(toMarkdown(rt)))
    }

    @Test fun aCalloutGoesOutAsAGitHubNoteAndComesBack() {
        val rt = RichText("n1\nn2", listOf(Span(0, 2, SpanType.BLOCK, BlockKinds.CALLOUT), Span(3, 5, SpanType.BLOCK, BlockKinds.CALLOUT)))
        assertEquals("> [!NOTE]\n> n1  \n> n2", toMarkdown(rt))
        assertEquals(rt, fromMarkdown(toMarkdown(rt)))
        assertEquals(BlockKinds.CALLOUT, fromMarkdown("> [!WARNING]\n> careful").spans.single { it.type == SpanType.BLOCK }.value)
    }

    @Test fun codeGoesOutFencedAndAsTyped() {
        val rt = RichText("a\nval x = *1*\nb", listOf(Span(2, 13, SpanType.BLOCK, BlockKinds.CODE)))
        assertEquals("a\n```\nval x = *1*\n```\nb", toMarkdown(rt))
        assertEquals(rt, fromMarkdown(toMarkdown(rt)))
    }

    @Test fun textThatLooksLikeAQuoteOrFenceStaysText() {
        for (t in listOf("> not a quote", "```")) assertEquals(RichText(t), fromMarkdown(toMarkdown(RichText(t))))
    }

    @Test fun aDividerGoesOutAsARuleAndComesBack() {
        val rt = RichText("a\n\uFFFC\nb", listOf(Span(2, 3, SpanType.DIVIDER)))
        assertEquals("a  \n\n---\n\nb", toMarkdown(rt))
        assertEquals(rt, fromMarkdown(toMarkdown(rt)))
    }

    @Test fun aLineOfDashesTypedAsTextStaysText() {
        val rt = RichText("---")
        assertEquals(rt, fromMarkdown(toMarkdown(rt)))
    }
}
