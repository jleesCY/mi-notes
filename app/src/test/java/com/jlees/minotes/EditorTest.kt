package com.jlees.minotes

import androidx.test.core.app.ApplicationProvider
import com.jlees.minotes.editor.NoteEditText
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.Span
import com.jlees.minotes.notes.SpanType
import com.jlees.minotes.editor.Inline
import com.jlees.minotes.editor.RichCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** The editor view itself, typed into as the keyboard would. */
@RunWith(RobolectricTestRunner::class)
class EditorTest {
    private var last: RichText? = null
    private fun editor(rt: RichText) = NoteEditText(ApplicationProvider.getApplicationContext()).apply { load(rt); onChange = { last = it } }
    private fun NoteEditText.type(s: String) { text.replace(selectionStart, selectionEnd, s) }
    /** What the editor last saved (typing is saved once it pauses). */
    private fun saved(): RichText { ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS); return last ?: error("not saved") }

    // ---- the title is the first line of one text
    private fun titled(title: String, rt: RichText) = NoteEditText(ApplicationProvider.getApplicationContext()).apply { load(rt, title); onChange = { last = it } }

    @Test fun theTitleIsTheFirstLine() {
        val e = titled("Shop", RichText("milk", listOf(Span(0, 4, SpanType.PARA, "check"))))
        assertEquals("Shop", e.title())
        assertEquals(RichText("milk", listOf(Span(0, 4, SpanType.PARA, "check"))), e.body())
    }

    @Test fun pastingIntoTheTitleKeepsOnlyTheFirstLineThere() {
        val e = titled("", RichText("old text"))
        e.setSelection(0)
        e.type("Groceries\neggs\nbread")
        assertEquals("Groceries", e.title())
        assertEquals("eggs\nbread\nold text", saved().text) // the rest goes into the body, above what was there
    }

    @Test fun writingBelowABlankTitleLeavesItBlank() {
        val e = titled("", RichText(""))
        e.setSelection(e.titleEnd() + 1)
        e.type("just text")
        assertEquals("", e.title())
        assertEquals("just text", saved().text)
    }

    @Test fun theTitleLineStaysPlain() {
        // a list item joined onto the title (backspace at the start of the body) leaves its list behind
        val e = titled("Shop", RichText("milk", listOf(Span(0, 4, SpanType.PARA, "bullet"))))
        e.text.delete(4, 5) // the title's line break
        assertEquals("Shopmilk", e.title())
        e.setSelection(0, 4)
        e.setInline(Inline.WEIGHT, 700)
        assertEquals(RichText(""), e.body())
        assertEquals(0, e.text.getSpans(0, 4, com.jlees.minotes.editor.WeightSpan::class.java).size + e.text.getSpans(0, 4, com.jlees.minotes.editor.ParaSpan::class.java).size)
    }

    @Test fun enterTwiceAtTheEndOfAListEndsIt() {
        val e = editor(RichText("One", listOf(Span(0, 3, SpanType.PARA, "bullet"))))
        e.setSelection(3)
        e.type("\n")
        e.type("\n")
        e.type("after")
        val rt = saved()
        assertEquals("One\nafter", rt.text)
        assertEquals(listOf("bullet"), rt.spans.filter { it.type == SpanType.PARA }.map { it.value })
    }

    @Test fun anEmptyNewItemIsSavedAsAListItem() {
        val e = editor(RichText("One", listOf(Span(0, 3, SpanType.PARA, "check"))))
        e.setSelection(3)
        e.type("\n")
        val rt = saved()
        assertEquals("One\n", rt.text)
        assertEquals(listOf(0 to 3, 4 to 4), rt.spans.filter { it.type == SpanType.PARA }.map { it.start to it.end })
    }

    @Test fun theCursorStaysOffTheEditorsOwnLastLine() {
        val e = editor(RichText("abc"))
        e.setSelection(e.length())
        ShadowLooper.idleMainLooper()
        assertEquals(3, e.selectionStart)
    }

    @Test fun formatAppliesToTheHeldSelectionAfterItCollapses() {
        val e = editor(RichText("hello world"))
        e.requestFocus()
        e.setSelection(0, 5)
        e.holdSelection = true
        e.setSelection(5) // the Cut/Copy toolbar closing collapses it
        ShadowLooper.idleMainLooper()
        assertEquals(0 to 5, e.selectionStart to e.selectionEnd)
        e.setInline(Inline.WEIGHT, 700)
        val rt = saved()
        assertEquals(listOf(Triple(0, 5, "700")), rt.spans.map { Triple(it.start, it.end, it.value) })
    }

    @Test fun anExactSizeIsSavedReloadedAndExported() {
        val e = editor(RichText("Hello"))
        e.requestFocus()
        e.setSelection(0, 5)
        ShadowLooper.idleMainLooper()
        e.setInline(Inline.SIZE, 23 / 17f) // 23 pt, typed in the size box
        val rt = saved()
        val size = rt.spans.single { it.type == SpanType.SIZE }
        assertEquals(23, Math.round(size.value.toFloat() * 17))
        // through the backup's JSON and back into an editor, it's the same size
        val back = com.jlees.minotes.data.json.decodeFromString(RichText.serializer(), com.jlees.minotes.data.json.encodeToString(RichText.serializer(), rt))
        val again = RichCodec.toRich(editor(back).text)
        assertEquals(size.value, again.spans.single { it.type == SpanType.SIZE }.value)
        // and Markdown gets a heading for it (1.35×: a level-3 heading)
        assertEquals("### Hello", com.jlees.minotes.notes.toMarkdown(rt).trim())
    }

    @Test fun dividersAndBlocksSurviveReopening() {
        val rt = RichText("a\n\uFFFC\nq\nc", listOf(
            Span(2, 3, SpanType.DIVIDER),
            Span(4, 5, SpanType.BLOCK, com.jlees.minotes.notes.BlockKinds.QUOTE),
            Span(6, 7, SpanType.BLOCK, com.jlees.minotes.notes.BlockKinds.CODE),
        ))
        assertEquals(rt, RichCodec.toRich(editor(rt).text))
    }

    @Test fun theBlockButtonTurnsALineIntoAQuoteAndBack() {
        val e = editor(RichText("one\ntwo"))
        e.requestFocus(); e.setSelection(5); ShadowLooper.idleMainLooper()
        e.block(com.jlees.minotes.editor.BlockKind.QUOTE)
        assertEquals(listOf(Span(4, 7, SpanType.BLOCK, "quote")), saved().spans)
        e.block(com.jlees.minotes.editor.BlockKind.QUOTE)
        assertEquals(emptyList<Span>(), saved().spans)
    }

    @Test fun enterCarriesABlockOnAndTwiceEndsIt() {
        val e = editor(RichText("q", listOf(Span(0, 1, SpanType.BLOCK, "callout"))))
        e.requestFocus(); e.setSelection(1); ShadowLooper.idleMainLooper()
        e.type("\n"); e.type("r")
        e.type("\n"); e.type("\n"); e.type("after")
        val rt = saved()
        assertEquals("q\nr\nafter", rt.text)
        assertEquals(listOf(0 to 1, 2 to 3), rt.spans.filter { it.type == SpanType.BLOCK }.map { it.start to it.end })
    }

    @Test fun enterOnAnEmptyNewBlockEndsIt() {
        // a callout started on an empty line (a new note), then Enter: no block lines pile up
        for (kind in com.jlees.minotes.editor.BlockKind.entries) {
            val e = editor(RichText(""))
            e.requestFocus(); e.setSelection(0); ShadowLooper.idleMainLooper()
            e.block(kind)
            e.type("\n")
            val rt = saved()
            assertEquals(kind.name, "", rt.text)
            assertEquals(kind.name, emptyList<Span>(), rt.spans)
        }
    }

    @Test fun enterOnAnEmptyNewListEndsIt() {
        for (kind in listOf(ListKind.BULLET, ListKind.NUMBER, ListKind.CHECK)) {
            val e = editor(RichText(""))
            e.requestFocus(); e.setSelection(0); ShadowLooper.idleMainLooper()
            e.list(kind)
            e.type("\n")
            val rt = saved()
            assertEquals(kind.name, "", rt.text)
            assertEquals(kind.name, emptyList<String>(), rt.spans.filter { it.type == SpanType.PARA && it.value != "none" }.map { it.value })
        }
    }

    @Test fun enterEndsAListOrBlockMovedOntoTheNewLine() {
        // on a device, Enter on an empty styled line moves its (empty) list or block span onto the new line: it ends all the same
        for (span in listOf(Span(0, 0, SpanType.BLOCK, "callout"), Span(0, 0, SpanType.PARA, "bullet"))) {
            val t = android.text.SpannableStringBuilder("\n\n")
            val sp: Any = if (span.type == SpanType.BLOCK) com.jlees.minotes.editor.BlockSpan(com.jlees.minotes.editor.BlockKind.CALLOUT)
                else com.jlees.minotes.editor.ParaSpan(ListKind.BULLET, 0)
            t.setSpan(sp, 1, 1, com.jlees.minotes.editor.PARA_FLAGS) // where the device leaves it
            assertEquals(span.value, true, com.jlees.minotes.editor.endListOnEmptyEnter(t, 0))
            assertEquals(span.value, "\n", t.toString())
            assertEquals(span.value, null, com.jlees.minotes.editor.blockAt(t, 0))
            assertEquals(span.value, null, com.jlees.minotes.editor.paraAt(t, 0)?.kind?.takeIf { it != ListKind.NONE })
        }
    }

    @Test fun aListStartedWithTheCursorOnTheHiddenLastLineLandsOnTheVisibleOne() {
        // a tap into an empty note can leave the cursor a moment on the editor's own last line: the list button then
        // put the list there, drawn beside the text but out of reach of backspace and Enter
        val e = editor(RichText(""))
        e.requestFocus()
        val f = NoteEditText::class.java.getDeclaredField("selS").apply { isAccessible = true }
        val g = NoteEditText::class.java.getDeclaredField("selE").apply { isAccessible = true }
        f.setInt(e, 1); g.setInt(e, 1)
        e.list(ListKind.BULLET)
        val sp = e.text.getSpans(0, e.length(), com.jlees.minotes.editor.ParaSpan::class.java).single()
        assertEquals(0, e.text.getSpanStart(sp))
        e.type("a"); e.setSelection(0)
        assertEquals(true, com.jlees.minotes.editor.backspaceAtParagraphStart(e.text, 0)) // and backspace takes it off
        assertEquals(emptyList<Span>(), saved().spans.filter { it.type == SpanType.PARA && it.value != "none" })
    }

    private fun NoteEditText.backspace() { onKeyDown(android.view.KeyEvent.KEYCODE_DEL, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL)) }
    private fun styled(kind: String) = if (kind in listOf("quote", "code", "callout")) SpanType.BLOCK else SpanType.PARA

    @Test fun selectingAllAndDeletingLeavesNoMarkerBehind() {
        for (kind in listOf("bullet", "number", "check", "quote", "code", "callout")) {
            val type = styled(kind)
            val e = editor(RichText("a\nb\nc", listOf(Span(0, 1, type, kind), Span(2, 3, type, kind), Span(4, 5, type, kind))))
            e.requestFocus(); e.setSelection(0, 5); ShadowLooper.idleMainLooper()
            e.text.delete(0, 5)
            val rt = saved()
            assertEquals(kind, "", rt.text)
            assertEquals(kind, emptyList<Span>(), rt.spans.filter { it.value != "none" })
            e.undo() // and it all comes back
            assertEquals(kind, "a\nb\nc", RichCodec.toRich(e.text).text)
            assertEquals(kind, 3, RichCodec.toRich(e.text).spans.count { it.type == type })
        }
    }

    @Test fun theKeyboardDeletingASelectionLeavesNoMarkerBehind() {
        // as the on-screen keyboard does it: through its connection, not the text directly
        for (kind in listOf("bullet", "quote")) {
            val type = styled(kind)
            val e = editor(RichText("I\nA", listOf(Span(0, 1, type, kind), Span(2, 3, type, kind))))
            e.requestFocus(); ShadowLooper.idleMainLooper()
            val ic = e.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
            e.setSelection(0, e.length()) // Select all takes in the editor's own last line break too
            ic.commitText("", 1)
            val rt = saved()
            assertEquals(kind, "", rt.text)
            assertEquals(kind, emptyList<Span>(), rt.spans.filter { it.value != "none" })
            e.setSelection(0, 0)
            ic.commitText("x", 1) // and what's typed next is plain
            assertEquals(kind, emptyList<Span>(), saved().spans.filter { it.value != "none" })
        }
    }

    @Test fun deletingMiddleItemsKeepsTheRestOfTheList() {
        val e = editor(RichText("a\nb\nc", listOf(Span(0, 1, SpanType.PARA, "bullet"), Span(2, 3, SpanType.PARA, "bullet"), Span(4, 5, SpanType.PARA, "bullet"))))
        e.requestFocus(); e.setSelection(1, 3); ShadowLooper.idleMainLooper()
        e.text.delete(1, 3) // "\nb": b goes, a and c stay items
        val rt = saved()
        assertEquals("a\nc", rt.text)
        assertEquals(listOf(0 to 1, 2 to 3), rt.spans.filter { it.type == SpanType.PARA }.map { it.start to it.end })
    }

    @Test fun deletingWholeLinesLeavesTheirEmptyLinePlain() {
        val e = editor(RichText("a\nb\nc\nd", listOf(Span(2, 3, SpanType.PARA, "bullet"), Span(4, 5, SpanType.PARA, "bullet"))))
        e.requestFocus(); e.setSelection(2, 5); ShadowLooper.idleMainLooper()
        e.text.delete(2, 5) // "b\nc": the line they were on stays, empty and plain
        val rt = saved()
        assertEquals("a\n\nd", rt.text)
        assertEquals(emptyList<Span>(), rt.spans)
    }

    @Test fun backspacingAListAwayLeavesNothing() {
        val e = editor(RichText("1\n2", listOf(Span(0, 1, SpanType.PARA, "number"), Span(2, 3, SpanType.PARA, "number"))))
        e.requestFocus(); e.setSelection(3); ShadowLooper.idleMainLooper()
        repeat(5) { e.backspace() } // "2", its marker, the line break, "1", its marker
        val rt = saved()
        assertEquals("", rt.text)
        assertEquals(emptyList<Span>(), rt.spans)
    }

    @Test fun typingAtTheStartOfThePlainLineBelowAListKeepsItPlain() {
        for (kind in listOf("bullet", "quote")) {
            val e = editor(RichText("a\nb", listOf(Span(0, 1, styled(kind), kind))))
            e.requestFocus(); e.setSelection(2); ShadowLooper.idleMainLooper()
            e.type("x")
            val rt = saved()
            assertEquals(kind, "a\nxb", rt.text)
            assertEquals(kind, listOf(0 to 1), rt.spans.map { it.start to it.end })
        }
    }

    @Test fun enterAtTheEndOfTheLastItemMakesAnotherItem() {
        val e = editor(RichText("a", listOf(Span(0, 1, SpanType.PARA, "bullet"))))
        e.requestFocus(); e.setSelection(1); ShadowLooper.idleMainLooper()
        e.type("\n"); e.type("b")
        val rt = saved()
        assertEquals("a\nb", rt.text)
        assertEquals(listOf(0 to 1, 2 to 3), rt.spans.filter { it.type == SpanType.PARA }.map { it.start to it.end })
    }

    @Test fun enterTwiceEndsEveryKindOfList() {
        for (kind in listOf("bullet", "number", "check")) {
            val e = editor(RichText("One", listOf(Span(0, 3, SpanType.PARA, kind))))
            e.requestFocus(); e.setSelection(3); ShadowLooper.idleMainLooper()
            e.type("\n"); e.type("\n"); e.type("x")
            val rt = saved()
            assertEquals(kind, "One\nx", rt.text)
            assertEquals(kind, listOf(kind), rt.spans.filter { it.type == SpanType.PARA }.map { it.value })
        }
    }

    @Test fun backspaceAtABlockLinesStartTakesTheBlockOff() {
        val e = editor(RichText("a\nq", listOf(Span(2, 3, SpanType.BLOCK, "code"))))
        assertEquals(true, com.jlees.minotes.editor.backspaceAtParagraphStart(e.text, 2))
        assertEquals(null, com.jlees.minotes.editor.blockAt(e.text, 2))
        assertEquals("a\nq", RichCodec.toRich(e.text).text) // the line stays where it is
    }

    @Test fun writingAfterADividerAtALinesEndGoesOnANewLine() {
        val e = editor(RichText("a\nb"))
        e.requestFocus(); e.setSelection(1); ShadowLooper.idleMainLooper()
        e.insertDivider()
        e.type("x")
        assertEquals("a\n￼\nx\nb", saved().text)
    }

    @Test fun scannedTextGoesOnLinesOfItsOwnAsOneUndoStep() {
        val e = editor(RichText("Receipt total"))
        e.requestFocus(); e.setSelection(7); ShadowLooper.idleMainLooper() // mid-line, after "Receipt"
        e.insertLines(RichText("Milk 2.40\nEggs 3.10"))
        assertEquals("Receipt\nMilk 2.40\nEggs 3.10\n total", saved().text) // not run into the words beside it
        assertEquals(8 + 19, e.selectionStart) // writing carries on at its end
        e.undo()
        assertEquals("Receipt total", RichCodec.toRich(e.text).text)
    }

    @Test fun aScannedChecklistKeepsItsItemsAndTheListAroundItsOwn() {
        val e = editor(RichText("Shop\nOne", listOf(Span(5, 8, SpanType.PARA, "bullet"))))
        e.requestFocus(); e.setSelection(8); ShadowLooper.idleMainLooper() // at the end of a bulleted item
        e.insertLines(RichText("Groceries\nMilk\nEggs", listOf(Span(10, 14, SpanType.PARA, "check"), Span(15, 19, SpanType.PARA, "check", checked = true))))
        val rt = saved()
        assertEquals("Shop\nOne\nGroceries\nMilk\nEggs", rt.text)
        assertEquals(listOf(Triple(5, "bullet", false), Triple(19, "check", false), Triple(24, "check", true)),
            rt.spans.filter { it.type == SpanType.PARA }.map { Triple(it.start, it.value, it.checked) }) // its heading isn't a bullet
    }

    @Test fun scannedTextOnAnEmptyLineAddsNoBlankLines() {
        val e = editor(RichText("a\n\nb"))
        e.requestFocus(); e.setSelection(2); ShadowLooper.idleMainLooper()
        e.insertLines(RichText("x"))
        assertEquals("a\nx\nb", saved().text)
    }

    @Test fun scannedTextAtTheEndAddsNoTrailingLine() {
        val e = editor(RichText("old"))
        e.requestFocus(); e.setSelection(0, 3); ShadowLooper.idleMainLooper()
        e.insertLines(RichText("new"))
        assertEquals("new", saved().text)
        assertEquals(3, e.selectionStart)
    }

    @Test fun theStartWritingLineGoesWhenABlockOrListStartsOnABlankNote() {
        for (kind in com.jlees.minotes.editor.BlockKind.entries) {
            val e = editor(RichText())
            e.requestFocus(); ShadowLooper.idleMainLooper()
            assertEquals(true, com.jlees.minotes.editor.showsPlaceholder(e.text))
            e.block(kind)
            assertEquals("$kind", false, com.jlees.minotes.editor.showsPlaceholder(e.text))
        }
        val e = editor(RichText())
        e.requestFocus(); ShadowLooper.idleMainLooper()
        e.list(ListKind.CHECK)
        assertEquals(false, com.jlees.minotes.editor.showsPlaceholder(e.text))
    }

    @Test fun flushHandsOverTypingThatHasntPausedYet() {
        val e = editor(RichText("Note"))
        e.requestFocus(); e.setSelection(4); ShadowLooper.idleMainLooper()
        last = null
        e.type(" more")
        assertEquals(null, last) // typing waits for a pause before it's saved
        e.flush()                // the app is leaving
        assertEquals("Note more", last!!.text)
    }

    @Test fun aScreenReaderTicksTheChecklistItemAtTheCursor() {
        val e = editor(RichText("Milk\nEggs", listOf(Span(0, 4, SpanType.PARA, "check"), Span(5, 9, SpanType.PARA, "check"))))
        e.requestFocus(); e.setSelection(7); ShadowLooper.idleMainLooper()
        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        e.onInitializeAccessibilityNodeInfo(info)
        assertEquals("Tick item", info.actionList.single { it.id == com.jlees.minotes.R.id.tick_item }.label)
        assertEquals(true, e.performAccessibilityAction(com.jlees.minotes.R.id.tick_item, null))
        assertEquals(listOf(false, true), saved().spans.filter { it.type == SpanType.PARA }.map { it.checked })
        // not in a checklist: no such action
        val plain = editor(RichText("Text")); plain.requestFocus(); plain.setSelection(2); ShadowLooper.idleMainLooper()
        val none = android.view.accessibility.AccessibilityNodeInfo.obtain(); plain.onInitializeAccessibilityNodeInfo(none)
        assertEquals(false, none.actionList.any { it.id == com.jlees.minotes.R.id.tick_item })
    }

    @Test fun aHugeNoteKeepsFewerUndoStepsInsteadOfFillingMemory() {
        val e = editor(RichText("x".repeat(600_000))) // each snapshot is the whole note: about 3 fit under the cap
        e.requestFocus(); e.setSelection(10); ShadowLooper.idleMainLooper()
        repeat(5) { e.type("y"); e.flush() }
        var steps = 0
        while (e.canUndo) { e.undo(); steps++ }
        assertEquals(true, steps in 1..3)
    }

    @Test fun undoPutsTheCursorWhereTheChangeWas() {
        val e = editor(RichText("one two"))
        e.requestFocus(); e.setSelection(3); ShadowLooper.idleMainLooper()
        e.type("X"); saved()
        e.setSelection(8) // typed somewhere, then moved away
        e.undo()
        assertEquals("one two", RichCodec.toRich(e.text).text)
        assertEquals(3, e.selectionStart)
    }

    @Test fun typingLeavesTheOtherParagraphsSpansAlone() {
        // each span change is a layout pass: a key typed in one line mustn't redo every line's list marker or tick
        val e = editor(RichText("a\nb\nc", listOf(Span(0, 1, SpanType.PARA, "check", checked = true), Span(2, 3, SpanType.PARA, "bullet"), Span(4, 5, SpanType.PARA, "bullet"))))
        e.requestFocus(); e.setSelection(5); ShadowLooper.idleMainLooper()
        val others = e.text.getSpans(0, 4, Any::class.java)
            .filter { (it is com.jlees.minotes.editor.ParaSpan || it is com.jlees.minotes.editor.DoneSpan) && e.text.getSpanStart(it) < 4 }
        assertEquals(3, others.size) // a's tick and crossing-out, b's bullet
        var touched = 0
        e.text.setSpan(object : android.text.SpanWatcher {
            override fun onSpanAdded(t: android.text.Spannable, w: Any, s: Int, en: Int) {}
            override fun onSpanRemoved(t: android.text.Spannable, w: Any, s: Int, en: Int) { if (others.any { it === w }) touched++ }
            override fun onSpanChanged(t: android.text.Spannable, w: Any, os: Int, oe: Int, ns: Int, ne: Int) { if (others.any { it === w }) touched++ }
        }, 0, e.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        e.type("d")
        assertEquals(0, touched)
        assertEquals("a\nb\ncd", saved().text)
    }

    @Test fun typingBesideADividerKeepsItAloneOnItsLine() {
        val rt = RichText("a\n￼", listOf(Span(2, 3, SpanType.DIVIDER)))
        val before = editor(rt)
        before.requestFocus(); before.setSelection(2); ShadowLooper.idleMainLooper() // tapped on the divider: just before it
        before.type("XY")
        assertEquals("a\nXY\n￼", saved().text)
        assertEquals(4, before.selectionStart) // carrying on after what was typed

        val after = editor(rt)
        after.requestFocus(); after.setSelection(3); ShadowLooper.idleMainLooper() // just after it
        after.type("Z")
        assertEquals("a\n￼\nZ", saved().text)
        assertEquals(5, after.selectionStart)
    }

    @Test fun theKeyboardNeitherCapitalisesNorCorrectsInACodeLine() {
        val e = editor(RichText("Plain\ncode", listOf(Span(6, 10, SpanType.BLOCK, "code"))))
        e.requestFocus()
        fun flags() = e.inputType
        e.setSelection(7); ShadowLooper.idleMainLooper()
        assertEquals(0, flags() and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertEquals(android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, flags() and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        e.setSelection(2); ShadowLooper.idleMainLooper() // back in plain text: as before
        assertEquals(android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, flags() and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertEquals(0, flags() and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    }

    @Test fun aScrollThatEndsOnTheTextDoesntPlaceTheCursor() {
        val e = editor(RichText("one\ntwo\nthree"))
        val t0 = android.os.SystemClock.uptimeMillis()
        fun ev(a: Int, y: Float, dt: Long) = android.view.MotionEvent.obtain(t0, t0 + dt, a, 20f, y, 0)
        e.onTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 10f, 0))
        e.onTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, 200f, 50))
        e.onTouchEvent(ev(android.view.MotionEvent.ACTION_UP, 200f, 80))
        assertEquals(false, e.isFocused) // a scroll: no focus, so no keyboard
    }
}
