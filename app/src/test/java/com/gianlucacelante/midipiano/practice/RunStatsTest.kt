package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tutto ciò che si può misurare di un giro, per le statistiche. */
class RunStatsTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, d: Double, key: Int) = SongNote(t, d, key, 80, Hand.RIGHT, beats = d * 2)
    private fun song(vararg notes: SongNote) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)

    private fun session(vararg notes: SongNote): PracticeSession {
        val s = PracticeSession(song(*notes)); s.countInEnabled = false
        s.checkHolds = true
        s.setMode(StudyMode.TEMPO, 0L); s.play(0L)
        return s
    }

    private fun press(s: PracticeSession, key: Int, t: Double) { s.tick(ns(t)); s.onNoteOn(key, ns(t)) }

    @Test fun earlyAndLateHitsAreCountedAgainstThePerfectWindow() {
        val s = session(n(0.0, 0.4, 60), n(1.0, 0.4, 62), n(2.0, 0.4, 64), n(3.0, 0.4, 65))
        press(s, 60, 0.02)  // perfetta (|0,02| < 0,1)
        press(s, 62, 0.85)  // 0,15 prima: in anticipo, non perfetta
        press(s, 64, 2.16)  // 0,16 dopo: in ritardo, non perfetta
        val st = s.runStats()
        assertEquals(3, st.noteHits); assertEquals(1, st.early); assertEquals(1, st.late); assertEquals(2, st.offBeat)
        // scarto medio: (+0,02 -0,15 +0,16)/3 = +0,0100 s -> +10 ms
        assertEquals(10, st.meanDevMs)
        assertEquals(110, st.meanAbsDevMs) // (0,02+0,15+0,16)/3
    }

    @Test fun wrongKeysAndMissedKeysAreTallied() {
        val s = session(n(0.0, 0.4, 60), n(1.0, 0.4, 62), n(2.0, 0.4, 64), n(8.0, 0.4, 66))
        press(s, 60, 0.0)
        press(s, 99, 0.5)   // tasto sbagliato
        press(s, 99, 3.0)   // di nuovo lo stesso
        press(s, 98, 3.1)
        s.tick(ns(5.0))     // 62 e 64 mai suonate
        val st = s.runStats()
        assertEquals(mapOf(99 to 2, 98 to 1), st.wrongKeys)
        assertEquals(mapOf(62 to 1, 64 to 1), st.missedKeys)
    }

    @Test fun restAndHoldCommentsAreCounted() {
        val s = session(n(0.0, 2.0, 60), n(4.0, 1.0, 62), n(9.0, 1.0, 64))
        press(s, 60, 0.0)
        s.onNoteOff(60, ns(0.4))             // nota di 2 s lasciata dopo 0,4: troppo corta
        press(s, 62, 3.0)                    // un secondo prima della nota dopo una pausa: troppo presto
        s.takeTimingFeedback()
        press(s, 70, 6.0)                    // un tasto qualsiasi in piena pausa
        val st = s.runStats()
        assertEquals(1, st.holdShort); assertEquals(1, st.restEarly); assertEquals(1, st.restInRest)
    }

    @Test fun barErrorsComeFromTheBarReport() {
        val s = session(n(0.0, 0.4, 60), n(1.0, 0.4, 62))
        press(s, 60, 0.0); press(s, 99, 0.5)
        assertEquals(1, s.runStats().barErrors.values.sum())
    }

    @Test fun restartStartsFromZero() {
        val s = session(n(0.0, 0.4, 60), n(1.0, 0.4, 62))
        press(s, 99, 0.5); press(s, 60, 0.0)
        s.restart(ns(2.0))
        val st = s.runStats()
        assertEquals(0, st.noteHits); assertEquals(emptyMap<Int, Int>(), st.wrongKeys); assertEquals(0, st.holdShort)
    }

    @Test fun waitModeCountsWrongKeys() {
        val s = PracticeSession(song(n(0.0, 0.4, 60), n(1.0, 0.4, 62)))
        s.onNoteOn(70, ns(0.0)); s.onNoteOn(71, ns(1.0))
        assertEquals(mapOf(70 to 1, 71 to 1), s.runStats().wrongKeys)
    }
}
