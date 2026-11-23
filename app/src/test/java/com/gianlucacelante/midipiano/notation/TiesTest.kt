package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiesTest {
    private fun song(vararg notes: SongNote, bars: List<Double>) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = bars, secPerBeat = 0.5)

    @Test
    fun aNoteHeldAcrossABarlineIsSplitAndTied() {
        // 4/4 a 120 bpm: battuta = 2 s. Minima col punto (3 quarti) da 1,0 s: attraversa la stanghetta a 2,0 s
        val s = song(SongNote(1.0, 1.4, 62, 80, Hand.RIGHT, 3.0), bars = listOf(2.0))
        val notes = NotationLayout.layout(s, Hand.BOTH).sortedBy { it.startSec }
        assertEquals(2, notes.size)
        assertEquals(1.0, notes[0].startSec, 1e-9)
        assertEquals(2.0, notes[0].beats, 1e-9) // fino alla stanghetta: una minima
        assertTrue(notes[0].tieNext)
        assertFalse(notes[0].tieFromPrev)
        assertEquals(2.0, notes[1].startSec, 1e-9)
        assertEquals(1.0, notes[1].beats, 1e-9) // il resto: una semiminima
        assertTrue(notes[1].tieFromPrev)
        assertFalse(notes[1].tieNext)
        assertNull(notes[1].accidental)
    }

    @Test
    fun anAlteredNoteKeepsItsAccidentalOnlyOnTheFirstPart() {
        val s = song(SongNote(1.0, 1.4, 61, 80, Hand.RIGHT, 3.0), bars = listOf(2.0)) // Do diesis
        val notes = NotationLayout.layout(s, Hand.BOTH).sortedBy { it.startSec }
        assertEquals(Accidental.SHARP, notes[0].accidental)
        assertNull(notes[1].accidental)
    }

    @Test
    fun aNoteThatEndsOnTheBarlineIsNotSplit() {
        val s = song(SongNote(1.0, 0.9, 62, 80, Hand.RIGHT, 2.0), bars = listOf(2.0)) // minima da 1,0 a 2,0 s
        val notes = NotationLayout.layout(s, Hand.BOTH)
        assertEquals(1, notes.size)
        assertFalse(notes[0].tieNext)
    }

    @Test
    fun aLongNoteCrossingTwoBarlinesGivesAChain() {
        // semibreve da 1,5 s in 4/4 (battuta 2 s): 1,5 -> 3,5 attraversa la stanghetta a 2,0 (e non quella a 4,0)
        val s = song(SongNote(1.5, 1.9, 60, 80, Hand.RIGHT, 4.0), bars = listOf(2.0, 4.0))
        val notes = NotationLayout.layout(s, Hand.BOTH).sortedBy { it.startSec }
        assertEquals(2, notes.size)
        assertEquals(1.0, notes[0].beats, 1e-9) // 1,0 quarti fino alla stanghetta (0,5 s = 1 quarto)
        assertEquals(3.0, notes[1].beats, 1e-9)
    }
}
