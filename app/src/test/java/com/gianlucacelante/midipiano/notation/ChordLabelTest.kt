package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChordLabelTest {
    private fun n(key: Int, pos: Int) = StaffNote(key, Clef.TREBLE, 0.0, 1.0, pos, null, 1.0, Hand.RIGHT)

    @Test fun labelListsTheNamesFromLowToHigh() {
        // Do4 (sotto il rigo), Mi4, Sol4 in ordine sparso
        val c = Chord(Clef.TREBLE, 0.0, listOf(n(67, 2), n(60, -2), n(64, 0)))
        assertEquals("do mi sol", c.namesLabel)
    }

    @Test fun labelIsBuiltOnlyOnce() {
        // disegnata a ogni fotogramma: non deve ricostruire (e riallocare) la stringa
        val c = Chord(Clef.TREBLE, 0.0, listOf(n(64, 0), n(67, 2)))
        assertSame(c.namesLabel, c.namesLabel)
    }
}
