package com.jlees.minotes

import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.SpanType
import com.jlees.minotes.scan.Mark
import com.jlees.minotes.scan.ScanLine
import com.jlees.minotes.scan.layoutScan
import org.junit.Assert.assertEquals
import org.junit.Test

/** A scan's lines read back into lists (scan/ScanLayout.kt). */
class ScanLayoutTest {
    /** Lines down a page, 40 px tall (letters 22 px wide), [left] in from the edge, with [marks]; a heading's are larger. */
    private fun page(vararg lines: String, left: (Int) -> Int = { 100 }, tall: Set<Int> = emptySet(), marks: Map<Int, Mark> = emptyMap()) =
        lines.mapIndexed { i, s ->
            val h = if (i in tall) 70 else 40; val cw = if (i in tall) 38 else 22
            ScanLine(s, left(i), 200 + i * 80, left(i) + cw * s.length, 200 + i * 80 + h, mark = marks[i])
        }

    /** Each line's list kind ("" for plain), level and tick, as "kind/level/✓". */
    private fun RichText.items(): List<String> {
        var p = 0
        return text.split('\n').map { line ->
            val s = spans.firstOrNull { it.type == SpanType.PARA && it.start == p }
            p += line.length + 1
            if (s == null) "" else "${s.value}/${s.level}" + if (s.checked) "/✓" else ""
        }
    }

    @Test fun aListWrittenWithoutMarksBecomesBulletsUnderItsHeading() {
        // nothing on the page says checklist, so it isn't one
        val rt = layoutScan(page("Groceries", "Milk", "Eggs", "Sourdough bread", "2 lemons", tall = setOf(0)))
        assertEquals("Groceries\nMilk\nEggs\nSourdough bread\n2 lemons", rt.text)
        assertEquals(listOf("", "bullet/0", "bullet/0", "bullet/0", "bullet/0"), rt.items())
    }

    @Test fun boxesSeenInThePictureMakeAChecklist() {
        val rt = layoutScan(page("Shopping", "Bananas", "Coffee beans", "Greek yogurt", tall = setOf(0), marks = mapOf(1 to Mark.BOX, 2 to Mark.TICKED_BOX, 3 to Mark.BOX)))
        assertEquals(listOf("", "check/0", "check/0/✓", "check/0"), rt.items())
    }

    @Test fun dotsAndDashesSeenInThePictureMakeBullets() {
        val rt = layoutScan(page("Bananas", "Coffee beans", "Greek yogurt", marks = mapOf(0 to Mark.BULLET, 1 to Mark.BULLET, 2 to Mark.BULLET)))
        assertEquals(listOf("bullet/0", "bullet/0", "bullet/0"), rt.items())
    }

    @Test fun linesWhoseMarkWasMissedTakeTheirListsKind() {
        // a box seen on two lines, missed on the other two: all four are checklist items, not bullets
        val rt = layoutScan(page("Bananas", "Coffee beans", "Greek yogurt", "Spinach", marks = mapOf(1 to Mark.TICKED_BOX, 3 to Mark.BOX)))
        assertEquals(listOf("check/0", "check/0/✓", "check/0", "check/0"), rt.items())
        // and a dot seen on one line makes the others bullets
        assertEquals(listOf("bullet/0", "bullet/0", "bullet/0"), layoutScan(page("Milk", "Eggs", "Jam", marks = mapOf(0 to Mark.BULLET))).items())
    }

    @Test fun numbersWinOverAMarkBesideThem() {
        val rt = layoutScan(page("1. Milk", "2. Eggs", marks = mapOf(0 to Mark.BULLET)))
        assertEquals(listOf("number/0", "number/0"), rt.items())
    }

    @Test fun headingsPartWayDownSplitTheLists() {
        val rt = layoutScan(page("Produce", "Apples", "Pears", "Dairy", "Milk", "Butter", tall = setOf(0, 3), marks = mapOf(1 to Mark.BOX, 4 to Mark.BULLET)))
        assertEquals(listOf("", "check/0", "check/0", "", "bullet/0", "bullet/0"), rt.items())
    }

    @Test fun bulletsAndDashesBecomeABulletedList() {
        val rt = layoutScan(page("Packing:", "• Shoes", "- Adapter", "* Charger"))
        assertEquals("Packing:\nShoes\nAdapter\nCharger", rt.text)
        assertEquals(listOf("", "bullet/0", "bullet/0", "bullet/0"), rt.items())
    }

    @Test fun boxesBecomeAChecklistWithTheirTicks() {
        val rt = layoutScan(page("☐ Milk", "☑ Eggs", "[x] Bread", "[ ] Jam"))
        assertEquals("Milk\nEggs\nBread\nJam", rt.text)
        assertEquals(listOf("check/0", "check/0/✓", "check/0/✓", "check/0"), rt.items())
    }

    @Test fun numbersBecomeANumberedList() {
        val rt = layoutScan(page("Shakshuka", "1. Soften an onion in olive oil until it's golden and sweet", "2) Add the tomatoes", "(3) Crack in the eggs", tall = setOf(0)))
        assertEquals("Shakshuka\nSoften an onion in olive oil until it's golden and sweet\nAdd the tomatoes\nCrack in the eggs", rt.text)
        assertEquals(listOf("", "number/0", "number/0", "number/0"), rt.items())
    }

