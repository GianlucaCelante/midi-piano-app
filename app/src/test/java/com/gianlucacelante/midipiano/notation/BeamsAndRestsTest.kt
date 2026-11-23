package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeamsAndRestsTest {
    // 4/4, un quarto = 0,5 s, battuta = 2 s
    private fun song(vararg notes: SongNote, bars: List<Double> = emptyList(), num: Int = 4, den: Int = 4) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = bars, timeNumerator = num, timeDenominator = den, secPerBeat = 0.5)

    private fun n(t: Double, beats: Double, key: Int = 72, hand: Hand = Hand.RIGHT) = SongNote(t, beats * 0.5, key, 80, hand, beats)

    private fun chords(s: Song, hand: Hand = Hand.RIGHT) =
        Chords.group(NotationLayout.layout(s, Hand.BOTH)).filter { it.clef == if (hand == Hand.LEFT) Clef.BASS else Clef.TREBLE }

    // ---- accordi ----

    @Test
    fun notesAtTheSameTimeAndStaffFormOneChordSortedByTime() {
        val s = song(n(1.0, 1.0, 64), n(0.0, 1.0, 60), n(0.0, 1.0, 67), n(0.0, 1.0, 48, Hand.LEFT))
        val all = Chords.group(NotationLayout.layout(s, Hand.BOTH))
        assertEquals(3, all.size) // violino a 0, violino a 1, basso a 0
        assertEquals(listOf(0.0, 0.0, 1.0), all.map { it.startSec })
        assertEquals(2, all.first { it.clef == Clef.TREBLE && it.startSec == 0.0 }.notes.size)
    }

    // ---- travature ----

    @Test
    fun fourSixteenthsInABeatShareOneBeam() {
        val s = song(n(0.0, 0.25), n(0.125, 0.25), n(0.25, 0.25), n(0.375, 0.25))
        val groups = Beaming.groups(chords(s), s)
        assertEquals(1, groups.size)
        assertEquals(4, groups.single().chords.size)
        assertEquals(2, groups.single().maxLevel)
    }

    @Test
    fun beamsDoNotCrossBeatBoundaries() {
        // quattro crome: due nel primo quarto (0..0,5 s), due nel secondo
        val s = song(n(0.0, 0.5), n(0.25, 0.5), n(0.5, 0.5), n(0.75, 0.5))
        val groups = Beaming.groups(chords(s), s)
        assertEquals(listOf(2, 2), groups.map { it.chords.size })
    }

    @Test
    fun aQuarterNoteBreaksTheBeam() {
        val s = song(n(0.0, 0.5), n(0.25, 0.5), n(0.5, 1.0), n(1.0, 0.5), n(1.25, 0.5))
        val groups = Beaming.groups(chords(s), s)
        assertEquals(listOf(2, 2), groups.map { it.chords.size })
    }

    @Test
    fun aRestBetweenNotesBreaksTheBeam() {
        // croma a 0.0 (dura fino a 0.25) e croma a 0.4: c'è una pausa in mezzo
        val s = song(n(0.0, 0.5), n(0.4, 0.5))
        assertTrue(Beaming.groups(chords(s), s).isEmpty())
    }

    @Test
    fun aSingleShortNoteKeepsItsFlagInsteadOfABeam() {
        val s = song(n(0.0, 0.5), n(0.5, 1.0))
        assertTrue(Beaming.groups(chords(s), s).isEmpty())
    }

    @Test
    fun beamedChordsAreMarkedSoTheirFlagsAreNotDrawn() {
        val s = song(n(0.0, 0.5), n(0.25, 0.5))
        val g = Beaming.groups(chords(s), s).single()
        assertTrue(Beaming.beamed(listOf(g)).containsAll(g.chords))
    }

    @Test
    fun compoundTimeBeamsByDottedQuarter() {
        // 6/8 (croma = 0,25 s): sei crome nella battuta -> due gruppi di tre
        val s = song(n(0.0, 0.5), n(0.25, 0.5), n(0.5, 0.5), n(0.75, 0.5), n(1.0, 0.5), n(1.25, 0.5), num = 6, den = 8)
        assertEquals(listOf(3, 3), Beaming.groups(chords(s), s).map { it.chords.size })
    }

    // ---- pause ----

    @Test
    fun aOneBeatGapGetsAQuarterRest() {
        val s = song(n(0.0, 1.0), n(1.0, 1.0)) // la seconda parte 0,5 s dopo la fine della prima
        val rests = Rests.compute(chords(s), s)
        assertEquals(listOf(1.0), rests.map { it.beats })
        assertEquals(0.5, rests.single().startSec, 1e-9) // il quarto va da 0 a 0.5 s; poi pausa fino a 1.0
    }

    @Test
    fun gapsAreDecomposedIntoNotatedValues() {
        // gap di 1,5 quarti -> una pausa di semiminima col punto
        val s = song(n(0.0, 1.0), n(1.25, 1.0))
        assertEquals(listOf(1.5), Rests.compute(chords(s), s).map { it.beats })
        // gap di 3 quarti -> una pausa di minima col punto
        val s2 = song(n(0.0, 1.0), n(2.0, 1.0)) // la prima finisce a 0.5; la successiva è a 2.0 -> vuoto di 1,5 s = 3 quarti
        assertEquals(listOf(3.0), Rests.compute(chords(s2), s2).map { it.beats })
    }

    @Test
    fun aLeadingGapAtTheStartOfTheStaffGetsRests() {
        val s = song(n(1.0, 1.0)) // il rigo comincia dopo 2 quarti
        val rests = Rests.compute(chords(s), s)
        assertEquals(listOf(2.0), rests.map { it.beats })
        assertEquals(0.0, rests.single().startSec, 1e-9)
    }

    @Test
    fun tinyGapsAreIgnored() {
        val s = song(n(0.0, 1.0), n(0.55, 1.0)) // 0,05 s di scarto: non è una pausa
        assertTrue(Rests.compute(chords(s), s).isEmpty())
    }

    @Test
    fun restsDoNotCrossBarLines() {
        // battuta di 2 s; nota a 0..0.5, poi silenzio fino a 3.0: stanghetta a 2.0
        val s = song(n(0.0, 1.0), n(3.0, 1.0), bars = listOf(2.0))
        val rests = Rests.compute(chords(s), s)
        assertEquals(listOf(0.5, 2.0), rests.map { it.startSec })
        assertTrue(rests.none { it.startSec < 2.0 && it.startSec + it.beats * 0.5 > 2.0 + 1e-9 })
    }

    @Test
    fun emptyBarsGetWholeRests() {
        // nota a 0 (1 quarto, finisce a 0.5), poi silenzio fino a 6.0; stanghette a 2, 4 e 6
        val s = song(n(0.0, 1.0), n(6.0, 1.0), bars = listOf(2.0, 4.0, 6.0))
        val rests = Rests.compute(chords(s), s)
        assertEquals(listOf(3.0, 4.0, 4.0), rests.map { it.beats }) // resto della 1ª battuta, poi due battute intere
        assertEquals(listOf(0.5, 2.0, 4.0), rests.map { it.startSec })
        assertFalse(rests.any { it.beats > 4.0 })
    }

    @Test
    fun rightAndLeftStavesHaveTheirOwnRests() {
        val s = song(n(0.0, 1.0, 60, Hand.RIGHT), n(1.0, 1.0, 60, Hand.RIGHT), n(0.0, 4.0, 48, Hand.LEFT))
        assertEquals(1, Rests.compute(chords(s, Hand.RIGHT), s).size)
        assertTrue(Rests.compute(chords(s, Hand.LEFT), s).isEmpty())
    }
}
