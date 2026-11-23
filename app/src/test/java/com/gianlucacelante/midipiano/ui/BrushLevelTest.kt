package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'opacità dei tasti «in arrivo» si arrotonda a pochi livelli: così i pennelli si riusano invece di crearne uno nuovo a ogni fotogramma. */
class BrushLevelTest {
    @Test fun levelsStayInRangeAndAreMonotonic() {
        var prev = -1
        for (i in 0..100) {
            val l = BrushLevels.level(i / 100f)
            assertTrue(l in 0..BrushLevels.STEPS)
            assertTrue(l >= prev); prev = l
        }
        assertEquals(0, BrushLevels.level(0f))
        assertEquals(BrushLevels.STEPS, BrushLevels.level(1f))
    }

    @Test fun outOfRangeValuesAreClamped() {
        assertEquals(0, BrushLevels.level(-3f))
        assertEquals(BrushLevels.STEPS, BrushLevels.level(7f))
    }

    @Test fun alphaOfALevelIsCloseToTheOriginal() {
        for (i in 0..100) {
            val a = i / 100f
            assertEquals(a, BrushLevels.alpha(BrushLevels.level(a)), 0.5f / BrushLevels.STEPS + 1e-4f) // non più di mezzo gradino di scarto
        }
    }

    @Test fun alphaIsTheInverseAtTheEnds() {
        assertEquals(0f, BrushLevels.alpha(0), 0f)
        assertEquals(1f, BrushLevels.alpha(BrushLevels.STEPS), 1e-6f)
    }
}
