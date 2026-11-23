package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Durata delle note: quanto a lungo si tiene premuto un tasto rispetto a quanto scritto (logica pura). */
class HoldTimingTest {
    private val spb = 0.5 // 120 bpm

    @Test fun fullLengthIsFine() {
        assertNull(HoldTiming.judge(heldSec = 2.0, expectedSec = 2.0, secPerBeat = spb, restFollows = true))
        assertNull(HoldTiming.judge(heldSec = 1.7, expectedSec = 2.0, secPerBeat = spb, restFollows = true)) // un po' corta ma accettabile
    }

    @Test fun clearlyTooShortIsFlaggedInBeats() {
        val f = HoldTiming.judge(heldSec = 0.5, expectedSec = 2.0, secPerBeat = spb, restFollows = false)
        assertNotNull(f)
        assertEquals(TimingKind.TOO_SHORT, f!!.kind)
        assertEquals(3.0, f.beats, 1e-9) // mancano 1,5 s = 3 battiti
        assertEquals("Nota troppo corta: tienila premuta ancora 3 battiti", f.message)
    }

    @Test fun tooLongOnlyCountsWhenARestFollows() {
        val f = HoldTiming.judge(heldSec = 5.0, expectedSec = 2.0, secPerBeat = spb, restFollows = true)!!
        assertEquals(TimingKind.TOO_LONG, f.kind)
        assertEquals(6.0, f.beats, 1e-9)
        assertEquals("Nota troppo lunga: rilasciala 6 battiti prima", f.message)
        assertNull(HoldTiming.judge(heldSec = 5.0, expectedSec = 2.0, secPerBeat = spb, restFollows = false)) // la nota dopo la «copre»: legato normale
    }

    @Test fun smallExcessIsTolerated() {
        assertNull(HoldTiming.judge(heldSec = 2.6, expectedSec = 2.0, secPerBeat = spb, restFollows = true))
        assertNull(HoldTiming.judge(heldSec = 0.9, expectedSec = 0.5, secPerBeat = spb, restFollows = true)) // oltre 1,6x ma meno di un battito in più
    }

    @Test fun veryShortNotesAreNotJudged() {
        // sotto mezzo battito (staccato, crome veloci) la durata non si controlla
        assertNull(HoldTiming.judge(heldSec = 0.02, expectedSec = 0.2, secPerBeat = spb, restFollows = true))
    }

    @Test fun shortfallBelowHalfABeatIsTolerated() {
        // 1 battito scritto, tenuto mezzo secondo scarso: 0,3 battiti in meno non si commentano
        assertNull(HoldTiming.judge(heldSec = 0.35, expectedSec = 0.5, secPerBeat = spb, restFollows = false))
        assertNotNull(HoldTiming.judge(heldSec = 0.1, expectedSec = 1.0, secPerBeat = spb, restFollows = false)) // qui mancano 0,9 s = 1,8 battiti
    }

    @Test fun holdMessagesDoNotHighlightRestsExceptTheOneAfterTooLong() {
        val s = HoldTiming.judge(0.5, 2.0, spb, false, noteStartSec = 0.0, noteEndSec = 2.0, restEndSec = 4.0)!!
        assertEquals(false, s.covers(2.0, 4.0)) // «troppo corta»: nessuna pausa da evidenziare
        val l = HoldTiming.judge(5.0, 2.0, spb, true, noteStartSec = 0.0, noteEndSec = 2.0, restEndSec = 4.0)!!
        assertEquals(true, l.covers(2.0, 4.0)) // «troppo lunga»: si evidenzia la pausa in cui si è invaso
    }
}
