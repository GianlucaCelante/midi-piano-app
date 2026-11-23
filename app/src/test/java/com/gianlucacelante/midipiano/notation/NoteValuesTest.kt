package com.gianlucacelante.midipiano.notation

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteValuesTest {
    @Test
    fun quantizeSnapsToTheNearestNotatedValue() {
        assertEquals(1.0, NoteValues.quantize(1.0), 1e-9)
        assertEquals(1.0, NoteValues.quantize(0.9), 1e-9)
        assertEquals(1.0, NoteValues.quantize(1.1), 1e-9)
        assertEquals(0.5, NoteValues.quantize(0.45), 1e-9)
        assertEquals(0.375, NoteValues.quantize(0.4), 1e-9) // più vicino alla croma puntata che alla croma
        assertEquals(1.5, NoteValues.quantize(1.45), 1e-9)
        assertEquals(2.0, NoteValues.quantize(2.1), 1e-9)
        assertEquals(3.0, NoteValues.quantize(3.2), 1e-9)
    }

    @Test
    fun veryShortAndVeryLongValuesAreClamped() {
        assertEquals(0.125, NoteValues.quantize(0.01), 1e-9)
        assertEquals(4.0, NoteValues.quantize(9.0), 1e-9)
    }

    @Test
    fun staccatoTakesTheDistanceToTheNextOnset() {
        // suonata per 0,2 quarti ma la nota successiva è dopo 1: si scrive una semiminima
        assertEquals(1.0, NoteValues.notated(playedBeats = 0.2, nextOnsetBeats = 1.0), 1e-9)
    }

    @Test
    fun heldNotesKeepTheirPlayedLength() {
        // suonata per 2 quarti mentre un'altra nota attacca dopo 1: resta una minima
        assertEquals(2.0, NoteValues.notated(playedBeats = 2.0, nextOnsetBeats = 1.0), 1e-9)
    }

    @Test
    fun longGapsAreTreatedAsRests() {
        // nota breve seguita da una lunga pausa (oltre 2 quarti): non si allunga
        assertEquals(1.0, NoteValues.notated(playedBeats = 1.0, nextOnsetBeats = 6.0), 1e-9)
        assertEquals(0.5, NoteValues.notated(playedBeats = 0.45, nextOnsetBeats = 3.0), 1e-9)
    }

    @Test
    fun withoutANextNoteUsesThePlayedLength() {
        assertEquals(2.0, NoteValues.notated(playedBeats = 2.0, nextOnsetBeats = null), 1e-9)
    }
}
