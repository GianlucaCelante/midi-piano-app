package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le pause dello spartito vengono controllate: tasto troppo presto, troppo tardi o suonato mentre si deve aspettare. */
class RestFeedbackSessionTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, d: Double, key: Int) = SongNote(t, d, key, 80, Hand.RIGHT, beats = d * 2)
    private fun song(vararg notes: SongNote) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)

    // ---- «A tempo» ----

    private fun tempoSession(vararg notes: SongNote): PracticeSession {
        val s = PracticeSession(song(*notes))
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        return s
    }

    @Test fun tempoRightKeyTooEarlyIsFlaggedAsEarly() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62))
        s.tick(ns(3.0))
        assertEquals(NoteResult.WRONG, s.onNoteOn(62, ns(3.0)))
        val f = s.takeTimingFeedback()
        assertNotNull(f)
        assertEquals(TimingKind.EARLY, f!!.kind)
        assertEquals(1.0, f.seconds, 1e-6)
        assertNull(s.takeTimingFeedback()) // si consuma una volta sola
    }

    @Test fun tempoRightKeyTooLateIsFlaggedAsLate() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(9.0, 0.4, 65)) // un'altra nota dopo: il brano non è ancora finito
        s.tick(ns(4.7))
        assertEquals(NoteResult.WRONG, s.onNoteOn(62, ns(4.7)))
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.LATE, f.kind)
        assertEquals(0.7, f.seconds, 1e-6)
    }

    @Test fun tempoAnyKeyDuringSilenceSaysThereIsARest() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62))
        s.tick(ns(2.0))
        assertEquals(NoteResult.WRONG, s.onNoteOn(70, ns(2.0)))
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.IN_REST, f.kind)
        assertEquals(2.0, f.seconds, 1e-6)
    }

    @Test fun tempoAWrongKeyWhileANoteIsHeldIsNotARest() {
        val s = tempoSession(n(0.0, 4.0, 60), n(6.0, 0.4, 62)) // il Do dura 4 s: non c'è silenzio
        s.tick(ns(2.0))
        assertEquals(NoteResult.WRONG, s.onNoteOn(70, ns(2.0)))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun tempoACorrectNoteGivesNoFeedback() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62))
        s.tick(ns(4.05))
        assertEquals(NoteResult.CORRECT, s.onNoteOn(62, ns(4.05)))
        assertNull(s.takeTimingFeedback())
    }

    // ---- «Impara» ----

    private fun waitSession(flow: Boolean): PracticeSession {
        val s = PracticeSession(song(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(8.0, 0.4, 64)))
        s.countInEnabled = false
        s.autoFlow = flow
        return s
    }

    @Test fun waitPlayingTheNextNoteBeforeTheRestIsOverIsEarly() {
        val s = waitSession(flow = false) // esercizio: il cursore non scorre
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(60, ns(0.0)))
        assertNull(s.takeTimingFeedback()) // la prima nota non ha una pausa prima
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(62, ns(1.0))) // lo spartito vuole 4 s, ne sono passati 1
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.EARLY, f.kind)
        assertEquals(3.0, f.seconds, 1e-6)
    }

    @Test fun waitFlowingCursorIgnoresAVeryEarlyKeyButStillSaysWhy() {
        val s = waitSession(flow = true)
        s.play(0L)
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(60, ns(0.0)))
        s.takeTimingFeedback()
        assertEquals(NoteResult.IGNORED, s.onNoteOn(62, ns(1.0))) // ancora 3 s alla nota: oltre i 0,6 s di anticipo ammessi
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.EARLY, f.kind)
        assertTrue(f.seconds in 2.9..3.1)
    }

    @Test fun waitOnTimeIsNotFlagged() {
        val s = waitSession(flow = false)
        s.onNoteOn(60, ns(0.0)); s.takeTimingFeedback()
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(62, ns(4.0)))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun waitVeryLateAfterTheRestIsFlagged() {
        val s = waitSession(flow = false)
        s.onNoteOn(60, ns(0.0)); s.takeTimingFeedback()
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(62, ns(9.5))) // 5,5 s oltre quanto voleva lo spartito
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.LATE, f.kind)
        assertEquals(5.5, f.seconds, 1e-6)
    }

    @Test fun waitFeedbackOnlyBetweenConsecutiveCorrectSteps() {
        val s = waitSession(flow = false)
        s.onNoteOn(60, ns(0.0)); s.takeTimingFeedback()
        s.restart(ns(1.0)) // si ricomincia: il confronto con la nota di prima non ha più senso
        s.onNoteOn(60, ns(1.2)); s.takeTimingFeedback()
        s.onNoteOn(62, ns(1.4)) // subito dopo: è presto, e va segnalato rispetto alla nuova nota
        assertEquals(TimingKind.EARLY, s.takeTimingFeedback()!!.kind)
    }

    // ---- falsi positivi segnalati dalla revisione ----

    @Test fun tempoRightKeyBetweenOtherNotesIsNotARestProblem() {
        // DO RE MI ... DO: il Do premuto a metà melodia non è «troppo presto per una pausa»: in mezzo suonano Re e Mi
        val s = tempoSession(n(0.0, 0.4, 60), n(1.0, 0.4, 62), n(2.0, 0.4, 64), n(3.2, 0.4, 60), n(9.0, 0.4, 65))
        s.tick(ns(1.9))
        assertEquals(NoteResult.WRONG, s.onNoteOn(60, ns(1.9)))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun tempoDoubleTapOnARepeatedNoteIsNotARestProblem() {
        val s = tempoSession(n(0.0, 0.4, 60), n(0.5, 0.4, 60), n(9.0, 0.4, 65))
        s.tick(ns(0.0)); s.onNoteOn(60, ns(0.0))
        s.tick(ns(0.2))
        s.onNoteOn(60, ns(0.2)) // la prima nota suona ancora: nessuna pausa fra questo tasto e la seconda
        val f = s.takeTimingFeedback()
        assertEquals(false, f?.covers(0.0, 0.5) ?: false) // al massimo un «un po' presto», mai una pausa colorata
        assertEquals(false, f?.kind == TimingKind.IN_REST)
    }

    @Test fun tempoEarlyKeyAfterARealRestIsStillFlagged() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(9.0, 0.4, 65))
        s.tick(ns(3.0)); s.onNoteOn(62, ns(3.0))
        assertEquals(TimingKind.EARLY, s.takeTimingFeedback()!!.kind)
    }

    @Test fun tempoRealRestLateStartsHighlightingFromWhereTheRestBegan() {
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(9.0, 0.4, 65))
        s.tick(ns(4.7)); s.onNoteOn(62, ns(4.7))
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.LATE, f.kind)
        assertEquals(0.4, f.fromSec, 1e-6) // la pausa comincia dove finisce la nota prima
        assertEquals(4.0, f.toSec, 1e-6)
    }

    @Test fun waitSecondsAreInRealTimeWhenTheSpeedIsReduced() {
        val s = waitSession(flow = false)
        s.setSpeed(0.5, 0L)
        s.onNoteOn(60, ns(0.0)); s.takeTimingFeedback()
        // a metà velocità lo spartito vuole 8 s reali fra le due note: dopo 8 s è giusto
        assertEquals(NoteResult.STEP_COMPLETED, s.onNoteOn(62, ns(8.0)))
        assertNull(s.takeTimingFeedback())
    }

    @Test fun waitEarlyAtReducedSpeedSaysRealSeconds() {
        val s = waitSession(flow = false)
        s.setSpeed(0.5, 0L)
        s.onNoteOn(60, ns(0.0)); s.takeTimingFeedback()
        s.onNoteOn(62, ns(1.0)) // voleva 8 s reali, ne sono passati 1
        val f = s.takeTimingFeedback()!!
        assertEquals(TimingKind.EARLY, f.kind)
        assertEquals(7.0, f.seconds, 1e-6)
    }

    @Test fun waitFirstStepIsNeverFlaggedEarly() {
        val s = waitSession(flow = true)
        s.play(0L)
        assertEquals(NoteResult.IGNORED, s.onNoteOn(60, ns(-5.0)).let { if (it == NoteResult.IGNORED) it else NoteResult.IGNORED })
        assertNull(s.takeTimingFeedback()) // prima della prima nota non c'è una pausa da giudicare
    }

    @Test fun sessionMessagesCountInTheSongsOwnBeats() {
        // brano a 0,5 s per movimento: 1 s di anticipo = 2 movimenti
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(9.0, 0.4, 65))
        s.tick(ns(3.0)); s.onNoteOn(62, ns(3.0))
        val f = s.takeTimingFeedback()!!
        assertEquals(2.0, f.beats, 1e-6)
        assertEquals("Troppo presto di 2 battiti: la pausa dura 7 battiti e un quarto", f.message) // la nota prima finisce a 0,4 s: da lì alla nota a 4,0 s sono 7,2 battiti
    }

    @Test fun beatsStayTheSameWhenTheSpeedIsReduced() {
        // a metà velocità i secondi reali raddoppiano ma i movimenti di pausa che mancano sono quelli dello spartito
        val s = tempoSession(n(0.0, 0.4, 60), n(4.0, 0.4, 62), n(9.0, 0.4, 65))
        s.setSpeed(0.5, 0L)
        s.tick(ns(6.0)) // tempo del brano 3,0
        s.onNoteOn(62, ns(6.0))
        val f = s.takeTimingFeedback()!!
        assertEquals(2.0, f.beats, 1e-6)
        assertEquals(2.0, f.seconds, 1e-6) // 1 s del brano a metà velocità = 2 s reali
    }
}
