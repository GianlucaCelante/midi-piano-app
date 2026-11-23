package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongImportTest {
    @Test
    fun slugifyKeepsLettersAndDigitsOnly() {
        assertEquals("per-elisa-v2", SongRepository.slugify("Per Elisa (v2)!"))
        assertEquals("caffe-del-sole", SongRepository.slugify("Caffè_del Sole"))
        assertEquals("brano", SongRepository.slugify("???"))
        assertTrue(SongRepository.slugify("x".repeat(200)).length <= 40)
    }

    @Test
    fun userSongFilesAreRecognisedAndKeepTheirFormat() {
        val e = SongEntry("user-a", "A", null, "user:user-a.mid", "File importato", "Personale", false, null)
        assertEquals(SongFormat.MIDI, e.format)
        val x = SongEntry("user-b", "B", null, "user:user-b.musicxml", "File importato", "Personale", false, null)
        assertEquals(SongFormat.MUSICXML, x.format)
    }

    @Test
    fun importedEntriesRoundTripThroughTheIndexJson() {
        val json = """[{"id":"user-a","title":"A","composer":null,"file":"user:user-a.mid","origin":"File importato","license":"Personale","attribution_required":false,"url":null}]"""
        val back = SongIndex.parse(json)
        assertEquals("user:user-a.mid", back[0].file)
        assertEquals(null, back[0].composer)
    }
}
