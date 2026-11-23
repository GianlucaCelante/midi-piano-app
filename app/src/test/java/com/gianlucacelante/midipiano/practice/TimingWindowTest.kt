package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** La finestra di tolleranza di «A tempo» cresce con il battito: nei brani lenti o rallentati un anticipo di un quarto di battito è giusto. */
class TimingWindowTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()

    @Test fun fastBeatsKeepTheClassicWindow() {
        assertEquals(0.2, TimingWindow.realToleranceSec(secPerBeat = 0.5, speed = 1.0), 1e-9) // 120 bpm
        assertEquals(0.2, TimingWindow.realToleranceSec(secPerBeat = 0.25, speed = 1.0), 1e-9) // sotto il minimo
    }

    @Test fun slowBeatsGetAQuarterOfABeat() {
        assertEquals(0.25, TimingWindow.realToleranceSec(secPerBeat = 1.0, speed = 1.0), 1e-9) // 60 bpm
        assertEquals(1.0 / 0.75 * 0.25, TimingWindow.realToleranceSec(secPerBeat = 1.0, speed = 0.75), 1e-9) // rallentato: il battito reale dura di più
    }

    @Test fun theWindowHasACeiling() {
        assertEquals(0.4, TimingWindow.realToleranceSec(secPerBeat = 2.0, speed = 0.5), 1e-9)
    }

    @Test fun songTimeToleranceFollowsTheSpeed() {
        assertEquals(TimingWindow.realToleranceSec(1.0, 0.75) * 0.75, TimingWindow.songToleranceSec(1.0, 0.75), 1e-9)
    }

    private fun n(t: Double, key: Int) = SongNote(t, 1.0, key, 80, Hand.RIGHT, beats = t / 1.0 + 1.0)
    private fun slowSong(vararg notes: SongNote) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 1.0)

    private fun tempoSession(speed: Double, vararg notes: SongNote): PracticeSession {
        val s = PracticeSession(slowSong(*notes))
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.setSpeed(speed, 0L)
        s.play(0L)
        return s
    }

    @Test fun aSlightlyEarlyPressInASlowPieceIsAHit() {
        val s = tempoSession(0.75, n(0.0, 60), n(4.0, 62))
        // nota a 4,0 s del brano; a 0,75 il brano scorre a 0,75 s per secondo reale: premuta 0,2 s di brano prima (0,27 s reali)
        val real = (3.8 / 0.75)
        s.tick(ns(real))
        assertEquals(NoteResult.CORRECT, s.onNoteOn(62, ns(real)))
    }

    @Test fun aWayEarlyPressIsStillWrong() {
        val s = tempoSession(0.75, n(0.0, 60), n(4.0, 62))
        val real = (3.0 / 0.75)
        s.tick(ns(real))
        assertEquals(NoteResult.WRONG, s.onNoteOn(62, ns(real)))
    }

    @Test fun anAlmostRightKeyWithoutARestStillGetsATimingHint() {
        // note una dopo l'altra, senza pause: il tasto giusto ma fuori finestra dice comunque «troppo presto»
        val s = tempoSession(1.0, n(0.0, 60), n(1.0, 62), n(2.0, 64))
        s.tick(ns(1.5))
        assertEquals(NoteResult.WRONG, s.onNoteOn(64, ns(1.5))) // 0,5 s prima della nota a 2,0
        val f = s.takeTimingFeedback()
        assertNotNull(f)
        assertEquals(TimingKind.EARLY, f!!.kind)
        assertEquals(false, f.covers(1.0, 2.0)) // nessuna pausa da colorare: non ce ne sono
        assertEquals(false, f.covers(2.0, 3.0))
    }

    @Test fun aPressFarFromAnyNoteHasNoHint() {
        val s = tempoSession(1.0, n(0.0, 60), n(1.0, 62), n(6.0, 64))
        s.tick(ns(3.0))
        assertEquals(NoteResult.WRONG, s.onNoteOn(64, ns(3.0))) // 3 s prima: non è «quasi giusto»
        assertNull(s.takeTimingFeedback()?.takeIf { it.kind != TimingKind.IN_REST })
    }

    // ---- colpi giusti ma non perfetti: si colorano diversamente dopo essere stati premuti ----

    @Test fun perfectWindowIsHalfOfTheTolerance() {
        assertEquals(0.5 * TimingWindow.songToleranceSec(0.5, 1.0), TimingWindow.perfectSongSec(0.5, 1.0), 1e-9)
        assertEquals(0.1, TimingWindow.perfectSongSec(0.5, 1.0), 1e-9) // 120 bpm: ±0,1 s
    }

    @Test fun sessionReportsHowFarFromThePerfectBeatAHitWas() {
        val s = tempoSession(1.0, n(0.0, 60), n(1.0, 62), n(2.0, 64))
        s.tick(ns(0.97)); s.onNoteOn(62, ns(0.97)) // 0,03 s prima: perfetta
        s.tick(ns(1.8)); s.onNoteOn(64, ns(1.8)) // 0,2 s prima: dentro la finestra (0,25 s a 60 bpm) ma oltre il «perfetto» (0,125 s)
        assertEquals(false, s.isOffBeat(62, 1.0))
        assertEquals(true, s.isOffBeat(64, 2.0))
        assertNull(s.isOffBeat(60, 0.0)) // mai suonata: nessun giudizio
    }
}
