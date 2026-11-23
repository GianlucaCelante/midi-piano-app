package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Correzioni dalla revisione: esercizi in «Impara» non finiscono tra i difficili, sigle copertina, nomi con ottava. */
class ReviewFixesTest {
    private fun rec(i: Int, mode: String, outcome: String) = RunRecord(
        whenMillis = LocalDate.of(2026, 11, 1 + i).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), source = "exercise", id = "e1", title = "E1",
        mode = mode, hand = "BOTH", speed = 1.0, outcome = outcome, hits = 9, misses = 0, wrong = 0, accuracy = 1.0,
    )

    @Test fun waitOnlySuccessfulExerciseIsNotHard() {
        val s = Analytics.compute(List(4) { rec(it, "WAIT", "passed") }, ZoneOffset.UTC)
        assertTrue(s.hardest.isEmpty())
    }

    @Test fun waitOnlyFailedExerciseIsStillHard() {
        val s = Analytics.compute(List(4) { rec(it, "WAIT", "failed") }, ZoneOffset.UTC)
        assertEquals(1, s.hardest.size)
    }

    @Test fun coverInitialsSkipSmallWords() {
        assertEquals("BS", Covers.initials("Ballata in Sol"))
        assertEquals("BM", Covers.initials("Ballata in Mi minore"))
        assertEquals("GD", Covers.initials("Giro di Do (I-vi-IV-V)"))
    }

    @Test fun keyNameWithOctave() {
        assertEquals("do4", Insights.keyNameOctave(60))
        assertEquals("sol♯3", Insights.keyNameOctave(56))
    }
}
