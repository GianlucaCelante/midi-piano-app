package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarReportTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.3, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()

    // quattro battute da 2 s, una nota ogni mezzo secondo (4 per battuta)
    private fun song() = Song("s", "t", null, SongSource("t", "t"),
        (0 until 16).map { n(it * 0.5, 60 + it % 5) }, barLines = listOf(2.0, 4.0, 6.0), secPerBeat = 0.5)

    @Test
    fun statsCountNotesAndErrorsPerBar() {
        val stats = BarReport.fromCounts(barCount = 4, notesPerBar = mapOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), errorsPerBar = mapOf(2 to 3, 4 to 1))
        assertEquals(listOf(0, 3, 0, 1), stats.map { it.errors })
        assertEquals(listOf(4, 4, 4, 4), stats.map { it.notes })
        assertEquals(1.0, stats[0].quality, 1e-9)
        assertTrue(stats[1].quality < stats[3].quality)
    }

    @Test
    fun theReviewLoopCoversTheWorstWindowOfTwoBars() {
        val stats = BarReport.fromCounts(6, (1..6).associateWith { 4 }, mapOf(2 to 1, 3 to 5, 4 to 4, 6 to 2))
        assertEquals(3..4, BarReport.reviewRange(stats)) // 3 e 4 hanno più errori insieme (9)
    }

    @Test
    fun noErrorsMeansNothingToReview() {
        val stats = BarReport.fromCounts(4, (1..4).associateWith { 4 }, emptyMap())
        assertNull(BarReport.reviewRange(stats))
    }

    @Test
    fun theLastBarCanBeReviewedAloneWhenItIsTheWorst() {
        val stats = BarReport.fromCounts(3, (1..3).associateWith { 4 }, mapOf(3 to 4, 1 to 1))
        assertEquals(2..3, BarReport.reviewRange(stats)) // finestra di due battute che finisce nell'ultima
    }

    @Test
    fun theSessionCountsMissedAndWrongNotesByBar() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        // battuta 1: tutto giusto; battuta 2: nessuna nota (4 mancate) e un tasto sbagliato
        for (i in 0 until 4) { val t = i * 0.5; s.tick(sec(t)); s.onNoteOn(60 + i % 5, sec(t)) }
        s.tick(sec(2.5)); s.onNoteOn(90, sec(2.5)) // sbagliata nella battuta 2
        s.tick(sec(7.9)) // il resto passa senza suonare: tutte mancate
        val bars = s.barReport()
        assertEquals(0, bars[0].errors)
        assertTrue("battuta 2: ${bars[1]}", bars[1].errors >= 5) // 4 mancate + 1 sbagliata
        assertEquals(4, bars[1].notes)
    }
}

class BarReportSkippedTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.3, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()
    private fun song() = Song("s", "t", null, SongSource("t", "t"), (0 until 16).map { n(it * 0.5, 60 + it % 5) }, barLines = listOf(2.0, 4.0, 6.0), secPerBeat = 0.5)

    @Test
    fun barsBeforeTheJumpAreNotJudgedNorCountedAsClean() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.seekTo(6.0, 0L) // si salta all'ultima battuta
        s.play(0L)
        s.tick(sec(0.0)); s.tick(sec(2.5))
        val bars = s.barReport()
        assertEquals(listOf(0, 0, 0), bars.take(3).map { it.notes }) // battute saltate: niente note giudicate
        assertEquals(4, bars[3].notes)
    }

    @Test
    fun aFullRunJudgesEveryBar() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        s.tick(sec(0.0))
        assertEquals(listOf(4, 4, 4, 4), s.barReport().map { it.notes })
    }

    @Test
    fun waitModeAfterAFreeSeekAlsoSkipsTheEarlierBars() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.seekTo(4.0, 0L)
        assertEquals(listOf(0, 0, 4, 4), s.barReport().map { it.notes })
    }
}
