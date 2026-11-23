package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import com.gianlucacelante.midipiano.song.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/** Un accordo (tutte le note di un passo) conta come un solo colpo o un solo errore. */
class ChordUnitScoringTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.4, key, 80, Hand.RIGHT, beats = 0.8)
    // passo 0: nota; passo 1: accordo di 3 note; passo 2: nota
    private val steps = listOf(
        Step(0.0, listOf(n(0.0, 60))),
        Step(1.0, listOf(n(1.0, 48), n(1.0, 52), n(1.0, 55))),
        Step(2.0, listOf(n(2.0, 64))),
    )

    @Test fun aCompletedChordIsOneHit() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(48, 1.0); e.onNoteOn(52, 1.01); e.onNoteOn(55, 1.02)
        assertEquals(1, e.hits)
        assertEquals(3, e.noteHits) // le singole note restano per la media degli scarti
        assertEquals(0, e.misses)
    }

    @Test fun aWholeMissedChordIsOneError() {
        val e = TempoModeEngine(steps)
        assertEquals(2, e.onTick(5.0, onlyBefore = 1.5)) // la nota e l'accordo scadono: 2 passi mancati, non 4 note
        assertEquals(2, e.misses)
    }

    @Test fun aChordWithSomeNotesMissingIsOneErrorNotAHit() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(60, 0.0)
        e.onNoteOn(48, 1.0); e.onNoteOn(52, 1.01) // manca il sol
        e.onTick(3.0)
        assertEquals(1, e.hits) // solo la nota singola
        assertEquals(2, e.misses) // accordo incompleto + passo 2 non suonato
    }

    @Test fun accuracyIsPerStep() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(60, 0.0); e.onNoteOn(64, 2.0) // due note giuste, accordo mancato
        e.onTick(5.0)
        assertEquals(2, e.hits); assertEquals(1, e.misses)
        assertEquals(2.0 / 3.0, e.accuracy, 1e-9) // non 2/5: l'accordo pesa quanto una nota
    }

    @Test fun skippedChordsAreNotCounted() {
        val e = TempoModeEngine(steps)
        e.skipBefore(1.5)
        e.onTick(5.0)
        assertEquals(0, e.hits); assertEquals(1, e.misses) // solo l'ultima nota, mai suonata
    }

    @Test fun lapResetStartsTheChordFromScratch() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(48, 1.0); e.onNoteOn(52, 1.0); e.onNoteOn(55, 1.0)
        assertEquals(1, e.hits)
        e.resetRange(0.5, 1.5)
        assertEquals(0, e.hits); assertEquals(0, e.noteHits)
        e.onTick(1.6, onlyBefore = 1.5)
        assertEquals(2, e.misses) // la nota iniziale mai suonata e l'accordo del giro nuovo: un errore ciascuno
    }

    @Test fun lapCountsAreInStepUnits() {
        val e = TempoModeEngine(steps)
        e.onNoteOn(60, 0.0); e.onNoteOn(48, 1.0) // accordo incompleto
        e.onTick(5.0)
        assertEquals(1 to 2, e.lapCounts(0.0, 3.0)) // 1 colpo (nota singola), 2 errori (accordo incompleto, nota finale)
    }

    @Test fun barReportCountsOneErrorPerChord() {
        val song = Song("s", "t", null, SongSource("t", "t"), steps.flatMap { it.notes }, barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)
        val s = PracticeSession(song); s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L); s.play(0L)
        s.tick(4_000_000_000L) // nulla suonato: 3 passi mancati
        assertEquals(3, s.barReport().sumOf { it.errors }) // non 5
        assertEquals(3, s.barReport().sumOf { it.notes })
        assertEquals(3, s.mistakes)
    }
}
