package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Step
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitModeEngineTest {
    private fun step(t: Double, vararg keys: Int) =
        Step(t, keys.map { SongNote(t, 0.5, it, 80, Hand.RIGHT) })

    @Test
    fun advancesOnlyWhenTheRightNoteIsPlayed() {
        val e = WaitModeEngine(listOf(step(0.0, 60), step(1.0, 62)))
        assertEquals(NoteResult.WRONG, e.onNoteOn(61))
        assertEquals(0, e.index)
        assertEquals(NoteResult.STEP_COMPLETED, e.onNoteOn(60))
        assertEquals(1, e.index)
        assertEquals(NoteResult.FINISHED, e.onNoteOn(62))
        assertTrue(e.isFinished)
        assertEquals(1, e.mistakes)
    }

    @Test
    fun chordNotesInAnyOrder() {
        val e = WaitModeEngine(listOf(step(0.0, 60, 64, 67)))
        assertEquals(NoteResult.CORRECT, e.onNoteOn(67))
        assertEquals(setOf(60, 64), e.expectedKeys)
        assertEquals(NoteResult.CORRECT, e.onNoteOn(60))
        assertEquals(NoteResult.FINISHED, e.onNoteOn(64))
    }

    @Test
    fun repeatedKeyInsideChordDoesNotCompleteIt() {
        val e = WaitModeEngine(listOf(step(0.0, 60, 64)))
        e.onNoteOn(60)
        assertEquals(NoteResult.WRONG, e.onNoteOn(60)) // già suonata: non conta due volte
        assertFalse(e.isFinished)
    }

    @Test
    fun afterFinishNotesAreIgnored() {
        val e = WaitModeEngine(listOf(step(0.0, 60)))
        e.onNoteOn(60)
        assertEquals(NoteResult.IGNORED, e.onNoteOn(60))
        assertEquals(0, e.mistakes)
    }

    @Test
    fun restartResetsProgressAndMistakes() {
        val e = WaitModeEngine(listOf(step(0.0, 60), step(1.0, 62)))
        e.onNoteOn(61)
        e.onNoteOn(60)
        e.restart()
        assertEquals(0, e.index)
        assertEquals(0, e.mistakes)
        assertEquals(setOf(60), e.expectedKeys)
    }

    @Test
    fun emptySongIsImmediatelyFinished() {
        assertTrue(WaitModeEngine(emptyList()).isFinished)
    }
}
