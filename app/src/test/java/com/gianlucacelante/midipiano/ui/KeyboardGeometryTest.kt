package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La geometria precalcolata dà gli stessi rettangoli della formula e offre gli elenchi dei tasti già pronti. */
class KeyboardGeometryTest {
    private val range = 36..96
    private val layout = KeyboardLayout(range, widthPx = 2500f, heightPx = 440f)

    @Test fun whiteAndBlackListsPartitionTheRange() {
        assertEquals(range.filterNot(::isBlackKey), layout.whiteKeys)
        assertEquals(range.filter(::isBlackKey), layout.blackKeys)
        assertEquals(range.count(), layout.whiteKeys.size + layout.blackKeys.size)
    }

    @Test fun rectsMatchTheDefinition() {
        val wkw = 2500f / range.filterNot(::isBlackKey).size
        for (n in range) {
            val r = layout.rect(n)
            val whiteIdx = (range.first until n).count { !isBlackKey(it) }
            if (isBlackKey(n)) {
                assertTrue(r.isBlack)
                assertEquals(whiteIdx * wkw, r.left + r.width / 2, 1e-3f) // sul confine destro del bianco che precede
                assertEquals(440f * 0.62f, r.height, 1e-3f)
                assertEquals(wkw * 0.6f, r.width, 1e-3f)
            } else {
                assertFalse(r.isBlack)
                assertEquals(whiteIdx * wkw, r.left, 1e-3f)
                assertEquals(wkw, r.width, 1e-3f)
                assertEquals(440f, r.height, 1e-3f)
            }
        }
    }

    @Test fun rectIsTheSameObjectEveryTime() {
        // niente allocazioni a ogni richiesta: il disegno chiede lo stesso tasto più volte per disegno
        assertTrue(layout.rect(60) === layout.rect(60))
    }

    @Test fun outOfRangeStaysNull() {
        assertEquals(null, layout.rectOrNull(10))
        assertEquals(null, layout.rectOrNull(120))
    }
}
