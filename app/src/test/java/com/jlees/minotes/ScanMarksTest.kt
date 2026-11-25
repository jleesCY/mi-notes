package com.jlees.minotes

import com.jlees.minotes.scan.Mark
import com.jlees.minotes.scan.findMark
import org.junit.Assert.assertEquals
import org.junit.Test

/** The mark in front of a scanned line, read from the picture (scan/ScanMarks.kt). Lines here are 50 px tall. */
class ScanMarksTest {
    /** A 90×70 patch of white paper with [draw]'s pixels in black. */
    private fun patch(draw: (Int, Int) -> Boolean) = IntArray(90 * 70) { i -> if (draw(i % 90, i / 90)) 20 else 245 }
    private fun mark(nearRight: Boolean = true, draw: (Int, Int) -> Boolean) = findMark(90, 70, patch(draw), 50, nearRight)

    private fun box(x: Int, y: Int) = x in 30..65 && y in 15..50 && (x < 34 || x > 61 || y < 19 || y > 46)
    private fun tick(x: Int, y: Int) = (x in 38..46 && y - x in -10..-4 + 0 && false) || (y in 30..40 && kotlin.math.abs((x - 38) - (y - 30)) <= 2) || (x in 44..58 && kotlin.math.abs((y - 40) + (x - 44)) <= 2)

    @Test fun anOutlinedSquareIsABox() = assertEquals(Mark.BOX, mark(draw = ::box))
    @Test fun aSquareWithATickInItIsTicked() = assertEquals(Mark.TICKED_BOX, mark { x, y -> box(x, y) || tick(x, y) })
    @Test fun aFilledSquareTheSizeOfABoxIsTicked() = assertEquals(Mark.TICKED_BOX, mark { x, y -> x in 30..65 && y in 15..50 })
    @Test fun aSmallFilledCircleIsADot() = assertEquals(Mark.BULLET, mark { x, y -> (x - 60) * (x - 60) + (y - 35) * (y - 35) <= 36 })
    @Test fun aSmallHollowCircleIsADot() = assertEquals(Mark.BULLET, mark { x, y -> ((x - 60) * (x - 60) + (y - 35) * (y - 35)).let { it in 25..64 } })
    @Test fun aShortFlatStrokeIsADash() = assertEquals(Mark.BULLET, mark { x, y -> x in 50..75 && y in 33..37 })
    @Test fun aTallThinStrokeIsNoMark() = assertEquals(null, mark { x, y -> x in 60..64 && y in 10..55 }) // an l or an I
    @Test fun blankPaperIsNoMark() = assertEquals(null, mark { _, _ -> false })
    @Test fun aFaintSmudgeIsNoMark() = assertEquals(null, findMark(90, 70, IntArray(90 * 70) { i -> if (i % 90 in 50..60 && i / 90 in 30..40) 215 else 240 }, 50, true))
    @Test fun specksAreNoMark() = assertEquals(null, mark { x, y -> (x == 20 && y == 20) || (x == 70 && y == 50) })

    @Test fun theMarkNearestTheWordsIsTaken() {
        // a dash at the far left (another column's), a dot right beside the words
        assertEquals(Mark.BULLET, mark { x, y -> (x in 2..27 && y in 33..37) || (x - 80) * (x - 80) + (y - 35) * (y - 35) <= 30 })
    }
}
