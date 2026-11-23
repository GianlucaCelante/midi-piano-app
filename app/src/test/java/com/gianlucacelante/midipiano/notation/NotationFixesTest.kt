package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationFixesTest {
    private fun song(vararg notes: SongNote, bars: List<Double> = emptyList()) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = bars, secPerBeat = 0.5)

    // ---- pause brevi nei MIDI ----
    @Test
    fun anEighthNoteFollowedByAnEighthRestKeepsTheRest() {
        // suonata esattamente mezzo quarto e la nota dopo attacca dopo un quarto: croma + pausa di croma, non una semiminima
        assertEquals(0.5, NoteValues.notated(playedBeats = 0.5, nextOnsetBeats = 1.0), 1e-9)
        assertEquals(1.0, NoteValues.notated(playedBeats = 1.0, nextOnsetBeats = 2.0), 1e-9) // semiminima + pausa di semiminima
    }

    @Test
    fun aClearlyDetachedNoteStillGetsTheFullValue() {
        assertEquals(1.0, NoteValues.notated(playedBeats = 0.3, nextOnsetBeats = 1.0), 1e-9)
        assertEquals(1.0, NoteValues.notated(playedBeats = 0.7, nextOnsetBeats = 1.0), 1e-9)
    }

    // ---- legato con sovrapposizione ----
    @Test
    fun aSlightlyOverlappingLegatoNoteDoesNotBecomeDotted() {
        assertEquals(1.0, NoteValues.notated(playedBeats = 1.3, nextOnsetBeats = 1.0), 1e-9)
        assertEquals(2.0, NoteValues.notated(playedBeats = 2.0, nextOnsetBeats = 1.0), 1e-9) // una nota davvero tenuta resta lunga
    }

    // ---- accordi sul limite del gruppo ----
    @Test
    fun chordNotesStraddlingTheBucketBoundaryStayTogether() {
        // 0,044 e 0,046 s sono "la stessa pulsazione": non devono finire in due accordi diversi
        fun n(t: Double, key: Int) = StaffNote(key, Clef.TREBLE, t, 0.4, 4, null, 1.0, Hand.RIGHT)
        val chords = Chords.group(listOf(n(0.044, 60), n(0.046, 64), n(0.5, 62)))
        assertEquals(2, chords.size)
        assertEquals(2, chords.first().notes.size)
    }

    // ---- durate non standard (MusicXML) ----
    @Test
    fun nonStandardDurationsAreSplitIntoTiedStandardValues() {
        val s = song(SongNote(0.0, 1.25, 60, 80, Hand.RIGHT, 2.5), bars = listOf(10.0))
        val notes = NotationLayout.layout(s, Hand.BOTH).sortedBy { it.startSec }
        assertEquals(listOf(2.0, 0.5), notes.map { it.beats })
        assertTrue(notes[0].tieNext)
        assertTrue(notes[1].tieFromPrev)
        assertEquals(0.0 + 1.0, notes[1].startSec, 1e-9) // la seconda parte comincia dopo i 2 quarti (1,0 s)
    }

    @Test
    fun standardDurationsAreNeverSplit() {
        for (b in listOf(0.125, 0.25, 0.375, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0)) {
            val s = song(SongNote(0.0, b / 2, 60, 80, Hand.RIGHT, b), bars = listOf(10.0))
            assertEquals("$b", 1, NotationLayout.layout(s, Hand.BOTH).size)
        }
    }

    // ---- pausa finale ----
    @Test
    fun theLastBarOfEachStaffEndsWithItsRestsWhenTheOtherHandPlaysOn() {
        // destra: una nota nella prima battuta; sinistra suona anche nella seconda: la destra deve chiudere con una pausa
        val s = song(
            SongNote(0.0, 0.5, 72, 80, Hand.RIGHT, 1.0),
            SongNote(0.0, 1.9, 48, 80, Hand.LEFT, 4.0),
            SongNote(2.0, 1.9, 48, 80, Hand.LEFT, 4.0),
            bars = listOf(2.0),
        )
        val chords = Chords.group(NotationLayout.layout(s, Hand.BOTH)).filter { it.clef == Clef.TREBLE }
        val rests = Rests.compute(chords, s)
        val lastRestEnd = rests.maxOf { s.secAfter(it.startSec, it.beats) }
        assertEquals(4.0, lastRestEnd, 0.3) // il silenzio fino alla fine della seconda battuta è scritto
    }

    // ---- terzine ----
    @Test
    fun aTripletEighthIsAnEighthWithOneFlagNotASixteenth() {
        val n = StaffNote(60, Clef.TREBLE, 0.0, 0.16, 4, null, 1.0 / 3, Hand.RIGHT)
        assertEquals(1, n.flags)
        assertTrue(n.isTriplet)
        assertTrue(n.filled && n.hasStem)
        assertEquals(false, n.dotted)
        val q = StaffNote(60, Clef.TREBLE, 0.0, 0.33, 4, null, 2.0 / 3, Hand.RIGHT)
        assertEquals(0, q.flags)
        assertTrue(q.filled)
    }

    @Test
    fun midiTripletsAreRecognizedFromTheirSpacing() {
        // tre note a distanza di un terzo di quarto, partendo sul movimento
        assertEquals(1.0 / 3, NoteValues.notated(playedBeats = 0.3, nextOnsetBeats = 1.0 / 3, startBeats = 2.0), 1e-9)
        assertEquals(1.0 / 3, NoteValues.notated(playedBeats = 0.3, nextOnsetBeats = 1.0 / 3, startBeats = 2.0 + 1.0 / 3), 1e-9)
        assertEquals(2.0 / 3, NoteValues.notated(playedBeats = 0.6, nextOnsetBeats = 2.0 / 3, startBeats = 1.0), 1e-9)
        // una croma normale non diventa terzina
        assertEquals(0.5, NoteValues.notated(playedBeats = 0.5, nextOnsetBeats = 0.5, startBeats = 1.0), 1e-9)
        assertEquals(0.25, NoteValues.notated(playedBeats = 0.25, nextOnsetBeats = 0.25, startBeats = 1.25), 1e-9)
    }

    @Test
    fun tripletValuesAreNotSplitIntoTiedNotes() {
        val s = Song("s", "t", null, SongSource("t", "t"),
            listOf(SongNote(0.0, 0.16, 60, 80, Hand.RIGHT, 1.0 / 3), SongNote(1.0 / 6, 0.16, 62, 80, Hand.RIGHT, 1.0 / 3), SongNote(2.0 / 6, 0.16, 64, 80, Hand.RIGHT, 1.0 / 3)),
            barLines = listOf(10.0), secPerBeat = 0.5)
        assertEquals(3, NotationLayout.layout(s, Hand.BOTH).size)
    }
}
