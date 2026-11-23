package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.notation.Accidental
import com.gianlucacelante.midipiano.notation.Chords
import com.gianlucacelante.midipiano.notation.NotationLayout
import com.gianlucacelante.midipiano.notation.Rests
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import com.gianlucacelante.midipiano.song.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test dei difetti trovati dalla review indipendente. */
class ReviewFixesTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, d: Double, key: Int, hand: Hand = Hand.RIGHT, beats: Double = d * 2) = SongNote(t, d, key, 80, hand, beats)

    private fun song(vararg notes: SongNote, bars: List<Double> = emptyList(), num: Int = 4, den: Int = 4) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = bars, timeNumerator = num, timeDenominator = den, secPerBeat = 0.5)

    // ---- suonare con il brano fermo non conta ----

    @Test
    fun tempoModeIgnoresNotesWhileNotPlaying() {
        val s = PracticeSession(song(n(0.0, 0.5, 60), n(1.0, 0.5, 62)))
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        assertEquals(NoteResult.IGNORED, s.onNoteOn(99, ns(0.0))) // non ancora partito
        assertEquals(NoteResult.IGNORED, s.onNoteOn(60, ns(0.0))) // nemmeno una nota giusta
        assertEquals(0, s.mistakes)
        s.play(0L)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(60, ns(0.0)))
        s.pause(ns(0.5))
        assertEquals(NoteResult.IGNORED, s.onNoteOn(99, ns(0.6))) // in pausa
    }

    // ---- cambio di mano a metà brano ----

    @Test
    fun changingHandMidSongDoesNotTurnPastNotesIntoMisses() {
        val s = PracticeSession(song(n(0.0, 0.5, 72), n(0.5, 0.5, 74), n(10.0, 0.5, 76), n(0.0, 0.5, 48, Hand.LEFT), n(10.0, 0.5, 50, Hand.LEFT)))
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        s.tick(ns(8.0))
        s.setHand(Hand.LEFT)
        s.tick(ns(8.1))
        assertEquals(0, s.misses) // le note già passate non contano
        assertEquals(0, s.mistakes)
    }

    // ---- loop che parte dopo la prima battuta ----

    @Test
    fun loopFromALaterBarCountsInBeforeThatBarAndIgnoresKeysThere() {
        // battute da 2 s (4/4, 0,5 s a quarto); loop sulla battuta 2 (2.0..4.0)
        val s = PracticeSession(song(n(0.0, 0.5, 60), n(2.0, 0.5, 62), n(3.0, 0.5, 64), n(5.0, 0.5, 65), bars = listOf(2.0, 4.0)))
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(2, 2)
        s.countInEnabled = true
        s.play(0L) // conto alla rovescia: 2 battute (4 s) prima del loop, dal tempo -2.0 a 2.0
        assertTrue(s.isCountingIn)
        assertEquals(1, s.countInBeat)
        assertEquals(NoteResult.IGNORED, s.onNoteOn(60, ns(0.5))) // tasti nel conto alla rovescia: ignorati
        assertEquals(0, s.mistakes)
        s.tick(ns(3.5))
        assertEquals(4, s.countInBeat) // ultimo battito della seconda battuta (0,5 s a quarto)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(62, ns(4.0))) // la prima nota del loop, a 2.0
        assertFalse(s.isCountingIn)
    }

    @Test
    fun countInNumberDisappearsBeforeTheFirstNoteArrives() {
        val s = PracticeSession(song(n(0.0, 0.5, 60), n(2.0, 0.5, 62), n(3.0, 0.5, 64), n(5.0, 0.5, 65), bars = listOf(2.0, 4.0)))
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(2, 2)
        s.countInEnabled = true
        s.play(0L) // conto da -2.0 a 2.0, un movimento = 0,5 s
        s.tick(ns(3.6)) // tempo 1.6: ultimo movimento, ancora prima di mezzo movimento dalla nota
        assertEquals(4, s.countInBeat)
        s.tick(ns(3.8)) // tempo 1.8: manca meno di mezzo movimento alla nota
        assertTrue(s.isCountingIn)
        assertNull(s.countInBeat)
    }

    @Test
    fun startingALoopDoesNotJudgeTheNotesBeforeIt() {
        val s = PracticeSession(song(n(0.0, 0.5, 60), n(2.0, 0.5, 62), n(5.0, 0.5, 65), bars = listOf(2.0, 4.0)))
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(2, 2)
        s.countInEnabled = true
        s.play(0L)
        s.tick(ns(1.9)) // ancora nel conto alla rovescia: la nota a 0.0 non deve risultare mancata
        assertEquals(0, s.misses)
    }

    // ---- accordi non quantizzati ----

    @Test
    fun everyNoteOfAChordHasItsOwnStateEvenIfItStartsAFewMillisecondsLater() {
        val step = Step(1.0, listOf(SongNote(1.0, 0.5, 60, 80, Hand.RIGHT), SongNote(1.02, 0.5, 64, 80, Hand.RIGHT)))
        val e = TempoModeEngine(listOf(step), 0.2)
        assertNotNull(e.stateAt(60, 1.0))
        assertNotNull(e.stateAt(64, 1.02)) // prima restituiva null
        e.onNoteOn(64, 1.03)
        assertEquals(HitState.HIT, e.stateAt(64, 1.02))
    }

    // ---- fine brano con una nota finale breve ----

    @Test
    fun aVeryShortLastNoteCanStillBePlayedLate() {
        val s = PracticeSession(song(n(0.0, 0.5, 60), n(2.0, 0.05, 62)))
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        assertEquals(NoteResult.CORRECT, s.onNoteOn(62, ns(2.15))) // oltre la fine della nota (2.05) ma dentro la tolleranza
    }

    // ---- pause con voci a durata diversa ----

    @Test
    fun noRestIsDrawnWhileALongerNoteOfTheSameChordStillSounds() {
        // stesso istante nel rigo destro: minima da 4 quarti (si estende fino a 2.0) e una croma; poi una nota a 2.0
        val s = song(n(0.0, 2.0, 60, beats = 4.0), n(0.0, 0.5, 64, beats = 1.0), n(2.0, 0.5, 67, beats = 1.0))
        val chords = Chords.group(NotationLayout.layout(s, Hand.BOTH))
        assertTrue(Rests.compute(chords, s).isEmpty())
    }

    // ---- bequadri nella battuta ----

    @Test
    fun anAccidentalLastsUntilTheEndOfTheBar() {
        val s = song(n(0.0, 0.5, 66, beats = 1.0), n(0.5, 0.5, 66, beats = 1.0), n(1.0, 0.5, 65, beats = 1.0), n(2.0, 0.5, 65, beats = 1.0), bars = listOf(2.0))
        val l = NotationLayout.layout(s, Hand.BOTH)
        assertEquals(Accidental.SHARP, l[0].accidental) // Fa# la prima volta
        assertNull(l[1].accidental) // ripetuto nella stessa battuta: nessuna alterazione
        assertEquals(Accidental.NATURAL, l[2].accidental) // Fa naturale dopo un Fa#: serve il bequadro
        assertNull(l[3].accidental) // nuova battuta: l'alterazione scade e il Fa è naturale per armatura
    }

    @Test
    fun accidentalsAreTrackedPerStaff() {
        val s = song(n(0.0, 0.5, 66, Hand.RIGHT, beats = 1.0), n(0.5, 0.5, 54, Hand.LEFT, beats = 1.0))
        val l = NotationLayout.layout(s, Hand.BOTH)
        assertEquals(Accidental.SHARP, l[0].accidental)
        assertEquals(Accidental.SHARP, l[1].accidental) // Fa#3 in basso: un'altra altezza e un altro rigo
    }

    // ---- movimenti in tempi composti ----

    @Test
    fun compoundMetersCountDottedQuarters() {
        assertEquals(2, song(n(0.0, 0.5, 60), num = 6, den = 8).beatsPerBar)
        assertEquals(3, song(n(0.0, 0.5, 60), num = 9, den = 8).beatsPerBar)
        assertEquals(4, song(n(0.0, 0.5, 60), num = 12, den = 8).beatsPerBar)
        assertEquals(4, song(n(0.0, 0.5, 60), num = 4, den = 4).beatsPerBar)
        assertEquals(3, song(n(0.0, 0.5, 60), num = 3, den = 4).beatsPerBar)
        assertEquals(5, song(n(0.0, 0.5, 60), num = 5, den = 8).beatsPerBar)
    }
}
