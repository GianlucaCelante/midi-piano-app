package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimingTest {
    private val spb = 0.5 // mezzo secondo a quarto

    // ---- «A tempo»: tasto giusto ma fuori finestra ----

    @Test fun nearMissBeforeTheNoteIsEarlyAndPointsToTheRestBeforeIt() {
        val f = RestTiming.nearMiss(deviationSec = -0.6, noteSec = 4.0, secPerBeat = spb)!!
        assertEquals(TimingKind.EARLY, f.kind)
        assertEquals(0.6, f.seconds, 1e-9)
        assertEquals(3.4, f.fromSec, 1e-9); assertEquals(4.0, f.toSec, 1e-9) // dal momento in cui si è premuto alla nota
    }

    @Test fun nearMissAfterTheNoteIsLate() {
        val f = RestTiming.nearMiss(deviationSec = 0.9, noteSec = 4.0, secPerBeat = spb)!!
        assertEquals(TimingKind.LATE, f.kind)
        assertEquals(0.9, f.seconds, 1e-9)
        assertTrue(f.fromSec < 4.0 && f.toSec == 4.0) // la pausa che precede la nota
    }

    @Test fun lateHighlightStartsWhereTheRestBegan() {
        val f = RestTiming.nearMiss(deviationSec = 0.9, noteSec = 4.0, secPerBeat = spb, restStartSec = 1.5)!!
        assertEquals(1.5, f.fromSec, 1e-9)
    }

    @Test fun nearMissOfTinyDeviationGivesNothing() {
        assertNull(RestTiming.nearMiss(deviationSec = 0.02, noteSec = 1.0, secPerBeat = spb))
    }

    // ---- tasto in un momento di silenzio ----

    @Test fun pressDuringSilenceTellsHowLongTheRestLasts() {
        val f = RestTiming.inRest(pressSec = 2.0, nextNoteSec = 4.0, secPerBeat = spb)
        assertEquals(TimingKind.IN_REST, f.kind)
        assertEquals(2.0, f.seconds, 1e-9)
        assertEquals(2.0, f.fromSec, 1e-9); assertEquals(4.0, f.toSec, 1e-9)
    }

    // ---- «Impara»: ritmo fra due tasti consecutivi ----

    @Test fun playingTheNextNoteBeforeTheRestHasPassedIsEarly() {
        // fra le due note c'è una pausa: lo spartito vuole 3,0 s, si è aspettato 1,0 s
        val f = RestTiming.waitGap(expectedGapSec = 3.0, actualGapSec = 1.0, hadRest = true, secPerBeat = spb, nextNoteSec = 8.0)!!
        assertEquals(TimingKind.EARLY, f.kind)
        assertEquals(2.0, f.seconds, 1e-9)
        assertEquals(6.0, f.fromSec, 1e-9); assertEquals(8.0, f.toSec, 1e-9)
    }

    @Test fun withoutARestBetweenTheNotesNothingIsJudged() {
        assertNull(RestTiming.waitGap(expectedGapSec = 3.0, actualGapSec = 0.5, hadRest = false, secPerBeat = spb, nextNoteSec = 8.0))
    }

    @Test fun smallEarlinessIsTolerated() {
        // 0,2 s in anticipo: meno di mezzo secondo e meno di un quarto di movimento + soglia minima
        assertNull(RestTiming.waitGap(expectedGapSec = 2.0, actualGapSec = 1.8, hadRest = true, secPerBeat = spb, nextNoteSec = 5.0))
    }

    @Test fun veryLateAfterARestIsFlaggedButOrdinaryHesitationIsNot() {
        assertNull(RestTiming.waitGap(expectedGapSec = 2.0, actualGapSec = 2.8, hadRest = true, secPerBeat = spb, nextNoteSec = 5.0)) // 0,8 s di ritardo: normale
        val f = RestTiming.waitGap(expectedGapSec = 2.0, actualGapSec = 4.4, hadRest = true, secPerBeat = spb, nextNoteSec = 5.0)!!
        assertEquals(TimingKind.LATE, f.kind)
        assertEquals(2.4, f.seconds, 1e-9)
    }

    // ---- messaggi ----

    @Test fun messagesCountInBeatsAndSayHowLongTheRestIs() {
        // a 120 bpm (mezzo secondo a battito): 1,0 s = 2 battiti; la pausa scritta dura 1 battito
        assertEquals("Troppo presto di 2 battiti: la pausa dura 1 battito", RestTiming.nearMiss(-1.0, 4.0, spb, restBeats = 1.0)!!.message)
        assertEquals("In ritardo di mezzo battito dopo la pausa di 2 battiti", RestTiming.nearMiss(0.25, 4.0, spb, restBeats = 2.0)!!.message)
        assertEquals("Qui c'è una pausa di 2 battiti: aspetta ancora 1 battito e mezzo", RestTiming.inRest(2.0, 2.75, spb, restBeats = 2.0).message)
    }

    @Test fun withoutTheRestLengthTheMessageStillMakesSense() {
        assertEquals("Troppo presto di 2 battiti", RestTiming.nearMiss(-1.0, 4.0, spb)!!.message)
        assertEquals("In ritardo di 1 battito dopo la pausa", RestTiming.nearMiss(0.5, 4.0, spb)!!.message)
        assertEquals("Qui c'è una pausa: aspetta ancora 1 battito", RestTiming.inRest(2.0, 2.5, spb).message)
    }

    @Test fun earlinessSmallerThanTheRestSaysSoToo() {
        // mezzo battito prima della nota, con una pausa di 1 battito: ci si è fermati a metà pausa
        assertEquals("Troppo presto di mezzo battito: la pausa dura 1 battito", RestTiming.nearMiss(-0.25, 4.0, spb, restBeats = 1.0)!!.message)
    }

    @Test fun theSameDelayIsDifferentBeatsInDifferentSongs() {
        // 1 s di anticipo: a 120 bpm sono 2 battiti, a 60 bpm uno solo
        assertEquals(2.0, RestTiming.nearMiss(-1.0, 4.0, secPerBeat = 0.5)!!.beats, 1e-9)
        assertEquals(1.0, RestTiming.nearMiss(-1.0, 4.0, secPerBeat = 1.0)!!.beats, 1e-9)
        assertEquals("Troppo presto di 1 battito", RestTiming.nearMiss(-1.0, 4.0, secPerBeat = 1.0)!!.message)
    }

    @Test fun beatsPhraseReadsLikeAMusician() {
        assertEquals("un attimo", RestTiming.beatsPhrase(0.05))
        assertEquals("un quarto di battito", RestTiming.beatsPhrase(0.25))
        assertEquals("mezzo battito", RestTiming.beatsPhrase(0.5))
        assertEquals("tre quarti di battito", RestTiming.beatsPhrase(0.75))
        assertEquals("1 battito", RestTiming.beatsPhrase(1.0))
        assertEquals("1 battito e un quarto", RestTiming.beatsPhrase(1.25))
        assertEquals("1 battito e mezzo", RestTiming.beatsPhrase(1.5))
        assertEquals("1 battito e tre quarti", RestTiming.beatsPhrase(1.75))
        assertEquals("2 battiti", RestTiming.beatsPhrase(2.0))
        assertEquals("3 battiti e mezzo", RestTiming.beatsPhrase(3.5))
        assertEquals("mezzo battito", RestTiming.beatsPhrase(0.55)) // si arrotonda al quarto di battito più vicino
    }

    @Test fun waitGapKnowsBeatsAndRestLength() {
        val f = RestTiming.waitGap(expectedGapSec = 3.0, actualGapSec = 1.0, hadRest = true, secPerBeat = spb, nextNoteSec = 8.0, restBeats = 2.0)!!
        assertEquals(4.0, f.beats, 1e-9) // 2 s a mezzo secondo per battito
        assertEquals("Troppo presto di 4 battiti: la pausa dura 2 battiti", f.message)
    }

    @Test fun overlapDecidesWhichRestsAreHighlighted() {
        val f = RestTiming.nearMiss(-0.6, 4.0, spb)!! // 3,4 → 4,0
        assertTrue(f.covers(restStartSec = 3.0, restEndSec = 3.5)) // una pausa che comincia prima e finisce dentro
        assertTrue(f.covers(3.6, 4.0))
        assertTrue(!f.covers(1.0, 3.4)) // finita prima
        assertTrue(!f.covers(4.0, 4.5)) // comincia dopo la nota
        assertNotNull(f)
    }

    @Test fun shortLabelsAreTerseForTheOnScreenChip() {
        assertEquals("Troppo presto", RestTiming.nearMiss(-1.0, 4.0, spb, restBeats = 1.0)!!.shortLabel)
        assertEquals("Troppo tardi", RestTiming.nearMiss(0.5, 4.0, spb)!!.shortLabel)
        assertEquals("Pausa", RestTiming.inRest(2.0, 2.5, spb).shortLabel)
        assertEquals("Nota troppo corta", HoldTiming.judge(0.5, 2.0, spb, false)!!.shortLabel)
        assertEquals("Nota troppo lunga", HoldTiming.judge(5.0, 2.0, spb, true)!!.shortLabel)
    }
}
