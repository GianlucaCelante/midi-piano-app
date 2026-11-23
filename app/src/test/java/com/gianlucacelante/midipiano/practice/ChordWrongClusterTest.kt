package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Step
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un accordo tentato con tasti vicini di troppo è un solo errore, non uno per ogni tasto sbagliato. */
class ChordWrongClusterTest {
    private fun n(t: Double, key: Int) = SongNote(t, 1.0, key, 80, Hand.RIGHT, beats = 2.0)
    private val steps = listOf(Step(2.0, listOf(n(2.0, 50), n(2.0, 55), n(2.0, 71))), Step(6.0, listOf(n(6.0, 60))))

    @Test fun tempoThreeStrayKeysInOneChordAttemptCountOnce() {
        val e = TempoModeEngine(steps)
        assertEquals(HitKind.WRONG, e.onNoteOn(52, 2.00).kind)
        val second = e.onNoteOn(53, 2.01)
        val third = e.onNoteOn(69, 2.03)
        assertEquals(1, e.wrong)
        assertFalse(e.onNoteOn(40, 4.0).foldedIntoPreviousError) // un tasto sbagliato molto dopo è un errore nuovo
        assertEquals(2, e.wrong)
        assertTrue(second.foldedIntoPreviousError)
        assertTrue(third.foldedIntoPreviousError)
    }

    @Test fun tempoWrongPressesApartAreSeparateErrors() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(52, 2.0); e.onNoteOn(53, 2.6); e.onNoteOn(54, 3.4)
        assertEquals(3, e.wrong)
    }

    @Test fun tempoClusterWindowScalesWithSpeed() {
        val slow = TempoModeEngine(steps, toleranceSec = 0.4) // metà velocità: la tolleranza raddoppia
        slow.onNoteOn(52, 2.0); slow.onNoteOn(53, 2.2) // 0,2 s di brano = 0,1 s reali: stesso gesto
        assertEquals(1, slow.wrong)
    }

    @Test fun waitStrayKeysWithinTheChordWindowCountOnce() {
        val w = WaitModeEngine(steps)
        val ms = 1_000_000L
        assertEquals(NoteResult.WRONG, w.onNoteOn(52, 0L))
        assertEquals(NoteResult.WRONG, w.onNoteOn(53, 20 * ms)) // ancora segnalato come sbagliato, ma non si somma
        assertEquals(1, w.mistakes)
        w.onNoteOn(54, 900 * ms)
        assertEquals(2, w.mistakes)
    }
}
