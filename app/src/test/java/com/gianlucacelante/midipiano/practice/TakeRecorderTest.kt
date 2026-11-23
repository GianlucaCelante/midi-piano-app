package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeRecorderTest {
    private fun ms(m: Long) = m * 1_000_000L

    @Test
    fun timesAreRelativeToTheFirstKeyPress() {
        val r = TakeRecorder()
        r.noteOn(60, 90, ms(5_000)); r.noteOff(60, ms(5_400))
        r.noteOn(64, 70, ms(6_000)); r.noteOff(64, ms(6_300))
        val s = r.toSong()!!
        assertEquals(listOf(60, 64), s.notes.map { it.key })
        assertEquals(listOf(0.0, 1.0), s.notes.map { it.startSec })
        assertEquals(0.4, s.notes[0].durationSec, 1e-9)
        assertEquals(0.3, s.notes[1].durationSec, 1e-9)
        assertEquals(70, s.notes[1].velocity)
    }

    @Test
    fun aNoteNeverReleasedGetsADefaultLength() {
        val r = TakeRecorder()
        r.noteOn(60, 80, ms(0))
        assertEquals(0.4, r.toSong()!!.notes[0].durationSec, 1e-9)
    }

    @Test
    fun repeatedKeysPairWithTheirOwnRelease() {
        val r = TakeRecorder()
        r.noteOn(60, 80, ms(0)); r.noteOff(60, ms(200)); r.noteOn(60, 80, ms(300)); r.noteOff(60, ms(900))
        assertEquals(listOf(0.2, 0.6), r.toSong()!!.notes.map { it.durationSec })
    }

    @Test
    fun emptyTakeHasNoSongAndClearEmptiesIt() {
        val r = TakeRecorder()
        assertNull(r.toSong())
        r.noteOn(60, 80, ms(0))
        assertTrue(!r.isEmpty)
        r.clear()
        assertTrue(r.isEmpty)
    }

    @Test
    fun theTakeIsCappedSoItNeverGrowsWithoutLimit() {
        val r = TakeRecorder(maxNotes = 10)
        for (i in 0 until 50) { r.noteOn(60 + i % 12, 80, ms(i * 10L)); r.noteOff(60 + i % 12, ms(i * 10L + 5)) }
        assertEquals(10, r.toSong()!!.notes.size)
    }
}
