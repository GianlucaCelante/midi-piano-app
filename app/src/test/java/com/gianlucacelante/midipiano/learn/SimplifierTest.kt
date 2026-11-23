package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimplifierTest {
    // 4/4 a 120 bpm: quarto = 0,5 s, battuta = 2 s
    private fun n(t: Double, key: Int, hand: Hand, dur: Double = 0.4, lyric: String? = null) = SongNote(t, dur, key, 80, hand, lyric = lyric)

    /** 2 battute: a destra corcheie/sedicesimi con accordi e abbellimenti, a sinistra accordi sincopati. */
    private val busy: Song = run {
        val notes = ArrayList<SongNote>()
        for (i in 0 until 16) notes += n(i * 0.25, 72 + (i % 5), Hand.RIGHT, 0.2) // sedicesimi
        notes += n(0.0, 76, Hand.RIGHT, 0.5) // accordo a destra: due note insieme
        notes += n(2.0, 79, Hand.RIGHT, 0.5, "la")
        notes += n(2.0, 74, Hand.RIGHT, 0.5)
        for (t in listOf(0.0, 0.25, 0.5, 1.0, 1.5, 2.0, 2.75, 3.5)) { // sinistra: accordi di 3 note, anche fuori tempo
            notes += n(t, 48, Hand.LEFT); notes += n(t, 52, Hand.LEFT); notes += n(t, 55, Hand.LEFT)
        }
        Song("s", "t", null, SongSource("t", "t"), notes.sortedBy { it.startSec }, barLines = listOf(2.0), secPerBeat = 0.5)
    }

    @Test
    fun theOriginalVersionIsUntouched() {
        assertEquals(busy.notes, Simplifier.version(busy, Simplifier.ORIGINAL).notes)
    }

    @Test
    fun theNumberOfNotesGrowsWithTheLevel() {
        val e = Simplifier.version(busy, Simplifier.EASY).notes.size
        val m = Simplifier.version(busy, Simplifier.MEDIUM).notes.size
        assertTrue("$e <= $m <= ${busy.notes.size}", e < m && m < busy.notes.size)
    }

    @Test
    fun easyAndMediumHaveASingleMelodyLineAtTheRight() {
        for (lvl in listOf(Simplifier.EASY, Simplifier.MEDIUM)) {
            val right = Simplifier.version(busy, lvl).notes.filter { it.hand == Hand.RIGHT }
            assertEquals("livello $lvl: note insieme a destra", right.size, right.map { it.startSec }.distinct().size)
        }
    }

    @Test
    fun easyKeepsOneLeftNotePerBar() {
        val left = Simplifier.version(busy, Simplifier.EASY).notes.filter { it.hand == Hand.LEFT }
        assertEquals(listOf(0.0, 2.0), left.map { it.startSec })
        assertEquals(48, left[0].key) // il basso della battuta
    }

    @Test
    fun mediumKeepsLeftNotesOnTheBeatOnly() {
        val left = Simplifier.version(busy, Simplifier.MEDIUM).notes.filter { it.hand == Hand.LEFT }
        assertTrue(left.all { Math.abs(it.startSec / 0.5 - Math.round(it.startSec / 0.5)) < 0.06 })
        assertTrue(left.groupBy { it.startSec }.values.all { it.size <= 2 })
    }

    @Test
    fun notesWithLyricsAreNeverDropped() {
        for (lvl in listOf(Simplifier.EASY, Simplifier.MEDIUM)) {
            assertTrue(Simplifier.version(busy, lvl).notes.any { it.lyric == "la" })
        }
    }

    @Test
    fun keptNotesFillTheGapSoTheRhythmStillReads() {
        val right = Simplifier.version(busy, Simplifier.EASY).notes.filter { it.hand == Hand.RIGHT }.sortedBy { it.startSec }
        for (i in 0 until right.size - 1) assertTrue(right[i].startSec + right[i].durationSec <= right[i + 1].startSec + 1e-6)
    }

    @Test
    fun aSongThatIsAlreadySimpleOnlyOffersTheOriginal() {
        val simple = Song("s", "t", null, SongSource("t", "t"), (0 until 8).map { n(it * 1.0, 60 + it, Hand.RIGHT, 0.9) }, barLines = listOf(2.0, 4.0, 6.0), secPerBeat = 0.5)
        assertEquals(listOf(Simplifier.ORIGINAL), Simplifier.availableVersions(simple))
    }

    @Test
    fun aBusySongOffersAllThreeVersions() {
        assertEquals(listOf(1, 2, 3), Simplifier.availableVersions(busy))
    }

    @Test
    fun everyBundledSongKeepsMonotonicVersions() {
        val dir = File("src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension in listOf("mid", "midi") }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val song = com.gianlucacelante.midipiano.song.MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val e = Simplifier.version(song, 1).notes.size
            val m = Simplifier.version(song, 2).notes.size
            assertTrue("${f.name}: $e $m ${song.notes.size}", e in 1..m && m <= song.notes.size)
            assertFalse(Simplifier.version(song, 1).notes.any { it.durationSec <= 0.0 })
        }
    }

    @Test
    fun simplifiedNotesKeepTheirRealValueAtSlowTempos() {
        // 60 bpm (1 s a quarto), 4/4: quattro semiminime per battuta, due battute, mano destra e basso
        val notes = ArrayList<SongNote>()
        for (i in 0 until 8) notes += SongNote(i * 1.0, 0.9, 72 + i % 3, 80, Hand.RIGHT)
        notes += SongNote(0.0, 3.8, 48, 80, Hand.LEFT); notes += SongNote(4.0, 3.8, 50, 80, Hand.LEFT)
        val song = Song("s", "t", null, SongSource("t", "t"), notes, barLines = listOf(4.0), secPerBeat = 1.0)
        for (lvl in listOf(Simplifier.EASY, Simplifier.MEDIUM)) {
            val staff = com.gianlucacelante.midipiano.notation.NotationLayout.layout(Simplifier.version(song, lvl), Hand.BOTH)
            assertTrue("livello $lvl: legature finte", staff.none { it.tieNext || it.tieFromPrev })
            assertTrue("livello $lvl: ${staff.map { it.beats }}", staff.filter { it.hand == Hand.RIGHT }.all { it.beats == 1.0 || it.beats == 2.0 || it.beats == 3.0 })
        }
    }

    @Test
    fun anInnerVoiceBelowAHeldMelodyNoteIsNotKeptAsMelody() {
        val notes = listOf(SongNote(0.0, 1.0, 84, 80, Hand.RIGHT), SongNote(0.5, 0.4, 64, 80, Hand.RIGHT), SongNote(1.0, 0.4, 86, 80, Hand.RIGHT))
        val song = Song("s", "t", null, SongSource("t", "t"), notes, secPerBeat = 0.5, barLines = listOf(2.0))
        for (lvl in listOf(Simplifier.EASY, Simplifier.MEDIUM)) {
            val keys = Simplifier.simplifyAll(song, lvl).map { it.key }
            assertEquals("livello $lvl", listOf(84, 86), keys)
        }
    }

    @Test
    fun theBeatGridStartsFromTheBarNotFromZeroWhenThereIsAPickup() {
        // levare di una croma (0,25 s), poi crome staccate sulle pulsazioni di battute da 2 s
        val notes = listOf(SongNote(0.0, 0.2, 60, 80, Hand.RIGHT)) + (0 until 8).map { SongNote(0.25 + it * 0.5, 0.2, 62 + it % 3, 80, Hand.RIGHT) }
        val song = Song("s", "t", null, SongSource("t", "t"), notes, secPerBeat = 0.5, barLines = listOf(0.25, 2.25, 4.25))
        val kept = Simplifier.version(song, Simplifier.MEDIUM).notes.count { it.hand == Hand.RIGHT }
        assertTrue("tenute $kept su 9", kept >= 8)
    }

    @Test
    fun compoundMetersFollowTheDottedQuarterBeat() {
        // 6/8 a 0,5 s per quarto: pulsazioni a 0 e 0,75 s; crome staccate ogni 0,25 s su due battute
        val notes = (0 until 12).map { SongNote(it * 0.25, 0.2, 60 + it % 4, 80, Hand.RIGHT) }
        val song = Song("s", "t", null, SongSource("t", "t"), notes, secPerBeat = 0.5, barLines = listOf(1.5), timeNumerator = 6, timeDenominator = 8)
        val starts = Simplifier.simplifyAll(song, Simplifier.MEDIUM).map { it.startSec }
        assertTrue("0,75 è una pulsazione: $starts", starts.any { Math.abs(it - 0.75) < 1e-6 })
        assertTrue("0,5 non lo è: $starts", starts.none { Math.abs(it - 0.5) < 1e-6 })
    }
}
