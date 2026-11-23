package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** «A tempo» negli esercizi: si controlla anche per quanto si tiene premuto ogni tasto. */
class HoldSessionTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, d: Double, key: Int) = SongNote(t, d, key, 80, Hand.RIGHT, beats = d * 2)
    private fun song(vararg notes: SongNote) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)

    private fun session(check: Boolean = true, speed: Double = 1.0, vararg notes: SongNote): PracticeSession {
        val s = PracticeSession(song(*notes))
        s.countInEnabled = false
        s.checkHolds = check
        s.setMode(StudyMode.TEMPO, 0L)
        if (speed != 1.0) s.setSpeed(speed, 0L)
        s.play(0L)
        return s
    }

    // nota di 2 s (4 battiti) seguita da una pausa di 2 s, poi un'altra nota
    private val melody = arrayOf(n(0.0, 2.0, 60), n(4.0, 1.0, 62), n(6.0, 1.0, 64))

    @Test fun holdingTheFullNoteGivesNoComment() {
        val s = session(notes = melody)
        s.onNoteOn(60, ns(0.0)); s.onNoteOff(60, ns(2.0))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun releasingEarlySaysTheNoteIsTooShort() {
        val s = session(notes = melody)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(60, ns(0.0)))
        s.onNoteOff(60, ns(0.5))
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.TOO_SHORT, f.kind)
        assertEquals(3.0, f.beats, 1e-6)
        assertNull(s.takeTimingFeedback())
    }

    @Test fun holdingIntoTheRestSaysTheNoteIsTooLong() {
        val s = session(notes = melody)
        s.onNoteOn(60, ns(0.0)); s.tick(ns(3.9))
        s.onNoteOff(60, ns(3.9)) // la pausa dura fino a 4,0: ci sono 1,9 s di troppo (3,8 battiti)
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.TOO_LONG, f.kind)
        assertEquals(3.8, f.beats, 1e-6)
    }

    @Test fun noRestAfterMeansNoTooLong() {
        val s = session(notes = arrayOf(n(0.0, 1.0, 60), n(1.0, 1.0, 62)))
        s.onNoteOn(60, ns(0.0)); s.tick(ns(1.6))
        s.onNoteOff(60, ns(1.6))
        assertNull(s.takeTimingFeedback()) // la nota dopo comincia subito: il legato non è un errore
    }

    @Test fun slowSpeedStretchesTheExpectedHold() {
        val s = session(speed = 0.5, notes = melody) // la nota da 2 s dura 4 s reali, il battito 1 s
        s.onNoteOn(60, ns(0.0)); s.onNoteOff(60, ns(2.0))
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.TOO_SHORT, f.kind)
        assertEquals(2.0, f.beats, 1e-6) // mancano 2 s reali = 2 battiti
    }

    @Test fun learnModeAndNonExerciseSessionsAreNotJudged() {
        val off = session(check = false, notes = melody)
        off.onNoteOn(60, ns(0.0)); off.onNoteOff(60, ns(0.2))
        assertNull(off.takeTimingFeedback())
        val wait = PracticeSession(song(*melody)); wait.checkHolds = true // «Impara»
        wait.onNoteOn(60, ns(0.0)); wait.onNoteOff(60, ns(0.2))
        assertNull(wait.takeTimingFeedback())
    }

    @Test fun releasingAWrongKeyOrANeverPressedKeyIsIgnored() {
        val s = session(notes = melody)
        assertEquals(NoteResult.WRONG, s.onNoteOn(70, ns(0.0)))
        s.takeTimingFeedback()
        s.onNoteOff(70, ns(0.1)); s.onNoteOff(61, ns(0.1))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun aKeyHeldAcrossAPauseIsNotJudged() {
        val s = session(notes = melody)
        s.onNoteOn(60, ns(0.0))
        s.pause(ns(0.5))
        s.onNoteOff(60, ns(5.0))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun veryShortNotesAreNotJudgedInASession() {
        val s = session(notes = arrayOf(n(0.0, 0.2, 60), n(2.0, 1.0, 62)))
        s.onNoteOn(60, ns(0.0)); s.onNoteOff(60, ns(0.02))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun aChordReleasedEarlyIsCommentedOnlyOnce() {
        val s = session(notes = arrayOf(n(0.0, 2.0, 48), n(0.0, 2.0, 52), n(0.0, 2.0, 55), n(4.0, 1.0, 60)))
        s.onNoteOn(48, ns(0.0)); s.onNoteOn(52, ns(0.0)); s.onNoteOn(55, ns(0.0))
        s.onNoteOff(48, ns(0.5))
        assertEquals(TimingKind.TOO_SHORT, s.takeTimingFeedback()!!.kind)
        s.onNoteOff(52, ns(0.52)); assertNull(s.takeTimingFeedback())
        s.onNoteOff(55, ns(0.54)); assertNull(s.takeTimingFeedback())
    }

    @Test fun aChordIsJudgedAgainOnTheNextLap() {
        val s = session(notes = arrayOf(n(0.0, 2.0, 48), n(0.0, 2.0, 52), n(4.0, 1.0, 60)))
        s.onNoteOn(48, ns(0.0)); s.onNoteOff(48, ns(0.5)); s.takeTimingFeedback()
        s.restart(ns(1.0)); s.play(ns(1.0))
        s.onNoteOn(48, ns(1.0)); s.onNoteOff(48, ns(1.5))
        assertNotNull(s.takeTimingFeedback()) // dopo una ripartenza il commento torna possibile
    }

    @Test fun aChordNoteReleasedBeforeTheNextOneIsPressedStillCountsAsOneComment() {
        val s = session(notes = arrayOf(n(0.0, 2.0, 48), n(0.0, 2.0, 52), n(4.0, 1.0, 60)))
        s.onNoteOn(48, ns(0.0)); s.onNoteOff(48, ns(0.1))
        assertNotNull(s.takeTimingFeedback())
        s.onNoteOn(52, ns(0.12)); s.onNoteOff(52, ns(0.2))
        assertNull(s.takeTimingFeedback()) // stesso accordo, stesso tentativo
    }

    @Test fun aLoopLapJudgesTheChordAgain() {
        val s = PracticeSession(song(n(0.0, 2.0, 48), n(0.0, 2.0, 52), n(4.0, 1.0, 60)))
        s.countInEnabled = false; s.checkHolds = true
        s.setMode(StudyMode.TEMPO, 0L); s.setLoopBars(1, 1); s.play(0L)
        s.onNoteOn(48, ns(0.0)); s.onNoteOff(48, ns(0.1)); s.takeTimingFeedback()
        s.tick(ns(2.2)) // il giro ricomincia
        s.onNoteOn(48, ns(2.3)); s.onNoteOff(48, ns(2.4))
        assertNotNull(s.takeTimingFeedback())
    }
}
