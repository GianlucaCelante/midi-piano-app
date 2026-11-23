package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffGeometryTest {
    private val s = 26f
    private val tb = 300f
    private val bb = 640f
    private val width = 2560f

    private fun rects(a: FloatArray) = (0 until a.size / 4).map { listOf(a[it * 4], a[it * 4 + 1], a[it * 4 + 2], a[it * 4 + 3]) }

    @Test fun staffHasTenLinesOfTheRightThicknessAndPlace() {
        val r = rects(StaffGeometry.staffLines(s, tb, bb, width))
        assertEquals(10, r.size)
        for (line in 0..4) {
            for (bottom in listOf(tb, bb)) {
                val y = bottom - line * s
                assertTrue("manca la linea a y=$y", r.any { it[0] == 0f && it[2] == width && Math.abs((it[1] + it[3]) / 2 - y) < 1e-3f && Math.abs(it[3] - it[1] - 1.5f) < 1e-3f })
            }
        }
    }

    @Test fun ghostDashesStartAtZeroAndStayInsideTheWidth() {
        val r = rects(StaffGeometry.ghostDashes(s, tb, bb, width))
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it[0] >= 0f && it[2] <= width + 1e-3f && it[2] > it[0] })
        assertTrue(r.all { it[2] - it[0] <= 0.45f * s + 1e-3f })
        assertTrue(r.any { it[0] == 0f }) // ancorati a x = 0, non a dove comincia il rigo disegnato
        assertEquals(12 * Math.ceil((width / s).toDouble()).toInt(), r.size) // 3 sopra e 3 sotto, per due righi
    }

    @Test fun ghostLinesSitThreeStepsAboveAndBelowEachStaff() {
        val r = rects(StaffGeometry.ghostDashes(s, tb, bb, width))
        val ys = r.map { (it[1] + it[3]) / 2 }.toSet().map { Math.round(it) }.toSet()
        for (k in 1..3) for (bottom in listOf(tb, bb)) {
            assertTrue(Math.round(bottom + k * s) in ys) // sotto
            assertTrue(Math.round(bottom - (4 + k) * s) in ys) // sopra
        }
        assertEquals(12, ys.size)
    }

    @Test fun cacheKeyDoesNotDependOnTheDrawnRange() {
        // lo stesso rigo disegnato in due intervalli diversi usa la stessa geometria
        assertEquals(StaffGeometry.key(s, tb, bb, width), StaffGeometry.key(s, tb, bb, width))
        assertTrue(StaffGeometry.key(s, tb, bb, width) != StaffGeometry.key(s + 1, tb, bb, width))
    }
}
