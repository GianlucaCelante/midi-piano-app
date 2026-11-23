package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoModeEngineTest {
    private fun step(t: Double, vararg keys: Int) = Step(t, keys.map { SongNote(t, 0.5, it, 80, Hand.RIGHT) })

    private fun engine(vararg steps: Step, tol: Double = 0.2) = TempoModeEngine(steps.toList(), tol)

    @Test
    fun aNoteInsideTheWindowIsAHitWithItsDeviation() {
        val e = engine(step(1.0, 60))
        val r = e.onNoteOn(60, 1.1)
        assertEquals(HitKind.HIT, r.kind)
        assertEquals(0.1, r.deviationSec!!, 1e-9) // in ritardo: positivo
        assertEquals(1, e.hits)
    }

    @Test
    fun earlyNotesHaveNegativeDeviation() {
        val e = engine(step(1.0, 60))
        assertEquals(-0.15, e.onNoteOn(60, 0.85).deviationSec!!, 1e-9)
    }

    @Test
    fun outsideTheWindowOrWrongKeyIsWrong() {
        val e = engine(step(1.0, 60))
        assertEquals(HitKind.WRONG, e.onNoteOn(60, 1.5).kind) // troppo tardi
        assertEquals(HitKind.WRONG, e.onNoteOn(61, 1.0).kind) // altra nota
        assertEquals(2, e.wrong)
        assertEquals(0, e.hits)
    }

    @Test
    fun notesNotPlayedBecomeMissedOnlyAfterTheWindow() {
        val e = engine(step(1.0, 60), step(2.0, 62))
        assertEquals(0, e.onTick(1.1))
        assertEquals(1, e.onTick(1.3)) // 1.0 + 0.2 superato
        assertEquals(1, e.misses)
        assertEquals(HitState.MISSED, e.stateOf(0, 60))
        assertEquals(HitState.PENDING, e.stateOf(1, 62))
    }

    @Test
    fun aKeyCannotHitTwice() {
        val e = engine(step(1.0, 60))
        assertEquals(HitKind.HIT, e.onNoteOn(60, 1.0).kind)
        assertEquals(HitKind.WRONG, e.onNoteOn(60, 1.05).kind)
        assertEquals(1, e.hits)
    }

    @Test
    fun theClosestPendingNoteWinsForRepeatedNotes() {
        val e = engine(step(1.0, 60), step(1.3, 60)) // la stessa nota due volte, ravvicinata
        e.onNoteOn(60, 1.28) // più vicina alla seconda
        assertEquals(HitState.PENDING, e.stateOf(0, 60))
        assertEquals(HitState.HIT, e.stateOf(1, 60))
    }

    @Test
    fun chordNotesAreEvaluatedIndependently() {
        val e = engine(step(1.0, 60, 64, 67))
        e.onNoteOn(60, 1.02); e.onNoteOn(64, 1.05)
        e.onTick(1.3)
        assertEquals(2, e.hits)
        assertEquals(1, e.misses)
    }

    @Test
    fun accuracyAndMeanDeviationSummarizeThePerformance() {
        val e = engine(step(1.0, 60), step(2.0, 62), step(3.0, 64), step(4.0, 65))
        e.onNoteOn(60, 1.1); e.onNoteOn(62, 1.9); e.onNoteOn(64, 3.0)
        e.onTick(5.0) // 65 mancata
        assertEquals(3, e.hits); assertEquals(1, e.misses)
        assertEquals(0.75, e.accuracy, 1e-9)
        assertEquals(0.0, e.meanDeviationSec, 1e-9) // +0.1 -0.1 +0
        assertEquals(0.2 / 3, e.meanAbsDeviationSec, 1e-9)
    }

    @Test
    fun resetRangeRestartsOnlyThatSection() {
        val e = engine(step(1.0, 60), step(2.0, 62), step(5.0, 64))
        e.onNoteOn(60, 1.0); e.onTick(6.0)
        e.resetRange(0.0, 3.0)
        assertEquals(HitState.PENDING, e.stateOf(0, 60))
        assertEquals(HitState.PENDING, e.stateOf(1, 62))
        assertEquals(HitState.MISSED, e.stateOf(2, 64)) // fuori dall'intervallo: resta com'è
        assertEquals(0, e.hits)
        assertEquals(1, e.misses)
    }

    @Test
    fun notesBeforeAJumpAreSkippedAndNeverCountAsMissed() {
        val e = engine(step(1.0, 60), step(2.0, 62), step(3.0, 64))
        e.skipBefore(2.0)
        e.onTick(10.0)
        assertEquals(HitState.SKIPPED, e.stateOf(0, 60))
        assertEquals(2, e.misses) // solo 62 e 64
        assertEquals(0.0, e.accuracy, 1e-9)
    }

    @Test
    fun onTickCanBeLimitedToNotesBeforeAGivenTime() {
        val e = engine(step(1.0, 60), step(2.0, 62))
        assertEquals(1, e.onTick(10.0, onlyBefore = 2.0)) // solo 60
        assertEquals(HitState.PENDING, e.stateOf(1, 62))
    }

    @Test
    fun skippedNotesCannotBeHit() {
        val e = engine(step(1.0, 60))
        e.skipBefore(5.0)
        assertEquals(HitKind.WRONG, e.onNoteOn(60, 1.0).kind)
    }

    @Test
    fun anEmptySongHasPerfectAccuracy() {
        assertEquals(1.0, engine().accuracy, 1e-9)
    }

    @Test
    fun eventsAreTimeOrderedAndCoverEveryStepNote() {
        val e = engine(step(1.0, 60, 64), step(2.0, 62))
        assertEquals(3, e.expected.size)
        assertTrue(e.expected.zipWithNext().all { (a, b) -> a.timeSec <= b.timeSec })
    }
}
