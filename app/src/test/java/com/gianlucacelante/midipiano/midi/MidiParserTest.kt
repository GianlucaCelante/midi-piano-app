package com.gianlucacelante.midipiano.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiParserTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun noteOnAndNoteOff() {
        val p = MidiParser()
        val e = p.parse(bytes(0x90, 60, 100, 0x80, 60, 40))
        assertEquals(listOf(MidiEvent.NoteOn(0, 60, 100), MidiEvent.NoteOff(0, 60, 40)), e)
    }

    @Test
    fun noteOnWithZeroVelocityIsNoteOff() {
        val e = MidiParser().parse(bytes(0x91, 64, 0))
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOff(1, 64, 0)), e)
    }

    @Test
    fun runningStatus() {
        val e = MidiParser().parse(bytes(0x90, 60, 90, 62, 91, 64, 0))
        assertEquals(
            listOf(MidiEvent.NoteOn(0, 60, 90), MidiEvent.NoteOn(0, 62, 91), MidiEvent.NoteOff(0, 64, 0)),
            e,
        )
    }

    @Test
    fun messageSplitAcrossBuffers() {
        val p = MidiParser()
        assertTrue(p.parse(bytes(0x90, 60)).isEmpty())
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), p.parse(bytes(100)))
    }

    @Test
    fun realTimeBytesDoNotBreakMessages() {
        val e = MidiParser().parse(bytes(0x90, 60, 0xF8, 100))
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), e)
    }

    @Test
    fun sysexAndProgramChangesAreIgnored() {
        // il controllo 7 (volume) ora viene riportato; sysex e cambio di programma restano ignorati
        val e = MidiParser().parse(bytes(0xF0, 0x43, 0x10, 0x7F, 0xF7, 0xB0, 7, 100, 0xC0, 5, 0x90, 60, 80))
        assertEquals(listOf<MidiEvent>(MidiEvent.Control(0, 7, 100), MidiEvent.NoteOn(0, 60, 80)), e)
    }

    @Test
    fun honoursOffsetAndCount() {
        val e = MidiParser().parse(bytes(0x00, 0x90, 60, 100, 0x00), offset = 1, count = 3)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), e)
    }

    @Test
    fun controlChangesAreReported() {
        val e = MidiParser().parse(bytes(0xB0, 64, 127, 0xB0, 64, 0, 0xB1, 7, 90))
        assertEquals(listOf<MidiEvent>(MidiEvent.Control(0, 64, 127), MidiEvent.Control(0, 64, 0), MidiEvent.Control(1, 7, 90)), e)
    }
}
