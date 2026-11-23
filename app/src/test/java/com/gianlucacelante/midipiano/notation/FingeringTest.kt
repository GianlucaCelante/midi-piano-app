package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringTest {
    private fun n(t: Double, key: Int, hand: Hand) = SongNote(t, 0.4, key, 80, hand)
    private fun fingers(notes: List<SongNote>): List<Int?> {
        val f = Fingering.assign(notes)
        return notes.map { f[Fingering.id(it.startSec, it.key)] }
    }

    @Test
    fun rightHandFiveFingerScaleUp() {
        val notes = listOf(60, 62, 64, 65, 67).mapIndexed { i, k -> n(i * 0.5, k, Hand.RIGHT) }
        assertEquals(listOf(1, 2, 3, 4, 5), fingers(notes))
    }

    @Test
    fun rightHandDownwardIsReversed() {
        val notes = listOf(67, 65, 64, 62, 60).mapIndexed { i, k -> n(i * 0.5, k, Hand.RIGHT) }
        assertEquals(listOf(5, 4, 3, 2, 1), fingers(notes))
    }

    @Test
    fun leftHandIsMirrored() {
        val up = listOf(48, 50, 52, 53, 55).mapIndexed { i, k -> n(i * 0.5, k, Hand.LEFT) }
        assertEquals(listOf(5, 4, 3, 2, 1), fingers(up)) // la mano sinistra: il mignolo sul tasto più basso
    }

    @Test
    fun aTriadUsesThumbMiddleAndLittleFinger() {
        val right = listOf(60, 64, 67).map { n(0.0, it, Hand.RIGHT) }
        assertEquals(listOf(1, 3, 5), fingers(right))
        val left = listOf(48, 52, 55).map { n(0.0, it, Hand.LEFT) }
        assertEquals(listOf(5, 3, 1), fingers(left))
    }

    @Test
    fun repeatedNotesKeepTheSameFinger() {
        val notes = listOf(64, 64, 64, 62).mapIndexed { i, k -> n(i * 0.5, k, Hand.RIGHT) }
        val f = fingers(notes)
        assertEquals(f[0], f[1]); assertEquals(f[1], f[2])
        assertTrue(f[3]!! != f[2])
    }

    @Test
    fun everyNoteGetsAFingerBetweenOneAndFiveAndHandsAreIndependent() {
        val notes = (0 until 40).flatMap { i ->
            listOf(n(i * 0.5, 60 + (i * 7) % 17, Hand.RIGHT), n(i * 0.5, 40 + (i * 5) % 13, Hand.LEFT))
        }
        val f = Fingering.assign(notes)
        for (x in notes) {
            val v = f[Fingering.id(x.startSec, x.key)]
            assertNotNull(v)
            assertTrue(v!! in 1..5)
        }
    }

    @Test
    fun aBigLeapDoesNotBreakTheAssignment() {
        val notes = listOf(60, 62, 64, 76, 77, 79).mapIndexed { i, k -> n(i * 0.5, k, Hand.RIGHT) }
        assertTrue(fingers(notes).all { it in 1..5 })
    }

    @Test
    fun emptyInputGivesEmptyResult() {
        assertEquals(0, Fingering.assign(emptyList()).size)
    }

    private fun seq(keys: List<Int>, hand: Hand) = fingers(keys.mapIndexed { i, k -> n(i * 0.5, k, hand) })
    private val cMajor = listOf(60, 62, 64, 65, 67, 69, 71, 72)

    @Test
    fun rightHandScaleUpPassesTheThumbUnder() {
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), seq(cMajor, Hand.RIGHT))
    }

    @Test
    fun rightHandScaleDownPassesTheMiddleFingerOverTheThumb() {
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1), seq(cMajor.reversed(), Hand.RIGHT))
    }

    @Test
    fun leftHandScaleUpMirrors() {
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1), seq(cMajor.map { it - 12 }, Hand.LEFT))
    }

    @Test
    fun fMajorScaleKeepsFourFingersThenTheThumb() {
        val f = listOf(65, 67, 69, 70, 72, 74, 76, 77)
        assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 4), seq(f, Hand.RIGHT))
    }

    @Test
    fun noFingerEverCrossesWithoutTheThumb() {
        // in qualunque successione diatonica un dito non può "scendere" mentre la nota sale, salvo pollice sotto/sopra
        for (hand in listOf(Hand.RIGHT, Hand.LEFT)) for (start in 55..70) {
            val keys = listOf(0, 2, 4, 5, 7, 9, 11, 12, 11, 9, 7, 5, 4, 2, 0).map { start + it }
            val f = seq(keys, hand)
            for (i in 1 until keys.size) {
                val up = keys[i] > keys[i - 1]
                val dir = if (hand == Hand.RIGHT) f[i]!! - f[i - 1]!! else f[i - 1]!! - f[i]!! // > 0: la mano "sale"
                val crosses = (up && dir < 0) || (!up && dir > 0)
                if (crosses) assertTrue("$hand da $start: $f", f[i] == 1 || f[i - 1] == 1)
            }
        }
    }

    @Test
    fun anOctaveInOneHandUsesThumbAndLittleFinger() {
        val right = fingers(listOf(60, 72).map { n(0.0, it, Hand.RIGHT) })
        assertEquals(listOf(1, 5), right)
        val left = fingers(listOf(48, 60).map { n(0.0, it, Hand.LEFT) })
        assertEquals(listOf(5, 1), left)
    }

    @Test
    fun bigLeapsInAMelodyStillGetDifferentFingersOnTheTwoEnds() {
        // salto di un'ottava e più in una linea di note singole: mai lo stesso dito su due note lontane
        val f = seq(listOf(48, 62, 48, 62), Hand.LEFT)
        assertTrue("$f", f[0] != f[1])
        val g = seq(listOf(60, 76, 60), Hand.RIGHT)
        assertTrue("$g", g[0] != g[1])
    }

    @Test
    fun octaveJumpsKeepTheSameFingerOnTheSameNote() {
        val f = seq(listOf(48, 60, 48, 60), Hand.LEFT) // basso, ottava, basso, ottava
        assertEquals(f[0], f[2])
        assertEquals(f[1], f[3])
    }
}
