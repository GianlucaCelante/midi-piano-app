package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPickTest {
    private val files = listOf("01-generic.jpg", "02-generic.jpg", "t-beethoven.jpg", "t-bach-wide.jpg", "t-ritmo.jpg")
    private val map = mapOf("beethoven" to "t-beethoven.jpg", "bach" to "t-bach-wide.jpg", "ritmo" to "t-ritmo.jpg", "johann sebastian bach" to "t-bach-wide.jpg")

    @Test
    fun aKeywordInTheHintPicksItsPhoto() {
        assertEquals("t-beethoven.jpg", Covers.pickPhoto("x", "Für Elise Ludwig van Beethoven", files, map))
        assertEquals("t-ritmo.jpg", Covers.pickPhoto("ritmo-figure", "Ritmo e figure", files, map))
    }

    @Test
    fun theLongestKeywordWins() {
        val m = map + ("elise" to "01-generic.jpg") + ("für elise ludwig van beethoven" to "02-generic.jpg")
        assertEquals("02-generic.jpg", Covers.pickPhoto("x", "Für Elise Ludwig van Beethoven", files, m))
    }

    @Test
    fun withoutAKeywordAGenericPhotoIsUsedAndIsStable() {
        val a = Covers.pickPhoto("sconosciuto", "Boh", files, map)!!
        assertTrue(a in listOf("01-generic.jpg", "02-generic.jpg")) // mai il ritratto di qualcun altro
        assertEquals(a, Covers.pickPhoto("sconosciuto", "Boh", files, map))
    }

    @Test
    fun noPhotosGivesNull() {
        assertNull(Covers.pickPhoto("a", "b", emptyList(), map))
        assertNotNull(Covers.pickPhoto("a", "b", files, emptyMap()))
    }

    @Test
    fun songsWithoutAKeywordMatchGetNoPhotoWhenGenericIsOff() {
        // i brani non usano foto generiche: senza ritratto del compositore resta la grafica generata (null)
        assertNull(Covers.pickPhoto("sconosciuto", "Boh", files, map, allowGeneric = false))
        assertEquals("t-beethoven.jpg", Covers.pickPhoto("x", "Für Elise Ludwig van Beethoven", files, map, allowGeneric = false))
    }

    @Test
    fun songsOnlyUseComposerPortraitsNotCourseTopics() {
        val m = mapOf("beethoven" to "t-beethoven.jpg", "accordi" to "t-accordi.jpg", "greensleeves" to "t-green.jpg")
        val f = listOf("t-beethoven.jpg", "t-accordi.jpg", "t-green.jpg")
        assertNull(Covers.pickPhoto("accordi-giro-di-do", "Giro di Do Progressione armonica", f, m, allowGeneric = false))
        assertNull(Covers.pickPhoto("traditional-greensleeves", "Greensleeves Tradizionale", f, m, allowGeneric = false))
        assertEquals("t-beethoven.jpg", Covers.pickPhoto("x", "Für Elise Ludwig van Beethoven", f, m, allowGeneric = false))
        // i corsi (generico permesso) continuano a usare le parole chiave di argomento
        assertEquals("t-accordi.jpg", Covers.pickPhoto("accordi-rivolti", "Accordi", f, m))
    }
}
