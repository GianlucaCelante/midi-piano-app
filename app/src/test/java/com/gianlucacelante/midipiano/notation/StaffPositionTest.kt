package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Test

class StaffPositionTest {
    private fun pos(key: Int, hand: Hand): Int {
        val s = Song("s", "t", null, SongSource("t", "t"), listOf(SongNote(0.0, 1.0, key, 80, hand)))
        return NotationLayout.layout(s, Hand.BOTH).single().position
    }

    // posizione: 0 = riga più bassa, 1 = primo spazio, 2 = seconda riga...
    @Test
    fun bassClefPositionsMatchTheKeys() {
        assertEquals(0, pos(43, Hand.LEFT)) // Sol2 = riga più bassa
        assertEquals(1, pos(45, Hand.LEFT)) // La2 = primo spazio
        assertEquals(2, pos(47, Hand.LEFT)) // Si2 = seconda riga
        assertEquals(3, pos(48, Hand.LEFT)) // Do3
        assertEquals(8, pos(57, Hand.LEFT)) // La3 = riga più alta (la quinta, pos 8)
        assertEquals(-1, pos(41, Hand.LEFT)) // Fa2 = sotto il rigo
    }

    @Test
    fun trebleClefPositionsMatchTheKeys() {
        assertEquals(0, pos(64, Hand.RIGHT)) // Mi4 = riga più bassa
        assertEquals(-2, pos(60, Hand.RIGHT)) // Do4 = taglio sotto il rigo
        assertEquals(8, pos(77, Hand.RIGHT)) // Fa5 = riga più alta
    }

    @Test
    fun withinEachClefHigherKeysAreNeverLowerOnTheStaff() {
        for (hand in listOf(Hand.LEFT, Hand.RIGHT)) {
            val last = HashMap<Clef, Int>()
            for (k in 36..96) {
                val sg = Song("s", "t", null, SongSource("t", "t"), listOf(SongNote(0.0, 1.0, k, 80, hand)))
                val n = NotationLayout.layout(sg, Hand.BOTH).single()
                assert(n.position >= (last[n.clef] ?: Int.MIN_VALUE)) { "$hand $k scende: ${n.position}" }
                last[n.clef] = n.position
            }
        }
    }
}
