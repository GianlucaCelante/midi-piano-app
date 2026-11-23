package com.gianlucacelante.midipiano.song

import com.gianlucacelante.midipiano.notation.Clef
import com.gianlucacelante.midipiano.notation.NotationLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SongCleanupTest {
    private fun n(t: Double, d: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, d, key, 80, hand)

    @Test
    fun aRestruckKeyEndsTheEarlierNote() {
        val r = SongCleanup.tidy(listOf(n(0.0, 3.0, 65), n(1.0, 1.0, 65)))
        assertEquals(1.0, r[0].durationSec, 1e-9)
        assertEquals(2, r.size)
    }

    @Test
    fun identicalNotesBecomeOneLongest() {
        val r = SongCleanup.tidy(listOf(n(0.0, 1.0, 60), n(0.0, 2.0, 60), n(0.0004, 0.5, 60)))
        assertEquals(1, r.size)
        assertEquals(2.0, r[0].durationSec, 1e-9)
    }

    @Test
    fun differentKeysAreNeverTouched() {
        val src = listOf(n(0.0, 3.0, 60), n(1.0, 1.0, 64), n(0.5, 1.0, 67, Hand.LEFT))
        assertEquals(src.sortedWith(compareBy({ it.startSec }, { it.key })), SongCleanup.tidy(src))
    }

    @Test
    fun extremeNotesGoToTheOtherClef() {
        fun clef(key: Int, hand: Hand) = NotationLayout.layout(Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 1.0, key, hand))), Hand.BOTH).single().clef
        assertEquals(Clef.TREBLE, clef(84, Hand.LEFT)) // Do6 alla sinistra
        assertEquals(Clef.BASS, clef(48, Hand.LEFT))
        assertEquals(Clef.BASS, clef(60, Hand.LEFT))   // Do4 alla sinistra resta in basso
        assertEquals(Clef.BASS, clef(45, Hand.RIGHT))  // La2 alla destra
        assertEquals(Clef.TREBLE, clef(60, Hand.RIGHT))
    }
}
