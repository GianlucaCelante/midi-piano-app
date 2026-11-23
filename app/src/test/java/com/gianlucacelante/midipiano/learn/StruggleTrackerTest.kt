package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dopo alcuni giri falliti dello stesso passo l'app propone di ripassare le battute deboli. */
class StruggleTrackerTest {
    @Test fun suggestsOnlyAfterThreeFailedRuns() {
        val t = StruggleTracker()
        t.onFailedRun(mapOf(3 to 2)); t.onFailedRun(mapOf(3 to 1))
        assertFalse(t.active)
        t.onFailedRun(mapOf(2 to 1, 3 to 3))
        assertTrue(t.active)
        assertEquals(2..3, t.reviewRange(barCount = 4)) // le due battute con più errori messe insieme
    }

    @Test fun errorsAccumulateAcrossRunsSoTheWeakSpotIsStable() {
        val t = StruggleTracker()
        t.onFailedRun(mapOf(1 to 1, 4 to 2)); t.onFailedRun(mapOf(4 to 2, 3 to 1)); t.onFailedRun(mapOf(4 to 1))
        assertEquals(3..4, t.reviewRange(barCount = 4))
    }

    @Test fun aCleanRunOrAStepChangeResets() {
        val t = StruggleTracker()
        repeat(3) { t.onFailedRun(mapOf(2 to 1)) }
        t.onGoodRun()
        assertFalse(t.active); assertNull(t.reviewRange(4))
        repeat(3) { t.onFailedRun(mapOf(2 to 1)) }
        t.reset()
        assertFalse(t.active)
    }

    @Test fun noErrorsNoRange() {
        val t = StruggleTracker()
        repeat(3) { t.onFailedRun(emptyMap()) }
        assertTrue(t.active)
        assertNull(t.reviewRange(4)) // si è falliti ma senza un punto debole preciso: nessuna battuta da proporre
    }
}
