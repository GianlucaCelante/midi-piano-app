package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modalità Aspetta con il tasto play: lo spartito scorre e si ferma ad aspettare le note, come in Flowkey. */
class WaitPlaybackTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.5, key, 80, Hand.RIGHT)

    // passi a 0 s, 2 s, 4 s (una nota ciascuno)
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(2.0, 62), n(4.0, 64)))
    private fun sec(s: Double) = (s * 1e9).toLong()

    private fun waitSession() = PracticeSession(song, Hand.RIGHT).also { it.countInEnabled = false } // senza margine iniziale: qui si prova solo il flusso

    @Test
    fun playingInWaitModeStartsTheClockWithoutCountIn() {
        val s = waitSession()
        s.play(sec(0.0))
        assertTrue(s.isPlaying)
        assertEquals(StudyMode.WAIT, s.mode)
        assertNull(s.countInBeat)
    }

    private fun assertNull(v: Any?) = org.junit.Assert.assertNull(v)

    @Test
    fun theCursorStopsAtAStepAndWaitsForTheNote() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        val t = s.tick(sec(3.0)) // tre secondi dopo, senza aver suonato niente: ferma al primo passo (0 s)
        assertEquals(0.0, t.songTimeSec, 1e-6)
        assertEquals(0, s.stepIndex)
        assertEquals(setOf(60), s.expectedKeys)
    }

    @Test
    fun afterThePlayedNoteTheCursorMovesOnAndStopsAtTheNextStep() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.tick(sec(0.5)) // mentre aspetta il cursore resta fermo a 0 s
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(60, sec(0.5)))
        val mid = s.tick(sec(1.5))
        assertEquals(1.0, mid.songTimeSec, 0.01) // 1 s dopo la nota suonata a 0,5: parte dal passo (0 s) e scorre
        val stopped = s.tick(sec(5.0)) // oltre il passo successivo (2 s): si ferma lì ad aspettare
        assertEquals(2.0, stopped.songTimeSec, 1e-6)
        assertEquals(setOf(62), s.expectedKeys)
    }

    @Test
    fun aNoteTooEarlyIsIgnoredNotAnError() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.onNoteOn(60, sec(0.1))
        s.tick(sec(0.2))
        // il passo a 2 s è ancora lontano: suonare il suo tasto adesso non conta e non è un errore
        val before = s.mistakes
        assertEquals(NoteResult.IGNORED, s.onNoteOn(62, sec(0.3)))
        assertEquals(before, s.mistakes)
        assertEquals(1, s.stepIndex)
    }

    @Test
    fun aNoteJustBeforeTheCursorArrivesCompletesTheStepAndKeepsTheFlow() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.onNoteOn(60, sec(0.1))
        s.tick(sec(1.7))
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(62, sec(1.7))) // 0,3 s prima dell'arrivo a 2 s
        val t = s.tick(sec(2.2))
        assertTrue("il cursore non si ferma sul passo già suonato: $t", t.songTimeSec > 2.0)
    }

    @Test
    fun pausingFreezesAndPlayingAgainResumesFromThePlace() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.onNoteOn(60, sec(0.1))
        s.tick(sec(1.0))
        s.pause(sec(1.0))
        assertFalse(s.isPlaying)
        assertEquals(1.0, s.currentTimeSec, 0.01) // in pausa il cursore resta esattamente dove era arrivato
        s.play(sec(5.0))
        assertTrue(s.isPlaying)
        assertEquals(setOf(62), s.expectedKeys.ifEmpty { setOf(62) })
    }

    @Test
    fun switchingModeKeepsThePosition() {
        val s = waitSession()
        s.onNoteOn(60, 0L) // passo 1
        assertEquals(2.0, s.currentTimeSec, 1e-6)
        s.setMode(StudyMode.TEMPO, sec(0.0))
        assertEquals(2.0, s.currentTimeSec, 0.05)
        s.setMode(StudyMode.WAIT, sec(0.0))
        assertEquals(2.0, s.currentTimeSec, 1e-6)
        assertEquals(1, s.stepIndex)
    }

    @Test
    fun seekingByTimeInWaitModeWorksWhileTheClockRuns() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.seekTo(4.0, sec(0.5))
        assertEquals(2, s.stepIndex)
        val t = s.tick(sec(3.0))
        assertEquals(4.0, t.songTimeSec, 1e-6) // si ferma sul passo scelto e aspetta
    }

    @Test
    fun playedAllTheStepsFinishesAndStopsTheClock() {
        val s = waitSession()
        s.play(sec(0.0))
        s.tick(sec(0.0))
        s.onNoteOn(60, sec(0.1))
        s.tick(sec(1.8)); s.onNoteOn(62, sec(1.8))
        s.tick(sec(3.8)); assertEquals(NoteResult.FINISHED, s.onNoteOn(64, sec(3.8)))
        val t = s.tick(sec(4.0))
        assertTrue(t.finished)
        assertFalse(s.isPlaying)
    }
}
