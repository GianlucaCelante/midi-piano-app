package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quali tasti vanno ridisegnati sopra l'immagine «a riposo» della tastiera. */
class KeyboardOverlayPlanTest {
    private val range = 36..96

    @Test fun aHighlightedWhiteKeyAlsoRedrawsTheBlackKeysNextToIt() {
        val p = KeyboardOverlayPlan.of(range, setOf(62)) // Re4: a sinistra Do#4 (61), a destra Re#4 (63)
        assertEquals(listOf(62), p.whites)
        assertEquals(setOf(61, 63), p.blacks)
    }

    @Test fun aWhiteKeyWithNoBlackNeighbourAddsNoBlackKeys() {
        val p = KeyboardOverlayPlan.of(range, setOf(64)) // Mi4: a sinistra Re#4 (nero), a destra Fa4 (bianco)
        assertEquals(setOf(63), p.blacks)
        val q = KeyboardOverlayPlan.of(range, setOf(65)) // Fa4: a sinistra Mi4 (bianco), a destra Fa#4 (nero)
        assertEquals(setOf(66), q.blacks)
    }

    @Test fun aHighlightedBlackKeyIsRedrawnAndAddsNoWhiteKey() {
        val p = KeyboardOverlayPlan.of(range, setOf(61))
        assertTrue(p.whites.isEmpty())
        assertEquals(setOf(61), p.blacks)
    }

    @Test fun keysOutsideTheRangeAreIgnoredAndWhitesComeInOrder() {
        val p = KeyboardOverlayPlan.of(range, setOf(10, 120, 72, 60, 67))
        assertEquals(listOf(60, 67, 72), p.whites)
        assertTrue(p.blacks.all { it in range })
        // ai bordi dell'intervallo non si cercano neri oltre
        assertEquals(emptySet<Int>(), KeyboardOverlayPlan.of(36..48, setOf(36)).blacks - setOf(37))
    }
}
