package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongIndexTest {
    private val json = """
        [
          {"id":"a","title":"Uno","composer":"Bach","file":"a.mid","origin":"Mutopia Project","license":"Public Domain",
           "attribution_required":false,"url":"https://example.org/a/"},
          {"id":"b","title":"Due","composer":null,"file":"b.mxl","origin":"OpenScore Lieder","license":"CC0",
           "attribution_required":true}
        ]
    """.trimIndent()

    @Test
    fun parsesEntriesWithAllFields() {
        val e = SongIndex.parse(json)
        assertEquals(2, e.size)
        assertEquals("Uno", e[0].title)
        assertEquals("Bach", e[0].composer)
        assertEquals("a.mid", e[0].file)
        assertFalse(e[0].attributionRequired)
        assertEquals("https://example.org/a/", e[0].url)
    }

    @Test
    fun missingOptionalFieldsBecomeNull() {
        val b = SongIndex.parse(json)[1]
        assertNull(b.composer)
        assertNull(b.url)
        assertTrue(b.attributionRequired)
        assertEquals(SongFormat.MUSICXML, b.format)
    }

    @Test
    fun detectsFormatFromExtension() {
        assertEquals(SongFormat.MIDI, SongIndex.parse(json)[0].format)
    }

    @Test
    fun entriesWithoutLicenseAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SongIndex.parse("""[{"id":"x","title":"T","file":"x.mid","origin":"o"}]""")
        }
    }

    @Test
    fun duplicateIdsAreRejected() {
        val dup = """[
          {"id":"a","title":"A","file":"a.mid","origin":"o","license":"CC0"},
          {"id":"a","title":"B","file":"b.mid","origin":"o","license":"CC0"}]"""
        assertThrows(IllegalArgumentException::class.java) { SongIndex.parse(dup) }
    }

    @Test
    fun theBundledIndexIsValidAndFilesExist() {
        val dir = File("src/main/assets/songs")
        val entries = SongIndex.parse(File(dir, "index.json").readText())
        assertTrue(entries.isNotEmpty())
        entries.forEach { assertTrue("${it.file} non esiste", File(dir, it.file).exists()) }
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }
}
