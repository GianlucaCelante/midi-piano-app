package com.gianlucacelante.midipiano.song

import com.gianlucacelante.midipiano.notation.Chords
import com.gianlucacelante.midipiano.notation.NotationLayout
import com.gianlucacelante.midipiano.notation.Rests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoChangesTest {
    private val src = SongSource("t", "t")
    // 0,5 s al quarto fino a 10 s, poi 1,0 s al quarto
    private val slowing = Song("s", "t", null, src, emptyList(), secPerBeat = 0.5,
        tempoChanges = listOf(TempoChange(0.0, 0.5), TempoChange(10.0, 1.0)))

    @Test
    fun beatsAndSecondsConvertAcrossATempoChange() {
        assertEquals(22.0, slowing.beatsBetween(0.0, 12.0), 1e-9) // 20 quarti + 2 quarti
        assertEquals(12.0, slowing.secAfter(9.0, 4.0), 1e-9)      // 2 quarti veloci + 2 lenti
        assertEquals(0.5, slowing.secPerBeatAt(3.0), 1e-9)
        assertEquals(1.0, slowing.secPerBeatAt(11.0), 1e-9)
    }

    @Test
    fun withoutChangesItIsTheConstantTempo() {
        val s = Song("s", "t", null, src, emptyList(), secPerBeat = 0.4)
        assertEquals(5.0, s.beatsBetween(0.0, 2.0), 1e-9)
        assertEquals(1.6, s.secAfter(0.0, 4.0), 1e-9)
    }

    @Test
    fun noPhantomRestsAfterATempoSlowsDown() {
        // semiminime ogni quarto: a 0,5 s fino a 10 s, poi ogni 1,0 s
        val notes = (0 until 20).map { SongNote(it * 0.5, 0.45, 60, 80, Hand.RIGHT, 1.0) } +
            (0 until 6).map { SongNote(10.0 + it * 1.0, 0.9, 60, 80, Hand.RIGHT, 1.0) }
        val s = Song("s", "t", null, src, notes, secPerBeat = 0.5, tempoChanges = slowing.tempoChanges,
            barLines = (1..10).map { it * 2.0 }.filter { it < 10.0 } + (0..2).map { 10.0 + 4.0 * (it + 1) })
        val chords = Chords.group(NotationLayout.layout(s, Hand.BOTH))
        val rests = Rests.compute(chords, s)
        assertTrue("pause inventate: $rests", rests.isEmpty())
    }

    @Test
    fun aNoteEndingExactlyOnABarlineIsNotSplitAfterATempoChange() {
        // a 1,0 s al quarto una semiminima parte a 13 s e la stanghetta è a 14 s: nessuna legatura
        val n = SongNote(13.0, 0.9, 60, 80, Hand.RIGHT, 1.0)
        val s = Song("s", "t", null, src, listOf(n), secPerBeat = 0.5, tempoChanges = slowing.tempoChanges, barLines = listOf(14.0))
        val staff = NotationLayout.layout(s, Hand.BOTH)
        assertEquals(1, staff.size)
        assertEquals(1.0, staff[0].beats, 1e-9)
    }
}
