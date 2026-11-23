package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.TakeFiles
import com.gianlucacelante.midipiano.practice.TakeData
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

class SongRunsTest {
    @Test
    fun runsKeepTheLastTenAndTheBestAccuracy() {
        val p = Progress()
        for (i in 1..12) p.recordRun("s", i / 20.0, listOf(i % 5 + 1))
        assertEquals(10, p.runsOf("s").size)
        assertEquals(0.6, p.runsOf("s").last(), 1e-9)
        assertEquals(0.6, p.bestAccuracy("s")!!, 1e-9)
        assertEquals(listOf(3), p.weakBarsOf("s")) // quelle dell'ultimo giro (12 % 5 + 1 = 3)
        assertNull(p.bestAccuracy("altro"))
    }

    @Test
    fun runsSurviveSerialization() {
        val p = Progress()
        p.recordRun("s", 0.8, listOf(4, 7)); p.recordRun("s", 0.9, listOf(2))
        val b = Progress.fromJson(p.toJson())
        assertEquals(listOf(0.8, 0.9), b.runsOf("s"))
        assertEquals(listOf(2), b.weakBarsOf("s"))
        assertEquals(setOf("s"), b.songsWithRuns())
    }
}

class MinutesChartTest {
    private val day = LocalDate.of(2026, 11, 22)

    @Test
    fun lastDaysAreReturnedOldestFirstWithZerosForMissingDays() {
        val p = Progress()
        p.recordPractice(600, day); p.recordPractice(120, day.minusDays(2))
        val chart = Stats.minutesByDay(p, day, 5)
        assertEquals(5, chart.size)
        assertEquals(day.minusDays(4), chart.first().first)
        assertEquals(day, chart.last().first)
        assertEquals(listOf(0, 0, 2, 0, 10), chart.map { it.second })
    }
}

class AchievementsTest {
    private fun input(
        exercises: Int = 0, threeStars: Int = 0, songs: Int = 0, streak: Int = 0, minutes: Int = 0, courses: Int = 0, dailyDays: Int = 0, takes: Int = 0, runs: Int = 0,
    ) = Achievements.Input(exercises, threeStars, songs, streak, minutes, courses, dailyDays, takes, runs)

    @Test
    fun nothingIsUnlockedAtTheStart() {
        assertTrue(Achievements.compute(input()).none { it.unlocked })
    }

    @Test
    fun milestonesUnlockAtTheirThresholds() {
        val a = Achievements.compute(input(exercises = 10, threeStars = 1, songs = 5, streak = 7, minutes = 100, courses = 1, dailyDays = 5, takes = 1, runs = 10)).associateBy { it.id }
        for (id in listOf("first-exercise", "ten-exercises", "first-three-stars", "five-songs", "streak-7", "hundred-minutes", "first-course", "daily-5", "first-take", "ten-runs")) {
            assertTrue("$id", a.getValue(id).unlocked)
        }
        assertFalse(a.getValue("streak-30").unlocked)
        assertFalse(a.getValue("twenty-five-exercises").unlocked)
    }

    @Test
    fun progressTowardsATargetIsReported() {
        val t = Achievements.compute(input(exercises = 4)).first { it.id == "ten-exercises" }
        assertEquals(4, t.current); assertEquals(10, t.target); assertFalse(t.unlocked)
    }

    @Test
    fun idsAreUnique() {
        val ids = Achievements.compute(input()).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size >= 12)
    }
}

class TakeFilesTest {
    private fun data(title: String, whenMs: Long, n: Int = 3) = TakeData(title, whenMs, 0.9, (0 until n).map { SongNote(it * 0.5, 0.4, 60 + it, 80, Hand.RIGHT) })

    private fun dir() = Files.createTempDirectory("takes").toFile()

    @Test
    fun aSavedTakeComesBackIdentical() {
        val f = TakeFiles(dir())
        val name = f.save(data("Greensleeves", 1_700_000_000_000))
        val back = f.load(name)!!
        assertEquals("Greensleeves", back.title)
        assertEquals(0.9, back.accuracy!!, 1e-9)
        assertEquals(listOf(60, 61, 62), back.notes.map { it.key })
        assertEquals(1.0, back.notes[2].startSec, 1e-9)
        assertEquals(80, back.notes[0].velocity)
    }

    @Test
    fun theListIsNewestFirstAndOldOnesArePruned() {
        val f = TakeFiles(dir(), max = 3)
        for (i in 1..5) f.save(data("T$i", i * 1000L))
        val list = f.list()
        assertEquals(listOf("T5", "T4", "T3"), list.map { it.title })
        assertEquals(3, list.size)
    }

    @Test
    fun deleteRemovesTheFileAndBrokenFilesAreIgnored() {
        val d = dir()
        val f = TakeFiles(d)
        val name = f.save(data("A", 1000))
        java.io.File(d, "rotto.json").writeText("non è json")
        assertEquals(1, f.list().size)
        f.delete(name)
        assertEquals(0, f.list().size)
        assertNull(f.load("non-esiste.json"))
    }

    @Test
    fun metadataKnowsNotesAndDuration() {
        val f = TakeFiles(dir())
        f.save(data("A", 1000, n = 4))
        val m = f.list().first()
        assertEquals(4, m.noteCount)
        assertNotNull(m.name)
        assertEquals(2.0, m.durationSec, 0.5)
    }
}
