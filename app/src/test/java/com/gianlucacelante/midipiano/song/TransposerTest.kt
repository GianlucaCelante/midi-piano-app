package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransposerTest {
    private fun song(fifths: Int = 0, vararg keys: Int) = Song(
        "s", "t", null, SongSource("t", "t"),
        keys.mapIndexed { i, k -> SongNote(i * 0.5, 0.4, k, 80, Hand.RIGHT, beats = 1.0, diatonic = 28, alter = 1, lyric = "la") },
        keyFifths = fifths, chordSymbols = listOf(ChordSymbol(0.0, "Am7/G"), ChordSymbol(1.0, "N.C."), ChordSymbol(2.0, "F#m")),
    )

    @Test
    fun notesMoveByTheSemitonesAndKeepTheirRhythmAndLyrics() {
        val t = Transposer.transpose(song(0, 60, 64, 67), 2)
        assertEquals(listOf(62, 66, 69), t.notes.map { it.key })
        assertEquals(listOf(0.0, 0.5, 1.0), t.notes.map { it.startSec })
        assertEquals(listOf(1.0, 1.0, 1.0), t.notes.map { it.beats })
        assertEquals("la", t.notes[0].lyric)
        assertNull(t.notes[0].diatonic) // l'ortografia scritta non vale più: si rideduce dalla nuova armatura
    }

    @Test
    fun theKeySignatureFollowsTheNewKey() {
        assertEquals(2, Transposer.transpose(song(0, 60), 2).keyFifths)   // Do -> Re: due diesis
        assertEquals(3, Transposer.transpose(song(1, 60), 2).keyFifths)   // Sol -> La: tre diesis
        assertEquals(1, Transposer.transpose(song(-1, 60), 2).keyFifths)  // Fa -> Sol: un diesis
        assertEquals(-3, Transposer.transpose(song(0, 60), 3).keyFifths)  // Do -> Mib: tre bemolli
        assertEquals(0, Transposer.transpose(song(2, 60), -2).keyFifths)  // Re -> Do
    }

    @Test
    fun chordSymbolsAreTransposedIncludingTheBassNote() {
        val t = Transposer.transpose(song(0, 60), 2).chordSymbols.map { it.text }
        assertEquals(listOf("Bm7/A", "N.C.", "G#m"), t)
        val flats = Transposer.transpose(song(0, 60), 3).chordSymbols.map { it.text } // Mib: si scrive con i bemolli
        assertEquals("Cm7/Bb", flats[0])
    }

    @Test
    fun zeroSemitonesReturnsTheSameSongAndOutOfRangeNotesAreDropped() {
        val s = song(0, 60)
        assertTrue(Transposer.transpose(s, 0) === s)
        val extreme = Transposer.transpose(song(0, 100, 106), 4) // 104 ok, 110 oltre la tastiera (108)
        assertEquals(listOf(104), extreme.notes.map { it.key })
    }

    @Test
    fun keyChangesAreTransposedToo() {
        val s = song(0, 60).copy(keyChanges = listOf(KeyChange(4.0, 1)))
        assertEquals(listOf(KeyChange(4.0, 3)), Transposer.transpose(s, 2).keyChanges)
    }
}
