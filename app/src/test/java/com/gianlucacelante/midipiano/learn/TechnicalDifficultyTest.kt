package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TechnicalDifficultyTest {
    private fun n(t: Double, key: Int, hand: Hand = Hand.RIGHT, d: Double = 0.4) = SongNote(t, d, key, 80, hand)
    private fun song(vararg notes: SongNote, bars: List<Double> = listOf(2.0, 4.0), fifths: Int = 0) =
        Song("s", "t", null, SongSource("t", "t"), notes.toList(), barLines = bars, keyFifths = fifths, secPerBeat = 0.5)

    // battuta 1: quattro semiminime facili a destra; battuta 2: sedicesimi con salti, accordi e tasti neri
    private val mixed: Song = run {
        val notes = ArrayList<SongNote>()
        val whites = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76)
        for (i in 0 until 4) notes += n(i * 0.5, whites[i], d = 0.45)
        for (i in 0 until 16) notes += n(2.0 + i * 0.125, if (i % 2 == 0) whites[i % 5] else whites[9 - i % 3], d = 0.1)
        for (i in 0 until 4) { notes += n(2.0 + i * 0.5, 48, Hand.LEFT); notes += n(2.0 + i * 0.5, 52, Hand.LEFT); notes += n(2.0 + i * 0.5, 55, Hand.LEFT) }
        song(*notes.toTypedArray())
    }

    @Test
    fun aSlowStepwiseBarIsEasyAndAFastLeapingChordedBarIsHard() {
        val s = TechnicalDifficulty.barScores(mixed)
        assertTrue("battuta 1 ${s[0]} < battuta 2 ${s[1]}", s[0] < 0.8 * s[1])
        assertTrue("facile ${s[0]}", s[0] <= TechnicalDifficulty.EASY_MAX)
        assertTrue("difficile ${s[1]}", s[1] > TechnicalDifficulty.MEDIUM_MAX)
    }

    @Test
    fun theScoreGrowsWithEachDifficultyFactor() {
        fun score(vararg notes: SongNote, fifths: Int = 0) = TechnicalDifficulty.barScores(song(*notes, bars = listOf(2.0), fifths = fifths))[0]
        val base = score(n(0.0, 60), n(0.5, 62), n(1.0, 64), n(1.5, 65))
        assertTrue("più veloce", score(n(0.0, 60, d = 0.1), n(0.125, 62, d = 0.1), n(0.25, 64, d = 0.1), n(0.375, 65, d = 0.1), n(0.5, 67, d = 0.1), n(0.625, 65, d = 0.1)) > base)
        assertTrue("salto grande", score(n(0.0, 60), n(0.5, 72), n(1.0, 60), n(1.5, 74)) > base)
        assertTrue("tasti neri", score(n(0.0, 61), n(0.5, 63), n(1.0, 66), n(1.5, 68)) > base)
        assertTrue("accordo", score(n(0.0, 60), n(0.0, 64), n(0.0, 67), n(0.5, 62), n(0.5, 65), n(0.5, 69), n(1.0, 64), n(1.5, 65)) > base)
        assertTrue("due mani", score(n(0.0, 60), n(0.5, 62), n(1.0, 64), n(1.5, 65), n(0.0, 48, Hand.LEFT), n(0.25, 52, Hand.LEFT), n(0.75, 55, Hand.LEFT), n(1.25, 50, Hand.LEFT)) > base)
    }

    @Test
    fun anEmptyBarHasZeroScoreAndTheMeanIsDefined() {
        val s = song(n(0.0, 60), bars = listOf(2.0, 4.0))
        val sc = TechnicalDifficulty.barScores(s)
        assertEquals(0.0, sc.getOrElse(1) { 0.0 }, 1e-9)
        assertTrue(TechnicalDifficulty.overall(s) >= 0.0)
    }

    @Test
    fun simplifiedVersionsKeepEasyBarsUntouchedAndLowerTheHardOnes() {
        val easy = Simplifier.version(mixed, Simplifier.EASY)
        val bar1Orig = mixed.notes.filter { it.startSec < 2.0 }
        val bar1Easy = easy.notes.filter { it.startSec < 2.0 }
        assertEquals(bar1Orig.map { it.key to it.startSec }, bar1Easy.map { it.key to it.startSec }) // già facile: invariata
        val after = TechnicalDifficulty.barScores(easy)
        assertTrue("dopo: ${after[1]}", after[1] <= TechnicalDifficulty.EASY_MAX * 1.35)
    }

    @Test
    fun theEasyVersionIsNeverHarderThanTheMediumAndTheMediumNeverHarderThanTheOriginal() {
        val dir = File("src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension in listOf("mid", "midi") }.orEmpty()
        for (f in files) {
            val song = com.gianlucacelante.midipiano.song.MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val o = TechnicalDifficulty.overall(song)
            val m = TechnicalDifficulty.overall(Simplifier.version(song, Simplifier.MEDIUM))
            val e = TechnicalDifficulty.overall(Simplifier.version(song, Simplifier.EASY))
            assertTrue("${f.name}: $e <= $m <= $o", e <= m + 1e-9 && m <= o + 1e-9)
        }
    }

    @Test
    fun theEasyVersionOfEveryBundledSongIsPlayableForABeginner() {
        val dir = File("src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension in listOf("mid", "midi") }.orEmpty()
        for (f in files) {
            val song = com.gianlucacelante.midipiano.song.MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val e = Simplifier.version(song, Simplifier.EASY)
            val sc = TechnicalDifficulty.barScores(e).filter { it > 0 }
            if (sc.isEmpty()) continue
            val hardBars = sc.count { it > TechnicalDifficulty.EASY_MAX * 1.5 }
            assertTrue("${f.name}: ${hardBars}/${sc.size} battute ancora difficili", hardBars <= sc.size * 0.20) // le altezze delle note non si cambiano: in un brano come la sonata in Fa minore qualche battuta resta impegnativa
        }
    }
}

class SimplifiedSpansAndUnlockTest {
    @Test
    fun theSimplifiedVersionReportsWhichBarsItChanged() {
        val whites = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76)
        val notes = ArrayList<SongNote>()
        for (i in 0 until 4) notes += SongNote(i * 0.5, 0.45, whites[i], 80, Hand.RIGHT)           // battuta 1: facile
        for (i in 0 until 16) notes += SongNote(2.0 + i * 0.125, 0.1, whites[(i * 3) % 10], 80, Hand.RIGHT) // battuta 2: veloce con salti
        val song = Song("s", "t", null, SongSource("t", "t"), notes, barLines = listOf(2.0, 4.0), secPerBeat = 0.5)
        val easy = Simplifier.version(song, Simplifier.EASY)
        assertTrue("battuta 1 non cambiata", easy.simplifiedSpans.none { it.start < 1.0 })
        assertTrue("battuta 2 cambiata: ${easy.simplifiedSpans}", easy.simplifiedSpans.any { it.start >= 2.0 - 1e-6 })
        assertEquals(emptyList<ClosedFloatingPointRange<Double>>(), Simplifier.version(song, Simplifier.ORIGINAL).simplifiedSpans)
    }

    @Test
    fun songsDoneSurviveSerialization() {
        val p = Progress()
        p.recordSongDone("greensleeves", 1)
        val back = Progress.fromJson(p.toJson())
        assertEquals(setOf("greensleeves:1"), back.songsDoneIds())
    }
}

