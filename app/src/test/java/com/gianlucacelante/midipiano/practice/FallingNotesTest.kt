package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallingNotesTest {
    private fun n(t: Double, d: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, d, key, 80, hand)

    @Test
    fun keepsOnlyNotesInsideTheWindow() {
        val notes = listOf(n(0.0, 0.5, 60), n(1.0, 0.5, 62), n(5.0, 0.5, 64), n(10.0, 0.5, 65))
        val v = FallingNotes.visible(notes, currentTimeSec = 1.0, horizonSec = 4.0, hand = Hand.BOTH)
        assertEquals(listOf(62, 64), v.map { it.key })
    }

    @Test
    fun offsetIsRelativeToTheCurrentTime() {
        val v = FallingNotes.visible(listOf(n(3.0, 1.0, 60)), 1.0, 4.0, Hand.BOTH).single()
        assertEquals(2.0, v.offsetSec, 1e-9)
        assertEquals(1.0, v.durationSec, 1e-9)
    }

    @Test
    fun notesAlreadyStartedButStillSoundingAreIncluded() {
        val v = FallingNotes.visible(listOf(n(0.0, 3.0, 60)), 1.0, 4.0, Hand.BOTH)
        assertEquals(1, v.size)
        assertTrue(v.single().offsetSec < 0)
    }

    @Test
    fun filtersByHand() {
        val notes = listOf(n(0.0, 1.0, 72, Hand.RIGHT), n(0.0, 1.0, 48, Hand.LEFT))
        assertEquals(listOf(48), FallingNotes.visible(notes, 0.0, 4.0, Hand.LEFT).map { it.key })
        assertEquals(2, FallingNotes.visible(notes, 0.0, 4.0, Hand.BOTH).size)
    }

    @Test
    fun keyboardRangeCoversTheSongAndRoundsToOctaves() {
        val notes = listOf(n(0.0, 1.0, 62), n(0.0, 1.0, 79))
        val r = FallingNotes.keyboardRange(notes)
        assertTrue(r == 21..108 || (r.first % 12 == 0 && r.last % 12 == 11)) // 88 tasti interi, oppure da un Do a un Si
        assertTrue(62 in r && 79 in r)
    }

    @Test
    fun keyboardRangeHasAMinimumWidthForTinySongs() {
        val r = FallingNotes.keyboardRange(listOf(n(0.0, 1.0, 60)))
        assertTrue(r.last - r.first + 1 >= 36)
    }

    @Test
    fun keyboardRangeIsNeverEmptyEvenForNotesAboveThePiano() {
        val r = FallingNotes.keyboardRange(listOf(n(0.0, 1.0, 115), n(0.0, 1.0, 120)))
        assertTrue(r.first <= r.last)
        assertTrue(r.last - r.first + 1 >= 36)
    }

    @Test
    fun keyboardRangeIsClampedToTheRealPiano() {
        val r = FallingNotes.keyboardRange(listOf(n(0.0, 1.0, 21), n(0.0, 1.0, 108)))
        assertTrue(r.first >= 21 && r.last <= 108)
    }
}
