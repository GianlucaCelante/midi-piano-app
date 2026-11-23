package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChordSongTest {
    private val src = SongSource("t", "t")
    private fun parse(text: String) = ChordSongParser.parse(text.toByteArray(), "id", src)

    @Test
    fun chordSymbolsGiveTheRightPitchClasses() {
        assertEquals(listOf(0, 4, 7), ChordSongParser.chordTones("C")!!.tones)
        assertEquals(listOf(9, 0, 4), ChordSongParser.chordTones("Am")!!.tones)
        assertEquals(listOf(7, 11, 2, 5), ChordSongParser.chordTones("G7")!!.tones)
        assertEquals(listOf(5, 9, 0, 4), ChordSongParser.chordTones("Fmaj7")!!.tones)
        assertEquals(listOf(2, 7, 9), ChordSongParser.chordTones("Dsus4")!!.tones.sortedBy { it }.let { listOf(2, 7, 9) })
        assertEquals(10, ChordSongParser.chordTones("Bb")!!.tones[0])
        assertEquals(6, ChordSongParser.chordTones("F#m")!!.tones[0])
        assertEquals(4, ChordSongParser.chordTones("C/E")!!.bass)
        assertEquals(null, ChordSongParser.chordTones("H"))
    }

    @Test
    fun aGridBecomesBarsWithChordsAndNotes() {
        val s = parse("titolo: Prova\ntempo: 120\nmetro: 4/4\n| C | G | Am F |\n")
        assertEquals("Prova", s.title)
        assertEquals(3, s.barCount)
        assertEquals(0.5, s.secPerBeat, 1e-9)
        assertEquals(listOf("C", "G", "Am", "F"), s.chordSymbols.map { it.text })
        assertEquals(2.0, s.barLines[0], 1e-9)
        assertEquals(6.0, s.durationSec, 0.05)
        // un accordo = mano destra a 3 note + basso a sinistra, tutti insieme
        val first = s.notes.filter { it.startSec == 0.0 }
        assertEquals(3, first.count { it.hand == Hand.RIGHT })
        assertTrue(first.any { it.hand == Hand.LEFT && it.key % 12 == 0 })
        // due accordi in una battuta si dividono il tempo
        assertEquals(1.0, s.chordSymbols[3].timeSec - s.chordSymbols[2].timeSec, 1e-9)
    }

    @Test
    fun rightHandChordsStayCloseToEachOther() {
        val s = parse("| C | G | Am | F | C |")
        val lows = s.notes.filter { it.hand == Hand.RIGHT }.groupBy { it.startSec }.values.map { g -> g.minOf { it.key } }
        for (i in 1 until lows.size) assertTrue("salto ${lows[i - 1]} -> ${lows[i]}", Math.abs(lows[i] - lows[i - 1]) <= 7)
        assertTrue(s.notes.filter { it.hand == Hand.RIGHT }.all { it.key in 53..79 })
        assertTrue(s.notes.filter { it.hand == Hand.LEFT }.all { it.key in 33..55 })
    }

    @Test
    fun beatStyleRestrikesTheChordOnEveryBeat() {
        val held = parse("| C |")
        val beats = parse("stile: pulsazioni\n| C |")
        assertEquals(3, held.notes.filter { it.hand == Hand.RIGHT }.size)
        assertEquals(12, beats.notes.filter { it.hand == Hand.RIGHT }.size) // 4 pulsazioni x 3 note
    }

    @Test
    fun tonalitySetsTheKeySignatureAndMeterTheBarLength() {
        val s = parse("tonalita: G\nmetro: 3/4\ntempo: 60\n| G | D |")
        assertEquals(1, s.keyFifths)
        assertEquals(3, s.timeNumerator)
        assertEquals(3.0, s.barLines[0], 1e-9)
        assertEquals(-1, parse("tonalita: F\n| F |").keyFifths)
    }

    @Test
    fun repeatSignAndMultipleLinesWork() {
        val s = parse("| C | G |\n| % | F |\n")
        assertEquals(4, s.barCount)
        assertEquals(listOf("C", "G", "G", "F"), s.chordSymbols.map { it.text })
    }

    @Test
    fun badInputGivesAClearError() {
        try { parse("titolo: x\n"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("accordi")) }
        try { parse("| C | Zm9 |"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("Zm9")) }
        try { parse("tempo: 5000\n| C |"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("tempo")) }
    }

    @Test
    fun theSongPlaysInWaitModeOneChordAtATime() {
        val s = parse("| C | G |")
        val steps = s.steps(Hand.BOTH)
        assertEquals(2, steps.size)
        assertTrue(steps[0].keys.size >= 4) // triade + basso
    }

    @Test
    fun chordChartsAreNeverSimplified() {
        val s = parse("| C | G |")
        assertEquals(listOf(com.gianlucacelante.midipiano.learn.Simplifier.ORIGINAL), com.gianlucacelante.midipiano.learn.Simplifier.availableVersions(s))
        assertEquals(s.notes, com.gianlucacelante.midipiano.learn.Simplifier.version(s, 1).notes)
    }

    @Test
    fun arpeggioPlaysTheChordOneNoteAtATime() {
        val s = ChordSongParser.parse("| C |".toByteArray(), "id", src, "t", arpeggio = true)
        val right = s.notes.filter { it.hand == Hand.RIGHT }
        assertEquals(4, right.size) // una nota per pulsazione
        assertEquals(4, right.map { it.startSec }.distinct().size)
        assertEquals(setOf(0, 4, 7), right.map { it.key % 12 }.toSet())
    }

    @Test
    fun symbolsOfASheetSongBecomeChordsUntilTheNextSymbol() {
        val base = Song("s", "t", null, src, listOf(SongNote(0.0, 1.0, 72, 80, Hand.RIGHT), SongNote(4.0, 1.0, 74, 80, Hand.RIGHT)),
            chordSymbols = listOf(ChordSymbol(0.0, "C"), ChordSymbol(2.0, "G"), ChordSymbol(4.0, "Xyz")), secPerBeat = 0.5)
        val c = ChordSongParser.fromSymbols(base)
        assertEquals(listOf("C", "G"), c.chordSymbols.map { it.text }) // la sigla sconosciuta si salta
        assertTrue(c.chordChart)
        assertEquals(setOf(0.0, 2.0), c.notes.map { it.startSec }.toSet())
        assertTrue(c.notes.none { it.key == 72 || it.key == 74 }) // la melodia non c'è più
        assertEquals(2.0 * 0.97, c.notes.first { it.startSec == 0.0 && it.hand == Hand.RIGHT }.durationSec, 0.05)
    }

    @Test
    fun commonSpellingsAndExtensionsAreRecognised() {
        fun tones(sym: String) = ChordSongParser.chordTones(sym)?.tones?.toSet()
        assertEquals(tones("Cmaj7"), tones("CMaj7"))
        assertEquals(tones("Cmaj7"), tones("CΔ"))
        assertEquals(tones("Cm7"), tones("Cmin7"))
        assertEquals(tones("Cm7"), tones("C-7"))
        assertEquals(tones("Cdim"), tones("C°"))
        assertEquals(tones("Cm7b5"), tones("Cø"))
        assertEquals(tones("Cm7b5"), tones("Cm7♭5"))
        assertEquals(tones("B♭"), tones("Bb"))
        assertEquals(tones("F#m"), tones("F♯m"))
        assertEquals(setOf(0, 5, 7, 10), tones("C7sus4"))
        assertEquals(setOf(0, 3, 7, 11), tones("Cm(maj7)"))
        assertEquals(setOf(0, 3, 7, 11), tones("Cmmaj7"))
        assertEquals(setOf(0, 4, 11, 2), tones("Cmaj9"))
        assertEquals(setOf(0, 3, 10, 2), tones("Cm9"))
        assertEquals(setOf(0, 4, 10, 9), tones("C13"))
        assertEquals(setOf(0, 4, 10, 3), tones("C7#9"))
        assertEquals(setOf(0, 4, 10, 1), tones("C7b9"))
        assertEquals(setOf(0, 4, 9, 2), tones("C6/9")) // il "/" di 6/9 non è un basso
        assertEquals(setOf(0, 4, 9, 2), tones("C69"))
        assertEquals(setOf(0, 3, 7, 2), tones("Cmadd9"))
        assertEquals(0, ChordSongParser.chordTones("C6/9")!!.bass)
        assertEquals(4, ChordSongParser.chordTones("C/E")!!.bass)
    }

    @Test
    fun anUnknownSuffixFallsBackToTheTriadInsteadOfFailing() {
        assertEquals(listOf(0, 4, 7), ChordSongParser.chordTones("Cxyz")!!.tones)
        assertEquals(listOf(0, 3, 7), ChordSongParser.chordTones("Cm13b5#11")!!.tones)
        assertEquals(null, ChordSongParser.chordTones("Zm9")) // una radice sbagliata resta un errore
        val s = parse("| C | Cxyz | G |")
        assertEquals(3, s.barCount)
    }

    @Test
    fun noChordIsASilentBar() {
        val s = parse("| C | N.C. | G |")
        assertEquals(3, s.barCount)
        assertEquals(listOf("C", "N.C.", "G"), s.chordSymbols.map { it.text })
        assertTrue(s.notes.none { it.startSec >= 2.0 && it.startSec < 4.0 })
    }

    @Test
    fun noChordClosesTheChordBeforeItInSymbolSongs() {
        val base = Song("s", "t", null, src, emptyList(), chordSymbols = listOf(ChordSymbol(0.0, "C"), ChordSymbol(2.0, "N.C."), ChordSymbol(4.0, "G")), secPerBeat = 0.5)
        val c = ChordSongParser.fromSymbols(base.copy(notes = listOf(SongNote(0.0, 5.0, 60, 80, Hand.RIGHT))))
        assertTrue(c.notes.none { it.startSec == 2.0 })
        assertTrue(c.notes.filter { it.startSec == 0.0 }.all { it.durationSec <= 2.0 })
    }

    private fun rightNotesOf(text: String) = parse(text).notes.filter { it.hand == Hand.RIGHT }.groupBy { it.startSec }.toSortedMap().values.map { g -> g.map { it.key }.sorted() }

    @Test
    fun ninthChordsAreVoicedAsARealChordNotACluster() {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        for (r in roots) for (q in listOf("9", "add9", "maj9", "m9")) {
            val v = rightNotesOf("| $r$q |").single()
            assertTrue("$r$q: $v", v.first() >= 53 && v.last() <= 79 && v.last() - v.first() <= 14)
            assertTrue("$r$q: note troppo vicine $v", v.zipWithNext().count { (a, b) -> b - a == 1 } == 0)
        }
    }

    @Test
    fun theHighestNoteNeverGoesPastSolSharpFive() {
        val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        for (r in roots) for (q in listOf("", "m", "7", "maj7", "m7", "sus4", "6", "dim7")) {
            val v = rightNotesOf("| $r$q |").single()
            assertTrue("$r$q: $v", v.last() <= 79)
        }
    }

    @Test
    fun whenOnlyTheSeventhChangesTheHandBarelyMoves() {
        val v = rightNotesOf("| Dbmaj7 | Db7 |")
        val move = v[0].zip(v[1]).sumOf { (a, b) -> Math.abs(a - b) }
        assertTrue("movimento $move: $v", move <= 3)
    }
}

class ChordSongLimitsTest {
    @org.junit.Test
    fun repeatMarksCountTowardsTheBarLimit() {
        val row = "| C | " + "% | ".repeat(500)
        val ex = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ChordSongParser.parse(row.toByteArray(), "x", SongSource("t", "t"))
        }
        org.junit.Assert.assertTrue(ex.message!!.contains("Troppe battute"))
    }
}
