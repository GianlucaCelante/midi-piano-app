package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationLayoutTest {
    private fun note(key: Int, hand: Hand = Hand.RIGHT, beats: Double = 1.0, diatonic: Int? = null, alter: Int = 0, t: Double = 0.0) =
        SongNote(t, beats * 0.5, key, 80, hand, beats, diatonic, alter)

    private fun song(vararg notes: SongNote, fifths: Int = 0) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), keyFifths = fifths)

    private fun layout(n: SongNote, fifths: Int = 0) = NotationLayout.layout(song(n, fifths = fifths), Hand.BOTH).single()

    @Test
    fun trebleClefPositionsFromTheBottomLine() {
        assertEquals(0, layout(note(64)).position) // Mi4: prima linea
        assertEquals(2, layout(note(67)).position) // Sol4: seconda linea
        assertEquals(8, layout(note(77)).position) // Fa5: quinta linea
        assertEquals(-2, layout(note(60)).position) // Do4: primo taglio sotto
        assertEquals(10, layout(note(81)).position) // La5: primo taglio sopra
    }

    @Test
    fun bassClefPositionsFromTheBottomLine() {
        val g2 = layout(note(43, Hand.LEFT))
        assertEquals(Clef.BASS, g2.clef)
        assertEquals(0, g2.position) // Sol2: prima linea del basso
        assertEquals(8, layout(note(57, Hand.LEFT)).position) // La3: quinta linea
        assertEquals(10, layout(note(60, Hand.LEFT)).position) // Do4 in chiave di basso: taglio sopra
    }

    @Test
    fun handDecidesTheStaff() {
        assertEquals(Clef.TREBLE, layout(note(60, Hand.RIGHT)).clef)
        assertEquals(Clef.BASS, layout(note(60, Hand.LEFT)).clef)
    }

    @Test
    fun ledgerLinesAreOnlyOutsideTheStaffOnEvenPositions() {
        assertEquals(listOf(-2), layout(note(60)).ledgerPositions) // Do4
        assertEquals(listOf(-2, -4), layout(note(57)).ledgerPositions) // La3
        assertEquals(listOf(10), layout(note(81)).ledgerPositions) // La5
        assertTrue(layout(note(67)).ledgerPositions.isEmpty())
        assertTrue(layout(note(62)).ledgerPositions.isEmpty()) // Re4 sta nello spazio sotto la prima linea: nessun taglio
        assertEquals(listOf(-2, -4, -6), layout(note(53)).ledgerPositions) // Fa3
    }

    @Test
    fun accidentalsAreRelativeToTheKeySignature() {
        assertEquals(Accidental.SHARP, layout(note(66)).accidental) // Fa# in Do maggiore
        assertNull(layout(note(66), fifths = 1).accidental) // Fa# in Sol maggiore: già in armatura
        assertEquals(Accidental.NATURAL, layout(note(65), fifths = 1).accidental) // Fa naturale in Sol maggiore
        assertNull(layout(note(70), fifths = -1).accidental) // Sib in Fa maggiore
        assertNull(layout(note(60)).accidental)
    }

    @Test
    fun flatKeysSpellBlackKeysWithFlats() {
        assertNull(layout(note(70), fifths = -2).accidental) // Sib in Sib maggiore: già in armatura
        // 61 in una tonalità a bemolli si scrive Reb: stessa posizione di Re4, con il bemolle
        val reb = layout(note(61), fifths = -4)
        assertEquals(layout(note(62)).position, reb.position)
        assertNull(reb.accidental) // Reb è in armatura con 4 bemolli
    }

    @Test
    fun explicitSpellingFromMusicXmlWins() {
        // 61 scritto come Reb4: diatonico di Re4 = 4*7+1 = 29, alterazione -1
        val n = layout(note(61, diatonic = 29, alter = -1))
        assertEquals(Accidental.FLAT, n.accidental)
        assertEquals(layout(note(62)).position, n.position)
    }

    @Test
    fun stemDirectionFollowsTheMiddleLine() {
        assertTrue(layout(note(64)).stemUp) // sotto la linea centrale
        assertFalse(layout(note(81)).stemUp) // sopra
        assertFalse(layout(note(71)).stemUp) // Si4 = linea centrale: gambo in giù
    }

    @Test
    fun durationsDecideHeadStemAndFlags() {
        val whole = layout(note(60, beats = 4.0))
        assertFalse(whole.filled); assertFalse(whole.hasStem)
        val half = layout(note(60, beats = 2.0))
        assertFalse(half.filled); assertTrue(half.hasStem); assertEquals(0, half.flags)
        val quarter = layout(note(60, beats = 1.0))
        assertTrue(quarter.filled); assertEquals(0, quarter.flags)
        assertEquals(1, layout(note(60, beats = 0.5)).flags)
        assertEquals(2, layout(note(60, beats = 0.25)).flags)
        assertEquals(3, layout(note(60, beats = 0.125)).flags)
    }

    @Test
    fun dottedValuesAreFlaggedAndFlagsFollowTheUndottedValue() {
        val dottedQuarter = layout(note(60, beats = 1.5))
        assertTrue(dottedQuarter.dotted); assertEquals(0, dottedQuarter.flags)
        val dottedEighth = layout(note(60, beats = 0.75))
        assertTrue(dottedEighth.dotted); assertEquals(1, dottedEighth.flags); assertTrue(dottedEighth.filled)
        val dottedSixteenth = layout(note(60, beats = 0.375))
        assertTrue(dottedSixteenth.dotted); assertEquals(2, dottedSixteenth.flags)
        val dottedHalf = layout(note(60, beats = 3.0))
        assertTrue(dottedHalf.dotted); assertFalse(dottedHalf.filled); assertTrue(dottedHalf.hasStem)
        assertFalse(layout(note(60, beats = 1.0)).dotted)
        assertFalse(layout(note(60, beats = 0.5)).dotted)
        assertFalse(layout(note(60, beats = 4.0)).dotted)
    }

    @Test
    fun dottedValuesRoundToTheNearestNotatedHead() {
        assertFalse(layout(note(60, beats = 3.0)).filled) // minima col punto
        assertTrue(layout(note(60, beats = 1.5)).filled) // semiminima col punto
    }

    @Test
    fun handFilterRemovesTheOtherHand() {
        val s = song(note(72, Hand.RIGHT), note(48, Hand.LEFT))
        assertEquals(listOf(48), NotationLayout.layout(s, Hand.LEFT).map { it.key })
        assertEquals(2, NotationLayout.layout(s, Hand.BOTH).size)
    }

    @Test
    fun adjacentSecondsInAChordAreSideBySide() {
        // Do4 e Re4 insieme nella stessa mano: la più alta si sposta a destra
        val s = song(note(60), note(62))
        val l = NotationLayout.layout(s, Hand.BOTH).associateBy { it.key }
        assertFalse(l.getValue(60).shifted)
        assertTrue(l.getValue(62).shifted)
    }

    @Test
    fun nonAdjacentOrDifferentTimeNotesAreNotShifted() {
        val chord = NotationLayout.layout(song(note(60), note(64)), Hand.BOTH)
        assertTrue(chord.none { it.shifted }) // Do-Mi: una terza
        val sequence = NotationLayout.layout(song(note(60, t = 0.0), note(62, t = 1.0)), Hand.BOTH)
        assertTrue(sequence.none { it.shifted })
        val hands = NotationLayout.layout(song(note(60, Hand.RIGHT), note(62, Hand.LEFT)), Hand.BOTH)
        assertTrue(hands.none { it.shifted }) // righi diversi
    }

    @Test
    fun threeAdjacentNotesAlternateSides() {
        val l = NotationLayout.layout(song(note(60), note(62), note(64)), Hand.BOTH).associateBy { it.key }
        assertFalse(l.getValue(60).shifted)
        assertTrue(l.getValue(62).shifted)
        assertFalse(l.getValue(64).shifted) // Mi4 dista una seconda da Re4 ma questo è già a destra: torna a sinistra
    }

    @Test
    fun noteStateFollowsTheCurrentStep() {
        val n = layout(note(60, t = 2.0))
        assertEquals(NoteState.UPCOMING, NotationLayout.stateOf(n, currentTimeSec = 1.0, expectedKeys = setOf(60)))
        assertEquals(NoteState.CURRENT, NotationLayout.stateOf(n, 2.0, setOf(60)))
        assertEquals(NoteState.PLAYED, NotationLayout.stateOf(n, 2.0, emptySet())) // già premuta in questo passo
        assertEquals(NoteState.PLAYED, NotationLayout.stateOf(n, 3.0, setOf(60)))
    }

    @Test
    fun keySignatureSymbolsFollowTheCircleOfFifths() {
        assertEquals(listOf(Accidental.SHARP, Accidental.SHARP), NotationLayout.keySignature(2, Clef.TREBLE).map { it.accidental })
        assertEquals(listOf(8, 5), NotationLayout.keySignature(2, Clef.TREBLE).map { it.position }) // Fa# su quinta linea, Do# nel terzo spazio
        assertEquals(listOf(4), NotationLayout.keySignature(-1, Clef.TREBLE).map { it.position }) // Sib sulla linea centrale
        assertTrue(NotationLayout.keySignature(0, Clef.BASS).isEmpty())
    }
}