class DailyGoalTest {
    private val day = java.time.LocalDate.of(2026, 11, 22)

    @Test
    fun practiceSecondsAreCountedPerDay() {
        val p = Progress()
        p.recordPractice(120, day); p.recordPractice(180, day); p.recordPractice(60, day.minusDays(1))
        assertEquals(300L, p.secondsOn(day))
        assertEquals(60L, p.secondsOn(day.minusDays(1)))
        assertEquals(0L, p.secondsOn(day.plusDays(1)))
        assertEquals(360L, p.totalPracticeSeconds)
    }

    @Test
    fun theGoalIsReachedWhenTheMinutesAreDone() {
        val p = Progress()
        p.recordPractice(9 * 60, day)
        assertEquals(false, p.goalReached(day, 10))
        p.recordPractice(60, day)
        assertEquals(true, p.goalReached(day, 10))
    }

    @Test
    fun lastSongAndDaysSurviveSerializationAndOldDaysArePruned() {
        val p = Progress()
        p.lastSongId = "traditional-greensleeves"; p.lastSongVersion = 2
        p.recordPractice(100, day)
        p.recordPractice(100, day.minusDays(200)) // troppo vecchio per tenere i secondi del giorno
        val back = Progress.fromJson(p.toJson())
        assertEquals("traditional-greensleeves", back.lastSongId)
        assertEquals(2, back.lastSongVersion)
        assertEquals(100L, back.secondsOn(day))
        assertEquals(0L, back.secondsOn(day.minusDays(200)))
        assertEquals(200L, back.totalPracticeSeconds)
    }
}
