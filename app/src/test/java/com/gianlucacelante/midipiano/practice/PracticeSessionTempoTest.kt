package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTempoTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, 0.5, key, 80, hand)

    // Battute da 1 s (secPerBeat 0.25, 4/4); note ogni 0,5 s su 3 secondi: stanghette a 1 e 2
    private val song = Song(
        "s", "Prova", null, SongSource("t", "t"),
        listOf(n(0.0, 60), n(0.5, 62), n(1.0, 64), n(1.5, 65), n(2.0, 67), n(2.5, 69)),
        barLines = listOf(1.0, 2.0), secPerBeat = 0.25,
    )

    private fun session(countIn: Boolean = false): PracticeSession {
        val s = PracticeSession(song, Hand.BOTH)
        s.setMode(StudyMode.TEMPO, 0L)
        s.countInEnabled = countIn
        return s
    }

    @Test
    fun songTimeFollowsTheClockOnceStarted() {
        val s = session()
        s.play(ns(10.0))
        assertEquals(1.0, s.tick(ns(11.0)).songTimeSec, 1e-9)
        assertTrue(s.isPlaying)
    }

    @Test
    fun correctNotesAtTheRightTimeAreHits() {
        val s = session()
        s.play(0L)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(60, ns(0.0))) // la prima nota va suonata, altrimenti è mancata
        assertEquals(NoteResult.CORRECT, s.onNoteOn(62, ns(0.52)))
        assertEquals(0, s.mistakes)
    }

    @Test
    fun missedAndWrongNotesCountAsMistakes() {
        val s = session()
        s.play(0L)
        s.onNoteOn(99, ns(0.1)) // sbagliata
        val t = s.tick(ns(1.0)) // la nota a 0.0 e quella a 0.5 sono scadute
        assertEquals(2, t.newMisses)
        assertEquals(3, s.mistakes) // 2 mancate + 1 sbagliata
    }

    @Test
    fun countInRunsBeforeTheSongAndLastsAtLeastThreeSeconds() {
        val s = session(countIn = true)
        s.play(0L)
        assertEquals(-3.0, s.tick(0L).songTimeSec, 1e-9) // battute da 1 s: tre battute = 3 s di margine
        assertTrue(s.isCountingIn)
        assertEquals(0.5, s.tick(ns(3.5)).songTimeSec, 1e-9)
        assertFalse(s.isCountingIn)
    }

    @Test
    fun notesPlayedDuringTheCountInAreIgnoredButTheFirstNoteAtZeroIsAHit() {
        val s = session(countIn = true)
        s.play(0L) // conto alla rovescia: tempo del brano da -3.0 a 0.0
        assertEquals(NoteResult.IGNORED, s.onNoteOn(60, ns(1.5))) // a -1.5: ancora conto alla rovescia
        assertEquals(0, s.mistakes) // e non è un errore
        assertEquals(NoteResult.CORRECT, s.onNoteOn(60, ns(3.0))) // a 0.0: la prima nota del brano
        assertEquals(0, s.mistakes)
    }

    @Test
    fun songTimeIsAvailableImmediatelyAfterPlayWithoutWaitingForATick() {
        val s = session(countIn = true)
        s.play(ns(5.0))
        assertEquals(-3.0, s.songTimeAt(ns(5.0)), 1e-9) // subito, non 0
        assertEquals(-2.5, s.songTimeAt(ns(5.5)), 1e-9)
    }

    @Test
    fun peekingTheTimeDoesNotChangeTheSession() {
        val s = session()
        s.play(0L)
        s.songTimeAt(ns(100.0)) // oltre la fine: non deve fermare né segnare nulla
        assertTrue(s.isPlaying)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun countInIsScaledBySpeed() {
        val s = session(countIn = true)
        s.setSpeed(0.5, 0L)
        s.play(0L)
        assertEquals(-3.0, s.tick(0L).songTimeSec, 1e-9) // in tempo del brano: sempre lo stesso margine
        assertEquals(0.0, s.tick(ns(6.0)).songTimeSec, 1e-9) // ma ci mette il doppio in tempo reale
    }

    @Test
    fun theTimeWindowIsConstantInRealTimeWhateverTheSpeed() {
        // La nota a 0.5 s di brano. Colpita con 0,15 s di brano di ritardo:
        // a velocità 1 sono 0,15 s reali (dentro la finestra di 0,2 s)...
        val full = session()
        full.play(0L)
        assertEquals(NoteResult.CORRECT, full.onNoteOn(62, ns(0.65)))
        // ...a metà velocità sono 0,30 s reali (fuori): errore
        val half = session()
        half.setSpeed(0.5, 0L)
        half.play(0L)
        assertEquals(NoteResult.WRONG, half.onNoteOn(62, ns(1.30))) // brano a 0.65
        // e 0,05 s di brano (0,10 reali) a metà velocità sono ancora validi
        val ok = session()
        ok.setSpeed(0.5, 0L)
        ok.play(0L)
        assertEquals(NoteResult.CORRECT, ok.onNoteOn(62, ns(1.10))) // brano a 0.55
    }

    @Test
    fun loopRepeatsTheChosenBarsAndCountsLaps() {
        val s = session()
        s.setLoopBars(2, 2) // da 1.0 a 2.0
        s.seekTo(1.0, 0L)
        s.play(0L)
        val t = s.tick(ns(1.2)) // 1.0 + 1.2 = 2.2 -> torna a 1.2
        assertTrue(t.looped)
        assertEquals(1.2, t.songTimeSec, 1e-9)
        assertEquals(1, s.loopsCompleted)
    }

    @Test
    fun loopRestartsTheEvaluationOfThatSection() {
        val s = session()
        s.setLoopBars(2, 2)
        s.seekTo(1.0, 0L)
        s.play(0L)
        s.onNoteOn(64, ns(0.0)) // colpisce la nota a 1.0
        s.tick(ns(1.2)) // giro completato: la sezione si azzera
        assertEquals(0, s.mistakes)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(64, ns(1.2 + 0.8))) // di nuovo valida al giro dopo (tempo 2.0 -> 1.0)
    }

    @Test
    fun finishingTheSongStopsThePlayback() {
        val s = session()
        s.play(0L)
        val t = s.tick(ns(10.0))
        assertTrue(t.finished)
        assertTrue(s.isFinished)
        assertFalse(s.isPlaying)
    }

    @Test
    fun pauseKeepsThePosition() {
        val s = session()
        s.play(0L)
        s.pause(ns(1.0))
        assertEquals(1.0, s.tick(ns(5.0)).songTimeSec, 1e-9)
    }

    @Test
    fun progressFollowsTheClockInTempoMode() {
        val s = session()
        s.play(0L)
        s.tick(ns(1.5))
        assertEquals(0.5f, s.progress, 0.01f) // durata 3.0
    }

    @Test
    fun expectedKeysShowNotesDueSoon() {
        val s = session()
        s.play(0L)
        s.tick(ns(0.45))
        assertTrue(62 in s.expectedKeys) // è la nota a 0.5
        assertFalse(69 in s.expectedKeys) // a 2.5: troppo lontana
    }

    @Test
    fun switchingBackToWaitModeStopsTheClockAndResets() {
        val s = session()
        s.play(0L)
        s.setMode(StudyMode.WAIT, ns(1.0))
        assertFalse(s.isPlaying)
        assertEquals(0, s.stepIndex)
    }

    @Test
    fun seekToBarMovesTheClock() {
        val s = session()
        s.seekToBar(3, 0L)
        assertEquals(2.0, s.tick(ns(0.0)).songTimeSec, 1e-9)
    }
}
