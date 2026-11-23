package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitChordFlowTest {
    private fun n(t: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, 0.5, key, 80, hand)
    private fun sec(s: Double) = (s * 1e9).toLong()

    // t=0: accordo di 3 note; t=2: una nota; t=4: una nota
    private val song = Song("s", "t", null, SongSource("t", "t"),
        listOf(n(0.0, 60), n(0.0, 64), n(0.0, 67), n(2.0, 62), n(4.0, 65)))

    private fun session() = PracticeSession(song, Hand.RIGHT).also { it.autoFlow = true; it.countInEnabled = true }

    @Test
    fun aChordPressedOneNoteAtATimeFarApartDoesNotAdvance() {
        val s = session()
        s.onNoteOn(60, sec(0.0))
        s.onNoteOn(64, sec(1.0)) // un secondo dopo: la prima non conta più
        s.onNoteOn(67, sec(2.0))
        assertEquals(0, s.stepIndex)
    }

    @Test
    fun aChordPressedTogetherAdvances() {
        val s = session()
        s.onNoteOn(60, sec(0.00))
        s.onNoteOn(64, sec(0.05))
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(67, sec(0.12)))
        assertEquals(1, s.stepIndex)
    }

    @Test
    fun pressingTheSameNoteAgainDoesNotCompleteTheChord() {
        val s = session()
        s.onNoteOn(60, sec(0.0)); s.onNoteOn(60, sec(0.05)); s.onNoteOn(60, sec(0.1))
        assertEquals(0, s.stepIndex)
    }

    @Test
    fun afterACorrectStepTheCursorFlowsInsteadOfJumping() {
        val s = session()
        s.onNoteOn(60, sec(0.0)); s.onNoteOn(64, sec(0.0)); s.onNoteOn(67, sec(0.0))
        assertTrue(s.isPlaying) // l'orologio parte da solo
        assertEquals(0.0, s.currentTimeSec, 0.01) // resta dove ha suonato...
        s.tick(sec(0.0))
        assertEquals(1.0, s.tick(sec(1.0)).songTimeSec, 0.01) // ...e scorre a tempo verso la nota dopo
        assertEquals(2.0, s.tick(sec(5.0)).songTimeSec, 1e-6) // si ferma ad aspettarla
    }

    @Test
    fun withoutAutoFlowTheCursorStillJumps() {
        val s = PracticeSession(song, Hand.RIGHT)
        s.onNoteOn(60); s.onNoteOn(64); s.onNoteOn(67)
        assertFalse(s.isPlaying)
        assertEquals(2.0, s.currentTimeSec, 1e-6)
    }

    @Test
    fun playingFromTheStartInWaitModeLeavesAMarginBeforeTheFirstNote() {
        val s = session()
        s.play(sec(0.0))
        val t = s.tick(sec(0.0)).songTimeSec
        assertTrue("parte prima della prima nota: $t", t <= -2.9)
        assertEquals(0.0, s.tick(sec(10.0)).songTimeSec, 1e-6) // la prima nota poi aspetta
    }

    @Test
    fun tempoCountInLastsAtLeastThreeSeconds() {
        val s = PracticeSession(song, Hand.RIGHT) // 4/4 a 120 bpm: battuta = 2 s
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        assertTrue(s.tick(0L).songTimeSec <= -3.9) // due battute = 4 s
    }
}
