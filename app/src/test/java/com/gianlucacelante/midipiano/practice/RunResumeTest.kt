package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tornando indietro in un esercizio il giro ricomincia dal punto in cui si lascia il cursore, non dall'inizio. */
class RunResumeTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, key: Int) = SongNote(t, 0.4, key, 80, Hand.RIGHT, beats = 0.8)
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(1.0, 62), n(2.0, 64), n(3.0, 65), n(4.0, 67)),
        barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)

    @Test fun waitModeResumesAtTheStepFromTheReleasePoint() {
        val s = PracticeSession(song)
        s.onNoteOn(60, ns(0.0)); s.onNoteOn(62, ns(1.0)); s.onNoteOn(64, ns(2.0)) // si arriva al quarto passo
        s.restart(ns(3.0))
        s.seekTo(3.0, ns(3.0))
        assertEquals(3, s.stepIndex) // riparte dalla nota a 3,0 s, non dalla prima
        assertEquals(0, s.mistakes)
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(65, ns(4.0)))
    }

    @Test fun tempoModeDoesNotJudgeTheNotesBeforeTheResumePoint() {
        val s = PracticeSession(song)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.restart(0L)
        s.seekTo(3.0, 0L)
        s.play(0L)
        assertEquals(2, s.tick(ns(5.0)).newMisses) // solo le note a 3,0 e 4,0 risultano mancate: quelle prima del punto di ripartenza non contano
    }
}
