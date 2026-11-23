package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidiFileParserTest {
    private val src = SongSource("test", "test")

    private fun vlq(v: Int): List<Int> {
        val out = ArrayList<Int>()
        var x = v
        out.add(x and 0x7F)
        x = x shr 7
        while (x > 0) {
            out.add(0, (x and 0x7F) or 0x80)
            x = x shr 7
        }
        return out
    }

    private fun track(vararg events: List<Int>): List<Int> {
        val body = events.flatMap { it } + listOf(0x00, 0xFF, 0x2F, 0x00)
        return listOf('M'.code, 'T'.code, 'r'.code, 'k'.code) + int32(body.size) + body
    }

    private fun int32(v: Int) = listOf(v ushr 24, (v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)

    private fun smf(ppq: Int, vararg tracks: List<Int>): ByteArray {
        val header = listOf('M'.code, 'T'.code, 'h'.code, 'd'.code) + int32(6) +
            listOf(0, 1, 0, tracks.size, ppq shr 8, ppq and 0xFF)
        return (header + tracks.flatMap { it }).map { it.toByte() }.toByteArray()
    }

    private fun noteOn(delta: Int, key: Int, vel: Int = 80, ch: Int = 0) = vlq(delta) + listOf(0x90 or ch, key, vel)
    private fun noteOff(delta: Int, key: Int, ch: Int = 0) = vlq(delta) + listOf(0x80 or ch, key, 0)
    private fun tempo(delta: Int, us: Int) = vlq(delta) + listOf(0xFF, 0x51, 3, us shr 16, (us shr 8) and 0xFF, us and 0xFF)

    private fun keySig(delta: Int, sf: Int) = vlq(delta) + listOf(0xFF, 0x59, 2, sf and 0xFF, 0)
    private fun timeSig(delta: Int, nn: Int, dd: Int) = vlq(delta) + listOf(0xFF, 0x58, 4, nn, dd, 24, 8)

    private fun parse(b: ByteArray) = MidiFileParser.parse(b, "id", "titolo", null, src)

    @Test
    fun beatsComeFromTicks() {
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(96, 60), noteOn(0, 62), noteOff(192, 62), noteOn(0, 64), noteOff(48, 64))))
        assertEquals(listOf(1.0, 2.0, 0.5), s.notes.map { it.beats })
    }

    @Test
    fun staccatoNotesAreNotatedWithTheFullValue() {
        // suonate per 20 tick (quasi niente) ma una ogni quarto (96 tick)
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(20, 60), noteOn(76, 62), noteOff(20, 62), noteOn(76, 64), noteOff(20, 64))))
        // l'ultima nota non ha un attacco successivo: vale la durata suonata (20 tick = 0,21 quarti -> 0,25)
        assertEquals(listOf(1.0, 1.0, 0.25), s.notes.map { it.beats })
    }

    @Test
    fun heldNoteKeepsItsLengthEvenWithAnotherOnsetInTheSameHand() {
        // Do4 tenuta 2 quarti; nello stesso rigo un Re4 attacca dopo 1 quarto
        val s = parse(smf(96, track(noteOn(0, 60), noteOn(96, 62), noteOff(96, 60), noteOff(96, 62))))
        assertEquals(2.0, s.notes.first { it.key == 60 }.beats, 1e-9)
    }

    @Test
    fun readsKeyAndTimeSignature() {
        val s = parse(smf(96, track(keySig(0, 0xFE), timeSig(0, 3, 2), noteOn(0, 60), noteOff(96, 60))))
        assertEquals(-2, s.keyFifths)
        assertEquals(3, s.timeNumerator)
        assertEquals(4, s.timeDenominator)
    }

    @Test
    fun initialTempoGivesSecondsPerBeat() {
        assertEquals(0.5, parse(smf(96, track(noteOn(0, 60), noteOff(96, 60)))).secPerBeat, 1e-9)
        val slow = parse(smf(96, track(tempo(0, 1_000_000), noteOn(0, 60), noteOff(96, 60))))
        assertEquals(1.0, slow.secPerBeat, 1e-9)
        assertEquals(4.0, slow.barDurationSec, 1e-9) // 4/4 a 60 bpm: 4 quarti da 1 s
    }

    @Test
    fun barHelpersMapTimesToBars() {
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(960, 60)))) // stanghette a 2 e 4 s, durata 5 s
        assertEquals(3, s.barCount)
        assertEquals(0.0, s.barStartSec(1), 1e-9)
        assertEquals(2.0, s.barStartSec(2), 1e-9)
        assertEquals(2.0, s.barEndSec(1), 1e-9)
        assertEquals(1, s.barAt(1.9)); assertEquals(2, s.barAt(2.0)); assertEquals(3, s.barAt(4.5))
    }

    @Test
    fun defaultsToCMajorAndFourFour() {
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(96, 60))))
        assertEquals(0, s.keyFifths)
        assertEquals(4, s.timeNumerator)
        assertEquals(4, s.timeDenominator)
    }

    @Test
    fun barLinesFollowTheTimeSignatureAndTempo() {
        // 4/4 a 120 bpm: un quarto = 0,5 s, una battuta = 2 s. 960 tick = 10 quarti = 5 s -> stanghette a 2 e 4
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(960, 60))))
        assertEquals(listOf(2.0, 4.0), s.barLines)
    }

    @Test
    fun barLinesInThreeFour() {
        // 3/4 a 120 bpm: una battuta = 3 quarti = 1,5 s; 768 tick = 8 quarti = 4 s -> stanghette a 1,5 e 3,0
        val s = parse(smf(96, track(timeSig(0, 3, 2), noteOn(0, 60), noteOff(768, 60))))
        assertEquals(listOf(1.5, 3.0), s.barLines)
    }

    @Test
    fun defaultTempoIs120Bpm() {
        // 96 tick per quarto, 120 bpm => 0,5 s per quarto
        val s = parse(smf(96, track(noteOn(0, 60), noteOff(96, 60), noteOn(0, 62), noteOff(96, 62))))
        assertEquals(2, s.notes.size)
        assertEquals(0.0, s.notes[0].startSec, 1e-9)
        assertEquals(0.5, s.notes[0].durationSec, 1e-9)
        assertEquals(0.5, s.notes[1].startSec, 1e-9)
    }

    @Test
    fun tempoChangeAffectsLaterNotes() {
        // Primo quarto a 120 bpm (0,5 s), poi 60 bpm (1 s per quarto)
        val s = parse(
            smf(
                96,
                track(
                    noteOn(0, 60), noteOff(96, 60),
                    tempo(0, 1_000_000),
                    noteOn(0, 62), noteOff(96, 62),
                ),
            ),
        )
        assertEquals(0.5, s.notes[1].startSec, 1e-9)
        assertEquals(1.0, s.notes[1].durationSec, 1e-9)
    }

    @Test
    fun noteOnWithZeroVelocityEndsTheNote() {
        val s = parse(smf(96, track(noteOn(0, 60), noteOn(192, 60, vel = 0))))
        assertEquals(1.0, s.notes.single().durationSec, 1e-9)
    }

    @Test
    fun twoTracksSplitHands() {
        val right = track(noteOn(0, 72), noteOff(96, 72))
        val left = track(noteOn(0, 48), noteOff(96, 48))
        val s = parse(smf(96, right, left))
        assertEquals(Hand.RIGHT, s.notes.first { it.key == 72 }.hand)
        assertEquals(Hand.LEFT, s.notes.first { it.key == 48 }.hand)
    }

    @Test
    fun singleTrackSplitsAtMiddleC() {
        val s = parse(smf(96, track(noteOn(0, 59), noteOff(10, 59), noteOn(0, 60), noteOff(10, 60))))
        assertEquals(Hand.LEFT, s.notes.first { it.key == 59 }.hand)
        assertEquals(Hand.RIGHT, s.notes.first { it.key == 60 }.hand)
    }

    @Test
    fun drumChannelIsIgnored() {
        val s = parse(smf(96, track(noteOn(0, 36, ch = 9), noteOff(10, 36, ch = 9), noteOn(0, 60), noteOff(10, 60))))
        assertEquals(listOf(60), s.notes.map { it.key })
    }

    @Test
    fun stepsGroupSimultaneousNotesAndFilterByHand() {
        val right = track(noteOn(0, 72), noteOff(96, 72))
        val left = track(noteOn(0, 48), noteOff(96, 48))
        val s = parse(smf(96, right, left))
        assertEquals(1, s.steps(Hand.BOTH).size)
        assertEquals(setOf(48, 72), s.steps(Hand.BOTH).single().keys)
        assertEquals(setOf(72), s.steps(Hand.RIGHT).single().keys)
    }

    @Test
    fun truncatedTrackIsReportedAsMidiFileException() {
        val full = smf(96, track(noteOn(0, 60), noteOff(96, 60)))
        val cut = full.copyOf(full.size - 6) // la lunghezza dichiarata supera i byte reali
        assertThrows(MidiFileException::class.java) { parse(cut) }
    }

    @Test
    fun hugeDeclaredTrackLengthDoesNotOverflow() {
        val bad = smf(96, track(noteOn(0, 60), noteOff(96, 60))).also {
            // sovrascrive la lunghezza della traccia (offset 18..21) con 0x7FFFFFF0
            it[18] = 0x7F; it[19] = 0xFF.toByte(); it[20] = 0xFF.toByte(); it[21] = 0xF0.toByte()
        }
        assertThrows(MidiFileException::class.java) { parse(bad) }
    }

    @Test
    fun zeroDivisionIsRejected() {
        assertThrows(MidiFileException::class.java) { parse(smf(0, track(noteOn(0, 60), noteOff(10, 60)))) }
    }

    @Test
    fun rejectsNonMidiAndEmptyFiles() {
        assertThrows(MidiFileException::class.java) { parse("niente".toByteArray()) }
        assertThrows(MidiFileException::class.java) { parse(smf(96, track(tempo(0, 500_000)))) }
    }
}
