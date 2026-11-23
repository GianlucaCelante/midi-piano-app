package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cambio di passo di un esercizio (applyStep): il nuovo giro riparte dall'inizio, con il conto alla rovescia. */
class StepChangeTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.4, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(1.0, 62), n(2.0, 64)))

    private fun finishATempoRun(): PracticeSession {
        val s = PracticeSession(song, Hand.RIGHT)
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        s.tick(sec(0.0))
        s.tick(sec(60.0)) // oltre la fine: il giro è finito
        assertTrue(s.isFinished)
        return s
    }

    /** La stessa sequenza di MidiViewModel.applyStep. */
    private fun applyStep(s: PracticeSession, hand: Hand, mode: StudyMode, speed: Double, now: Long) {
        s.setHand(hand)
        s.setMode(mode, now)
        s.setSpeed(speed, now)
        s.restart(now)
    }

    @Test
    fun afterAFinishedRunTheNextStepStartsWithTheCountIn() {
        val s = finishATempoRun()
        applyStep(s, Hand.RIGHT, StudyMode.TEMPO, 0.75, sec(100.0))
        s.play(sec(100.0))
        val t = s.tick(sec(100.0))
        assertTrue("conto alla rovescia: ${t.songTimeSec}", s.isCountingIn && t.songTimeSec < 0.0)
        assertFalse(s.isFinished)
        assertFalse(s.tick(sec(100.5)).finished)
    }

    @Test
    fun movingFromWaitToTempoStartsBeforeTheFirstNoteEvenWhenItIsLate() {
        val late = Song("s", "t", null, SongSource("t", "t"), listOf(n(2.0, 60), n(3.0, 62)))
        val s = PracticeSession(late, Hand.RIGHT)
        applyStep(s, Hand.RIGHT, StudyMode.TEMPO, 1.0, 0L)
        s.play(0L)
        val t = s.tick(0L)
        assertTrue("parte prima della prima nota: ${t.songTimeSec}", t.songTimeSec < 2.0 - 1.0)
    }
}
