package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingMapTest {
    private fun n(t: Double, key: Int = 60, beats: Double = 0.25, hand: Hand = Hand.RIGHT) = SongNote(t, 0.1, key, 80, hand, beats)

    private fun map(vararg notes: SongNote, fifths: Int = 0, bars: List<Double> = emptyList()): SpacingMap {
        val s = Song("s", "t", null, SongSource("t", "t"), notes.toList(), keyFifths = fifths)
        return SpacingMap.build(Chords.group(NotationLayout.layout(s, Hand.BOTH)), 0.5, bars)
    }

    @Test
    fun closeNotesAreKeptApartByAMinimumGap() {
        val m = map(n(0.0), n(0.05), n(0.10))
        assertTrue(m.xAt(0.05) - m.xAt(0.0) >= SpacingMap.MIN_GAP - 1e-9)
        assertTrue(m.xAt(0.10) - m.xAt(0.05) >= SpacingMap.MIN_GAP - 1e-9)
    }

    @Test
    fun spacingFollowsTheNoteValueLikeFlowkey() {
        // misure su Flowkey (in spazi): corcheia 4,7, semiminima ~7,1, minima ~10,6; secPerBeat = 0,5 s
        assertEquals(4.7, SpacingMap.space(0.5), 0.01)
        assertEquals(7.1, SpacingMap.space(1.0), 0.1)
        assertEquals(10.6, SpacingMap.space(2.0), 0.2)
        val m = map(n(0.0), n(0.5, 62), n(1.5, 64)) // una corcheia e poi una semiminima
        assertEquals(SpacingMap.space(1.0), m.xAt(0.5) - m.xAt(0.0), 0.01) // 0,5 s = 1 quarto a 120 bpm
        assertEquals(SpacingMap.space(2.0), m.xAt(1.5) - m.xAt(0.5), 0.01)
    }

    @Test
    fun theFirstNoteAfterABarlineLeavesABreathingSpace() {
        val m = map(n(0.0), n(0.5, 62), n(1.0, 64), bars = listOf(1.0))
        assertEquals(SpacingMap.BAR_PAD, m.xAt(1.0) - m.barX(1.0), 1e-9) // mai una nota sulla stanghetta
        assertTrue(m.barX(1.0) > m.xAt(0.5)) // la stanghetta sta dopo la nota precedente
        val noBar = map(n(0.0), n(0.5, 62), n(1.0, 64))
        assertEquals(noBar.xAt(1.0) - noBar.xAt(0.5) + SpacingMap.BAR_PAD, m.xAt(1.0) - m.xAt(0.5), 1e-9)
    }

    @Test
    fun theMapIsMonotonicAndInvertible() {
        val m = map(n(0.0), n(0.1, 62), n(0.9, 64), n(1.0, 65), n(3.0, 67))
        var last = -1e9
        for (i in 0..60) {
            val t = -1.0 + i * 0.1
            val x = m.xAt(t)
            assertTrue(x > last)
            last = x
            assertEquals(t, m.timeAt(x), 1e-6)
        }
    }

    @Test
    fun notesAtTheSameTimeInBothStavesShareTheSamePosition() {
        val m = map(n(0.0, 72), n(0.005, 48, hand = Hand.LEFT), n(1.0, 74), n(1.0, 50, hand = Hand.LEFT))
        assertEquals(SpacingMap.space(2.0), m.xAt(1.0) - m.xAt(0.0), 0.01) // i due righi contano come un punto solo
    }

    @Test
    fun dottedNotesAndAccidentalsNeedMoreRoomWhenCrowded() {
        val plain = map(n(0.0), n(0.1, 62))
        val dotted = map(n(0.0, beats = 0.75), n(0.1, 62))
        val accidental = map(n(0.0), n(0.1, 61))
        val gap = { m: SpacingMap -> m.xAt(0.1) - m.xAt(0.0) }
        assertTrue(gap(dotted) > gap(plain))
        assertTrue(gap(accidental) > gap(plain))
    }

    @Test
    fun anEmptyMapIsTheLinearTimeScale() {
        val m = SpacingMap.build(emptyList())
        assertEquals(3.0 * SpacingMap.BASE_PER_SEC, m.xAt(3.0), 1e-9)
        assertEquals(3.0, m.timeAt(3.0 * SpacingMap.BASE_PER_SEC), 1e-9)
        assertEquals(3.0 * SpacingMap.BASE_PER_SEC, m.scrollAt(3.0), 1e-9)
    }

    @Test
    fun outsideTheNotesItExtrapolatesLinearly() {
        val m = map(n(1.0), n(2.0, 62))
        assertEquals(m.xAt(1.0) - 2.0 * SpacingMap.BASE_PER_SEC, m.xAt(-1.0), 1e-9)
        assertEquals(m.xAt(2.0) + 3.0 * SpacingMap.BASE_PER_SEC, m.xAt(5.0), 1e-9)
    }

    @Test
    fun scrollIsSmoothAndNeverFarFromTheExactPosition() {
        // velocità molto diverse fra tratti: una corcheia veloce poi una nota lunga
        val m = map(n(0.0), n(0.25, 62), n(0.5, 64), n(2.5, 65), n(3.0, 67))
        var maxErr = 0.0
        var maxJump = 0.0
        var prevV = Double.NaN
        var t = 0.0
        while (t <= 3.0) {
            maxErr = maxOf(maxErr, kotlin.math.abs(m.scrollAt(t) - m.xAt(t)))
            val v = (m.scrollAt(t + 0.001) - m.scrollAt(t)) / 0.001
            if (!prevV.isNaN()) maxJump = maxOf(maxJump, kotlin.math.abs(v - prevV))
            prevV = v
            t += 0.001
        }
        // la nota arriva al cursore con scarto piccolo (frazione di spazio)...
        assertTrue("errore $maxErr", maxErr < 1.0)
        // ...e la velocità non fa salti bruschi fra due millisecondi consecutivi
        assertTrue("salto di velocità $maxJump", maxJump < 0.3)
        // senza ammorbidire, la velocità salta a ogni nota
        var rawJump = 0.0
        var rawPrev = Double.NaN
        t = 0.0
        while (t <= 3.0) {
            val v = (m.xAt(t + 0.001) - m.xAt(t)) / 0.001
            if (!rawPrev.isNaN()) rawJump = maxOf(rawJump, kotlin.math.abs(v - rawPrev))
            rawPrev = v
            t += 0.001
        }
        assertTrue("grezza $rawJump", rawJump > 5 * maxJump)
    }
}

