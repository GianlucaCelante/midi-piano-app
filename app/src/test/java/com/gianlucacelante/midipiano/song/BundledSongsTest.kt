package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifica che tutti i file MIDI inclusi come asset si leggano e producano un brano sensato. */
class BundledSongsTest {
    private val dir = File("src/main/assets/songs")

    @Test
    fun everyBundledSongParses() {
        val files = dir.listFiles { f -> f.extension == "mid" || f.extension == "mxl" }.orEmpty()
        assertTrue("Nessun brano trovato in ${dir.absolutePath}", files.isNotEmpty())
        assertTrue("Manca almeno un .mxl", files.any { it.extension == "mxl" })
        for (f in files) {
            val source = SongSource("test", "test")
            val song = if (f.extension == "mxl") {
                MusicXmlParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, source)
            } else {
                MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, source)
            }
            assertTrue("${f.name}: nessuna nota", song.notes.isNotEmpty())
            assertTrue("${f.name}: durata non valida", song.durationSec in 1.0..3600.0)
            assertTrue("${f.name}: note fuori dal pianoforte", song.notes.all { it.key in 21..108 })
            assertTrue("${f.name}: durate non positive", song.notes.all { it.durationSec > 0 })
            assertTrue("${f.name}: nessun passo", song.steps().isNotEmpty())
        }
    }
}
