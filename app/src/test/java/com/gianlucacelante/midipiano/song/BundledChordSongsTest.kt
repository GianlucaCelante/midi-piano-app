package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledChordSongsTest {
    @Test
    fun everyBundledChordSongParsesAndHasBarsAndNotes() {
        val files = File("src/main/assets/songs").listFiles { f -> f.extension == "chords" }.orEmpty()
        assertTrue(files.size >= 13)
        for (f in files) {
            val s = ChordSongParser.parse(f.readBytes(), f.nameWithoutExtension, SongSource("t", "t"))
            assertTrue("${f.name}: poche battute", s.barCount >= 4)
            assertTrue("${f.name}: durata", s.durationSec in 15.0..400.0)
            assertTrue("${f.name}: senza mano sinistra", s.notes.any { it.hand == Hand.LEFT } && s.notes.any { it.hand == Hand.RIGHT })
        }
    }
}
