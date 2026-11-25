package com.jlees.minotes

import com.jlees.minotes.scan.ImageEnhance
import org.junit.Assert.assertEquals
import org.junit.Test

/** The clean-up a scan is also read through (scan/ImageEnhance.kt, from mi. Budget). */
class ImageEnhanceTest {
    @Test fun inkIsFoundInShadowAndInLightAlike() {
        // a page half in shadow: grey paper (110) with darker writing (50) on the left, white paper (240) with grey
        // writing (150) on the right: lighter than the shadowed paper, so one cut-off for the whole page fails
        val w = 200; val h = 40
        val gray = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val ink = y in 15..24 && (x in 40..60 || x in 140..160)
            if (x < 100) (if (ink) 50 else 110) else (if (ink) 150 else 240)
        }
        val bw = ImageEnhance.adaptiveThreshold(gray, w, h)
        assertEquals(0, bw[20 * w + 50]); assertEquals(0, bw[20 * w + 150]) // both inks are ink
        assertEquals(255, bw[5 * w + 20]); assertEquals(255, bw[5 * w + 180]) // both papers are paper
    }

    @Test fun thinStrokesAreThickened() {
        val w = 9; val h = 9
        val bin = IntArray(w * h) { if (it == 4 * w + 4) 0 else 255 }
        val out = ImageEnhance.thickenDark(bin, w, h, 1)
        assertEquals(9, out.count { it == 0 }) // the one ink pixel and its eight neighbours
    }
}
