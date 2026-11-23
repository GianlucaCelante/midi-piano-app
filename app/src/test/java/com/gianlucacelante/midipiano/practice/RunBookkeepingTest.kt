package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import com.gianlucacelante.midipiano.song.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/** Conti per giro: errori per battuta, passi suonati, gesti di più tasti sbagliati. */
class RunBookkeepingTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, key: Int) = SongNote(t, 0.4, key, 80, Hand.RIGHT, beats = 0.8)
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(1.0, 62), n(2.0, 64), n(3.0, 65), n(4.0, 67), n(5.0, 69)),
        barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)

    @Test fun restartClearsTheErrorsPerBarOfThePreviousRun() {
        val s = PracticeSession(song)
        s.onNoteOn(99, ns(0.0)) // sbagliato
        assertEquals(1, s.barReport().sumOf { it.errors })
        s.restart(ns(1.0))
        assertEquals(0, s.barReport().sumOf { it.errors }) // il giro nuovo parte pulito
    }

    @Test fun stepsPlayedCountsOnlyThisRunNotTheResumePoint() {
        val s = PracticeSession(song)
        s.restart(ns(0.0)); s.seekTo(3.0, ns(0.0))
        assertEquals(0, s.stepsPlayedThisRun) // si riparte dal passo 3 ma non si è suonato niente
        s.onNoteOn(65, ns(1.0))
        assertEquals(1, s.stepsPlayedThisRun)
        s.restart(ns(2.0))
        assertEquals(0, s.stepsPlayedThisRun)
    }

    private val chordSteps = listOf(Step(2.0, listOf(n(2.0, 50), n(2.0, 55))))

    @Test fun tempoGestureWindowIsAnchoredOnItsFirstKey() {
        val e = TempoModeEngine(chordSteps) // tolleranza 0,2 -> finestra 0,15
        e.onNoteOn(52, 2.00); e.onNoteOn(53, 2.10); e.onNoteOn(54, 2.20); e.onNoteOn(56, 2.30)
        assertEquals(2, e.wrong) // 2,00 apre un gesto (con 2,10), 2,20 ne apre un altro (con 2,30): una sequenza lunga non è un solo errore
    }

    @Test fun waitGestureWindowIsAnchoredOnItsFirstKey() {
        val w = WaitModeEngine(chordSteps)
        val ms = 1_000_000L
        for (t in listOf(0L, 100L, 200L, 300L)) w.onNoteOn(99, t * ms)
        assertEquals(2, w.mistakes)
    }
}
