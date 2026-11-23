package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidiMeterTest {
    private val ppq = 480

    private fun note(tick: Int, key: Int, dur: Int = 480) = MidiFileParser.RawNote(tick.toLong(), (tick + dur).toLong(), key, 80, 0)

    @Test
    fun aShortLeadInBeforeTheFirstDownbeatIsDetectedAsPickup() {
        // 4/4: un levare di un quarto (tick 0), poi note lunghe e basse sul primo movimento di ogni battuta, a partire da tick 480
        val notes = ArrayList<MidiFileParser.RawNote>()
        notes += note(0, 67, 480)
        for (bar in 0 until 6) {
            val t = 480 + bar * 1920
            notes += note(t, 48, 1920); notes += note(t, 64, 960); notes += note(t + 960, 62, 960)
        }
        assertEquals(480L, MidiFileParser.detectPickupTicks(notes, ppq, 1920L))
    }

    @Test
    fun musicStartingOnTheDownbeatIsNotShifted() {
        val notes = ArrayList<MidiFileParser.RawNote>()
        for (bar in 0 until 6) {
            val t = bar * 1920
            notes += note(t, 48, 1920); notes += note(t, 64, 960); notes += note(t + 960, 62, 960)
        }
        assertEquals(0L, MidiFileParser.detectPickupTicks(notes, ppq, 1920L))
    }

    private fun midi(tracks: ByteArray): ByteArray = tracks

    // File minimo: 4/4 per due battute, poi 3/4 per due battute; una nota per battuta.
    private fun meterChangeFile(): ByteArray {
        fun vl(v: Int): ByteArray = when { v < 128 -> byteArrayOf(v.toByte()); else -> byteArrayOf(((v shr 7) or 0x80).toByte(), (v and 0x7F).toByte()) }
        val ev = java.io.ByteArrayOutputStream()
        ev.write(byteArrayOf(0, 0xFF.toByte(), 0x58, 4, 4, 2, 24, 8)) // 4/4 a tick 0
        ev.write(byteArrayOf(0, 0xFF.toByte(), 0x51, 3, 0x07, 0xA1.toByte(), 0x20)) // 120 bpm
        for (bar in 0 until 2) { // quattro quarti = 1920 tick
            ev.write(vl(0)); ev.write(byteArrayOf(0x90.toByte(), 60, 80)); ev.write(vl(1920)); ev.write(byteArrayOf(0x80.toByte(), 60, 0))
        }
        ev.write(vl(0)); ev.write(byteArrayOf(0xFF.toByte(), 0x58, 4, 3, 2, 24, 8)) // 3/4 a tick 3840
        for (bar in 0 until 2) { // tre quarti = 1440 tick
            ev.write(vl(0)); ev.write(byteArrayOf(0x90.toByte(), 62, 80)); ev.write(vl(1440)); ev.write(byteArrayOf(0x80.toByte(), 62, 0))
        }
        ev.write(byteArrayOf(0, 0xFF.toByte(), 0x2F, 0))
        val trk = ev.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        out.write("MThd".toByteArray()); out.write(byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, (ppq shr 8).toByte(), (ppq and 0xFF).toByte()))
        out.write("MTrk".toByteArray()); out.write(byteArrayOf((trk.size shr 24).toByte(), (trk.size shr 16).toByte(), (trk.size shr 8).toByte(), trk.size.toByte()))
        out.write(trk)
        return out.toByteArray()
    }

    @Test
    fun aMeterChangeStartsANewBarAndIsRecorded() {
        val song = MidiFileParser.parse(meterChangeFile(), "m", "m", null, SongSource("t", "t"))
        assertEquals(4 to 4, song.timeSignatureAt(0.0))
        assertEquals(3 to 4, song.timeSignatureAt(5.0))
        assertEquals(1, song.timeChanges.size)
        assertEquals(4.0, song.timeChanges[0].timeSec, 1e-6) // 3840 tick a 120 bpm = 4 s
        // stanghette: 2 s, 4 s (cambio), 5,5 s
        assertEquals(listOf(2.0, 4.0, 5.5), song.barLines.map { Math.round(it * 100) / 100.0 }.take(3))
    }

    @Test
    fun theBeatGridFollowsTheMeterChange() {
        val song = MidiFileParser.parse(meterChangeFile(), "m", "m", null, SongSource("t", "t"))
        val g = song.beatGrid
        // 4/4: movimenti a 0, 0,5, 1, 1,5, poi 2... ; 3/4 dopo i 4 s: 4, 4,5, 5, poi 5,5...
        assertTrue(g.any { Math.abs(it.timeSec - 4.5) < 1e-6 })
        val accents = g.filter { it.accent }.map { it.timeSec }
        assertTrue("accenti $accents", accents.containsAll(listOf(0.0, 2.0, 4.0, 5.5)))
        assertTrue(g.none { Math.abs(it.timeSec - 5.0) < 1e-6 && it.accent })
    }

    @Test
    fun bundledMidiSongsStillHaveSaneBarLines() {
        val dir = File("src/main/assets/songs")
        for (f in dir.listFiles { x -> x.extension == "mid" }.orEmpty()) {
            val s = MidiFileParser.parse(f.readBytes(), "x", "x", null, SongSource("t", "t"))
            assertTrue("${f.name}: stanghette ordinate", s.barLines.zipWithNext().all { (a, b) -> b > a })
            assertTrue("${f.name}: beatGrid non vuota", s.beatGrid.isNotEmpty())
            println("PICKUP ${f.name.take(36)} bar1=${"%.2f".format(s.barLines.firstOrNull() ?: -1.0)} barDur=${"%.2f".format(s.barDurationSec)} meter=${s.timeNumerator}/${s.timeDenominator} changes=${s.timeChanges.size}/${s.keyChanges.size}")
        }
    }
}

class BeatPositionTest {
    private fun song(num: Int = 4, den: Int = 4) = Song(
        "s", "t", null, SongSource("t", "t"), listOf(SongNote(0.0, 0.3, 60, 80, Hand.RIGHT), SongNote(5.9, 0.3, 62, 80, Hand.RIGHT)),
        barLines = listOf(2.0, 4.0), timeNumerator = num, timeDenominator = den, secPerBeat = 0.5,
    )

    @Test
    fun theBeatPositionCountsWithinTheBar() {
        val s = song()
        assertEquals(1 to 4, s.beatPositionAt(0.1))
        assertEquals(2 to 4, s.beatPositionAt(0.6))
        assertEquals(4 to 4, s.beatPositionAt(1.9))
        assertEquals(1 to 4, s.beatPositionAt(2.05)) // nuova battuta
    }

    @Test
    fun compoundMetersCountTheDottedQuarter() {
        val s = Song("s", "t", null, SongSource("t", "t"), listOf(SongNote(0.0, 0.3, 60, 80, Hand.RIGHT), SongNote(2.9, 0.3, 62, 80, Hand.RIGHT)), barLines = listOf(1.5, 3.0), timeNumerator = 6, timeDenominator = 8, secPerBeat = 0.5)
        assertEquals(1 to 2, s.beatPositionAt(0.1))
        assertEquals(2 to 2, s.beatPositionAt(0.8))
    }

    @Test
    fun beforeTheFirstBeatThereIsNoPosition() {
        assertEquals(null, song().beatPositionAt(-1.0))
    }
}
