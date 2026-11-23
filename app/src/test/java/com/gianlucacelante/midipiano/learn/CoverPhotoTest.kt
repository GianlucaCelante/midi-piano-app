package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPhotoTest {
    @Test
    fun photoIndexIsStableAndInRange() {
        for (id in listOf("a", "amazing-grace", "bwv117a", "", "zzzzzzzzzzzzzzzzzzzzzzzzzz")) {
            val i = Covers.photoIndex(id, 12)
            assertTrue(i in 0..11)
            assertEquals(i, Covers.photoIndex(id, 12))
        }
        assertEquals(-1, Covers.photoIndex("x", 0))
    }

    @Test
    fun everyBundledCoverHasItsCredit() {
        val dir = java.io.File("src/main/assets/covers")
        val credits = java.io.File(dir, "CREDITS.json").readText()
        for (f in dir.listFiles { x -> x.extension == "jpg" }.orEmpty()) assertTrue("manca il credito di ${f.name}", credits.contains(f.name))
    }
}