class SpacingOnRealSongsTest {
    /** Su tutti i brani inclusi nessuna coppia di accordi consecutivi sta più vicina del minimo. */
    @Test
    fun noBundledSongHasCrowdedNotes() {
        val dir = java.io.File("src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension == "mid" || f.extension == "midi" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val song = com.gianlucacelante.midipiano.song.MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val chords = Chords.group(NotationLayout.layout(song, Hand.BOTH))
            val m = SpacingMap.build(chords, song.secPerBeat, song.barLines)
            // note a meno di 12 ms l'una dall'altra (abbellimenti/arpeggi) condividono lo stesso punto: si controllano le altre coppie
            val sorted = chords.sortedBy { it.startSec }
            for (i in 1 until sorted.size) {
                if (sorted[i].startSec - sorted[i - 1].startSec <= 0.012) continue
                val gap = m.xAt(sorted[i].startSec) - m.xAt(sorted[i - 1].startSec)
                assertTrue("${f.name}: $gap", gap >= SpacingMap.MIN_GAP - 0.3)
            }
        }
    }

    @Test
    fun noBundledMusicXmlSongHasCrowdedNotes() {
        val dir = java.io.File("src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension == "mxl" || f.extension == "musicxml" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val song = com.gianlucacelante.midipiano.song.MusicXmlParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val chords = Chords.group(NotationLayout.layout(song, Hand.BOTH))
            val m = SpacingMap.build(chords, song.secPerBeat, song.barLines)
            // note a meno di 12 ms l'una dall'altra (abbellimenti/arpeggi) condividono lo stesso punto: si controllano le altre coppie
            val sorted = chords.sortedBy { it.startSec }
            for (i in 1 until sorted.size) {
                if (sorted[i].startSec - sorted[i - 1].startSec <= 0.012) continue
                val gap = m.xAt(sorted[i].startSec) - m.xAt(sorted[i - 1].startSec)
                assertTrue("${f.name}: $gap", gap >= SpacingMap.MIN_GAP - 0.3)
            }
        }
    }
}