    @Test fun indentedItemsAreOneLevelIn() {
        val rt = layoutScan(page("• Fruit", "• Apples", "• Pears", "• Dairy", left = { if (it == 1 || it == 2) 160 else 100 }))
        assertEquals(listOf("bullet/0", "bullet/1", "bullet/1", "bullet/0"), rt.items())
    }

    @Test fun sentencesStayPlainText() {
        val rt = layoutScan(page("Dear Sam,", "Thanks for the lovely dinner last night.", "We should do it again soon.", "See you on Friday."))
        assertEquals(listOf("", "", "", ""), rt.items())
    }

    @Test fun aReceiptsRowsStayTogetherAsPlainLines() {
        val rows = listOf("Milk" to "2.40", "Eggs" to "3.10", "Bread" to "4.25")
        val lines = rows.flatMapIndexed { i, (a, b) -> listOf(ScanLine(a, 100, 200 + i * 80, 400, 240 + i * 80), ScanLine(b, 800, 202 + i * 80, 900, 242 + i * 80)) }
        val rt = layoutScan(lines.shuffled(java.util.Random(1)))
        assertEquals("Milk | 2.40\nEggs | 3.10\nBread | 4.25", rt.text)
        assertEquals(listOf("", "", ""), rt.items())
    }

    @Test fun aListInTwoColumnsIsReadOneColumnThenTheOther() {
        val left = listOf("Milk", "Eggs", "Bread"); val right = listOf("Apples", "Jam", "Rice")
        val lines = left.mapIndexed { i, s -> ScanLine(s, 100, 200 + i * 80, 100 + 22 * s.length, 240 + i * 80) } +
            right.mapIndexed { i, s -> ScanLine(s, 600, 201 + i * 80, 600 + 22 * s.length, 241 + i * 80) }
        val rt = layoutScan(lines)
        assertEquals("Milk\nEggs\nBread\nApples\nJam\nRice", rt.text)
        assertEquals(List(6) { "bullet/0" }, rt.items())
    }

    @Test fun oneLineIsJustText() {
        assertEquals(RichText("Hello"), layoutScan(page("Hello")))
        assertEquals(RichText(), layoutScan(emptyList()))
    }

    // ML Kit's own lines for two test pictures (boxes as it gave them, marks as the picture shows them)

    @Test fun aBulletedListWhoseBulletsMLKitDroppedOrKept() {
        // "Groceries" in bold over five bullets; ML Kit kept two bullets, dropped the rest, and dropped the sub-item's dash
        val rt = layoutScan(listOf(
            ScanLine("Groceries", 94, 110, 462, 172),
            ScanLine("Oat milk", 144, 278, 346, 322, mark = Mark.BULLET),
            ScanLine("Free-range eggs", 144, 377, 553, 435, mark = Mark.BULLET),
            ScanLine("Sourdough", 105, 471, 411, 534, textLeft = 143, mark = Mark.BULLET),
            ScanLine("whole grain", 200, 574, 482, 632, mark = Mark.BULLET),
            ScanLine("Lemons", 95, 677, 339, 723, textLeft = 145, mark = Mark.BULLET),
        ))
        assertEquals("Groceries\nOat milk\nFree-range eggs\nSourdough\nwhole grain\nLemons", rt.text)
        assertEquals(listOf("", "bullet/0", "bullet/0", "bullet/0", "bullet/1", "bullet/0"), rt.items())
    }

    @Test fun aChecklistWithATickedBox() {
        val rt = layoutScan(listOf(
            ScanLine("Shopping", 92, 108, 453, 187),
            ScanLine("Bananas", 177, 273, 393, 319, mark = Mark.BOX),
            ScanLine("Coffee beans", 87, 369, 505, 421, textLeft = 177, mark = Mark.TICKED_BOX),
            ScanLine("Greek yogurt", 216, 470, 502, 531, mark = Mark.BOX),
            ScanLine("Spinach", 156, 570, 373, 632, textLeft = 178, mark = Mark.BOX),
        ))
        assertEquals(listOf("", "check/0", "check/0/✓", "check/0", "check/0"), rt.items())
    }

    @Test fun anUnmarkedHeadingAboveMarkedItemsStaysAHeading() {
        // in line with the marks, left of the items' words: over the list, not in it, though its letters aren't big
        val rt = layoutScan(listOf(
            ScanLine("Today", 90, 100, 200, 140),
            ScanLine("Call Sam", 150, 200, 326, 240, mark = Mark.BOX),
            ScanLine("Post parcel", 150, 280, 392, 320, mark = Mark.BOX),
        ))
        assertEquals(listOf("", "check/0", "check/0"), rt.items())
    }

    @Test fun aClearPictureIsReadOnceAnUnclearOneGetsTheCleanUpPasses() {
        assertEquals(true, com.jlees.minotes.scan.clearRead(listOf(0.95f, 0.9f, 0.85f)))
        assertEquals(false, com.jlees.minotes.scan.clearRead(listOf(0.9f, 0.4f, 0.5f))) // shadowed or faint
        assertEquals(false, com.jlees.minotes.scan.clearRead(emptyList()))             // nothing read: try harder
        assertEquals(false, com.jlees.minotes.scan.clearRead(listOf(Float.NaN, 0f)))   // no confidence given
    }
}
