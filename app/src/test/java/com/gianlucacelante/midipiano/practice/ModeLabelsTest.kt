package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeLabelsTest {
    @Test
    fun theButtonShowsTheModeAndTheHand() {
        assertEquals("Impara", ModeLabels.pill(StudyMode.WAIT, Hand.BOTH))
        assertEquals("Impara · destra", ModeLabels.pill(StudyMode.WAIT, Hand.RIGHT))
        assertEquals("Impara · sinistra", ModeLabels.pill(StudyMode.WAIT, Hand.LEFT))
        assertEquals("A tempo", ModeLabels.pill(StudyMode.TEMPO, Hand.BOTH))
        assertEquals("A tempo · destra", ModeLabels.pill(StudyMode.TEMPO, Hand.RIGHT))
        assertEquals("Ascolta", ModeLabels.pill(StudyMode.LISTEN, Hand.RIGHT)) // ascoltando suona tutto
    }

    @Test
    fun theDescriptionExplainsWhatTheAppDoes() {
        assertTrue(ModeLabels.describe(StudyMode.WAIT, Hand.BOTH, backing = true).contains("aspetta"))
        val one = ModeLabels.describe(StudyMode.TEMPO, Hand.RIGHT, backing = true)
        assertTrue(one, one.contains("sinistra") && one.contains("suona"))
        val off = ModeLabels.describe(StudyMode.TEMPO, Hand.RIGHT, backing = false)
        assertTrue(off, !off.contains("l'app suona"))
        assertTrue(ModeLabels.describe(StudyMode.LISTEN, Hand.BOTH, true).contains("guarda"))
    }

    @Test
    fun theAccompanimentSwitchOnlyMattersForOneHandInTempoMode() {
        assertTrue(ModeLabels.backingApplies(StudyMode.TEMPO, Hand.RIGHT))
        assertTrue(!ModeLabels.backingApplies(StudyMode.TEMPO, Hand.BOTH))
        assertTrue(!ModeLabels.backingApplies(StudyMode.WAIT, Hand.LEFT))
    }
}
