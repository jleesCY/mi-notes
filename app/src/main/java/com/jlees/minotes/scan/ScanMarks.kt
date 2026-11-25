// The mark in front of a scanned line, from the picture itself: a box, a ticked box, a dot or a dash. ML Kit reads
// words, and drops or misreads these (a box comes back as "D", a ticked one as "M", a bullet as nothing at all), so the
// patch of picture where the mark sits is looked at instead: the dark shape there, measured against the line's height.
// Pure (a grid of brightness in, a Mark out), so it's tested on the JVM (ScanMarksTest).
package com.jlees.minotes.scan

/** What a list line starts with: a box (ticked or not), or a bullet (a dot, filled or hollow, or a dash). */
enum class Mark { BOX, TICKED_BOX, BULLET }

/**
 * The mark in a [w]×[h] patch of picture ([lum]: each pixel's brightness, 0–255, row by row), for a line [lineH] pixels
 * tall; null when there's nothing there that looks like one (a letter, a smudge, blank paper). [nearRight]: the mark is
 * the shape nearest the patch's right edge (a patch to the left of a line's words); else the biggest shape in it.
 */
fun findMark(w: Int, h: Int, lum: IntArray, lineH: Int, nearRight: Boolean): Mark? {
    if (w <= 0 || h <= 0 || lineH <= 0 || lum.size < w * h) return null
    // ink is what's clearly darker than the paper here; too little contrast, and there's no ink at all
    val sorted = lum.copyOf(w * h).also { it.sort() }
    val paper = sorted[(sorted.size * 0.85).toInt().coerceAtMost(sorted.lastIndex)]
    val darkest = sorted[(sorted.size * 0.001).toInt()] // a bullet is a speck of the patch: its darkest pixels, not a share of it
    if (paper - darkest < 50) return null
    val cut = (paper + darkest) / 2
    val ink = BooleanArray(w * h) { lum[it] < cut }

    // the shapes: ink pixels touching side by side
    val seen = BooleanArray(w * h)
    var best: Shape? = null
    val queue = IntArray(w * h)
    for (start in 0 until w * h) {
        if (!ink[start] || seen[start]) continue
        var head = 0; var tail = 0
        queue[tail++] = start; seen[start] = true
        val s = Shape(w, 0, h, 0)
        while (head < tail) {
            val p = queue[head++]; val x = p % w; val y = p / w
            s.add(x, y)
            if (x > 0 && ink[p - 1] && !seen[p - 1]) { seen[p - 1] = true; queue[tail++] = p - 1 }
            if (x < w - 1 && ink[p + 1] && !seen[p + 1]) { seen[p + 1] = true; queue[tail++] = p + 1 }
            if (y > 0 && ink[p - w] && !seen[p - w]) { seen[p - w] = true; queue[tail++] = p - w }
            if (y < h - 1 && ink[p + w] && !seen[p + w]) { seen[p + w] = true; queue[tail++] = p + w }
        }
        // big enough to be drawn on purpose, small enough to sit beside a line of text
        val size = maxOf(s.width, s.height)
        if (s.count < 4 || size < lineH * 0.12 || size > lineH * 1.4) continue
        // beside the words, a shape running into the patch's right edge is the start of a word (ML Kit's box for a
        // line can begin a letter or two late), not a mark: a mark has a gap before the text
        if (nearRight && s.maxX >= w - 2) continue
        val b = best
        best = when {
            b == null -> s
            nearRight -> if (s.maxX > b.maxX) s else b
            else -> if (s.count > b.count) s else b
        }
    }
    val s = best ?: return null

    val aspect = s.width.toFloat() / s.height
    val fill = s.count.toFloat() / (s.width * s.height)
    val size = maxOf(s.width, s.height).toFloat() / lineH
    return when {
        // long and flat: a dash (-, –, —)
        aspect >= 2.2f && s.height <= lineH * 0.3 -> Mark.BULLET
        aspect < 0.55f || aspect > 1.8f -> null // a letter (l, I, 1) or a stroke, not a mark
        // about a capital letter's height: a box. Ink in its middle (a tick, a cross, or filled in) is ticked
        size >= 0.5f -> if (fill >= 0.6f || inkInside(s, ink, w) >= 0.06f) Mark.TICKED_BOX else Mark.BOX
        // smaller, round or square: a dot (•, ◦, ▪)
        else -> Mark.BULLET
    }
}

/** A shape's bounds and how many ink pixels it has. */
private class Shape(var minX: Int, var maxX: Int, var minY: Int, var maxY: Int) {
    var count = 0
    val width get() = maxX - minX + 1
    val height get() = maxY - minY + 1
    fun add(x: Int, y: Int) { count++; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
}

/** How much of the middle of a box is inked (its outline left out): any ink, the box's own or a separate tick. */
private fun inkInside(s: Shape, ink: BooleanArray, w: Int): Float {
    val x0 = s.minX + s.width / 4; val x1 = s.maxX - s.width / 4
    val y0 = s.minY + s.height / 4; val y1 = s.maxY - s.height / 4
    if (x1 < x0 || y1 < y0) return 0f
    var n = 0
    for (y in y0..y1) for (x in x0..x1) if (ink[y * w + x]) n++
    return n.toFloat() / ((x1 - x0 + 1) * (y1 - y0 + 1))
}
