package com.gianlucacelante.midipiano.midi

/** Evento MIDI di canale, ridotto a quello che serve all'app. */
sealed interface MidiEvent {
    val channel: Int

    data class NoteOn(override val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    data class NoteOff(override val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    /** Controllo continuo: 64 = pedale di risonanza (valore ≥ 64 = premuto). */
    data class Control(override val channel: Int, val controller: Int, val value: Int) : MidiEvent
}

/**
 * Parser di flusso MIDI 1.0 (byte grezzi). Gestisce running status, più eventi nello stesso
 * buffer, messaggi real-time intercalati e sysex (ignorati). Un'istanza per porta: ricorda
 * lo stato tra una chiamata e l'altra.
 */
class MidiParser {
    private var status = 0
    private val data = IntArray(2)
    private var dataCount = 0
    private var inSysex = false

    fun parse(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset): List<MidiEvent> {
        val events = ArrayList<MidiEvent>(2)
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b >= 0xF8 -> Unit // real-time: non interrompe nulla
                b == 0xF0 -> {
                    inSysex = true
                    status = 0
                }
                b == 0xF7 -> inSysex = false
                b >= 0x80 -> {
                    inSysex = false
                    status = if (b >= 0xF0) 0 else b // system common: nessun running status
                    dataCount = 0
                }
                inSysex || status == 0 -> Unit
                else -> {
                    data[dataCount++] = b
                    if (dataCount == dataBytesFor(status)) {
                        toEvent(status, data[0], data[1])?.let(events::add)
                        dataCount = 0 // running status: status resta valido
                    }
                }
            }
        }
        return events
    }

    private fun dataBytesFor(status: Int): Int = when (status and 0xF0) {
        0xC0, 0xD0 -> 1
        else -> 2
    }

    private fun toEvent(status: Int, d1: Int, d2: Int): MidiEvent? {
        val channel = status and 0x0F
        return when (status and 0xF0) {
            0x90 -> if (d2 == 0) MidiEvent.NoteOff(channel, d1, 0) else MidiEvent.NoteOn(channel, d1, d2)
            0x80 -> MidiEvent.NoteOff(channel, d1, d2)
            0xB0 -> MidiEvent.Control(channel, d1, d2)
            else -> null
        }
    }
}
