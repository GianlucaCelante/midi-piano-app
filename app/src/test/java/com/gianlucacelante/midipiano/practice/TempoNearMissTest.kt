package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TempoNearMissTest {
    private fun note(key: Int, t: Double) = SongNote(t, 0.4, key, 80, Hand.RIGHT, beats = 1.0)
    private fun engine(vararg pairs: Pair<Int, Double>) =
        TempoModeEngine(pairs.sortedBy { it.second }.map { (k, t) -> Step(t, listOf(note(k, t))) }, 0.2)

    @Test fun rightKeyTooEarlyIsWrongButRemembersHowEarly() {
        val e = engine(60 to 0.0, 62 to 4.0)
        val o = e.onNoteOn(62, 3.0)
        assertEquals(HitKind.WRONG, o.kind)
        assertEquals(-1.0, o.nearMissSec!!, 1e-9)
        assertEquals(4.0, o.nearNoteSec!!, 1e-9)
    }

    @Test fun rightKeyTooLateIsWrongAndSaysHowLate() {
        val e = engine(60 to 0.0, 62 to 4.0)
        e.onTick(4.6) // la finestra della nota è scaduta: è mancata
        val o = e.onNoteOn(62, 4.5)
        assertEquals(HitKind.WRONG, o.kind)
        assertEquals(0.5, o.nearMissSec!!, 1e-9)
    }

    @Test fun inTimeIsAHitWithNoNearMiss() {
        val e = engine(60 to 0.0, 62 to 4.0)
        val o = e.onNoteOn(62, 4.1)
        assertEquals(HitKind.HIT, o.kind)
        assertNull(o.nearMissSec)
    }

    @Test fun aKeyThatIsNotInTheSongHasNoNearMiss() {
        val o = engine(60 to 0.0, 62 to 4.0).onNoteOn(64, 3.0)
        assertEquals(HitKind.WRONG, o.kind)
        assertNull(o.nearMissSec)
    }

    @Test fun farFromAnyNoteOfThatKeyThereIsNoNearMiss() {
        val o = engine(60 to 0.0, 62 to 4.0).onNoteOn(62, 1.5) // 2,5 s prima: oltre la finestra di confronto
        assertEquals(HitKind.WRONG, o.kind)
        assertNull(o.nearMissSec)
    }

    @Test fun anAlreadyPlayedNoteIsNotAnOptionAgain() {
        val e = engine(60 to 0.0, 62 to 4.0)
        e.onNoteOn(62, 4.0) // colpita
        val o = e.onNoteOn(62, 4.8) // ripetuta fuori tempo: la nota è già colpita, non c'è un'altra da confrontare
        assertEquals(HitKind.WRONG, o.kind)
        assertNull(o.nearMissSec)
    }

    @Test fun theClosestNoteOfTheKeyIsChosen() {
        val e = engine(62 to 1.0, 62 to 3.0)
        val o = e.onNoteOn(62, 2.2)
        assertEquals(-0.8, o.nearMissSec!!, 1e-9)
        assertNotNull(o.nearNoteSec)
        assertEquals(3.0, o.nearNoteSec!!, 1e-9)
    }
}
