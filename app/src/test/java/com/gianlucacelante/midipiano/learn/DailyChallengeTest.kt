package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyChallengeTest {
    private val day = LocalDate.of(2026, 11, 22)
    private val songs = (1..20).map { "song-$it" }

    @Test
    fun theSongOfTheDayIsTheSameForTheSameDayAndIgnoresListOrder() {
        assertEquals(DailyChallenge.songOfDay(day, songs), DailyChallenge.songOfDay(day, songs.reversed()))
    }

    @Test
    fun consecutiveDaysGetDifferentSongsAndAllSongsEventuallyAppear() {
        val picks = (0 until 60).map { DailyChallenge.songOfDay(day.plusDays(it.toLong()), songs)!! }
        for ((a, b) in picks.zipWithNext()) assertNotEquals(a, b)
        assertTrue("copertura ${picks.toSet().size}", picks.toSet().size >= 15)
    }

    @Test
    fun noSongsMeansNoPick() {
        assertNull(DailyChallenge.songOfDay(day, emptyList()))
        assertEquals("only", DailyChallenge.songOfDay(day, listOf("only")))
    }

    @Test
    fun theTrialPrefersExercisesToReviewThenNewOnes() {
        val ex = (1..10).map { "e$it" }
        val stars = mapOf("e1" to 3, "e2" to 3, "e3" to 2, "e4" to 1)
        val pick = DailyChallenge.trialOfDay(day, ex) { stars[it] ?: 0 }
        assertTrue("da ripassare: $pick", pick in setOf("e3", "e4"))
    }

    @Test
    fun withNothingToReviewTheTrialIsOneOfTheNextNewExercises() {
        val ex = (1..10).map { "e$it" }
        val stars = (1..4).associate { "e$it" to 3 }
        val pick = DailyChallenge.trialOfDay(day, ex) { stars[it] ?: 0 }
        assertTrue("nuovo: $pick", pick in setOf("e5", "e6", "e7", "e8", "e9"))
    }

    @Test
    fun whenEverythingIsPerfectAnyExerciseCanComeBack() {
        val ex = (1..4).map { "e$it" }
        assertTrue(DailyChallenge.trialOfDay(day, ex) { 3 } in ex)
        assertNull(DailyChallenge.trialOfDay(day, emptyList()) { 0 })
    }

    @Test
    fun picksAndCompletionPersistAndOldDaysArePruned() {
        val p = Progress()
        p.setDailyPick(day, "song", "greensleeves")
        p.setDailyPick(day, "trial", "e3")
        p.markDailyDone(day, "song")
        p.setDailyPick(day.minusDays(60), "song", "old")
        val back = Progress.fromJson(p.toJson())
        assertEquals("greensleeves", back.dailyPick(day, "song"))
        assertEquals("e3", back.dailyPick(day, "trial"))
        assertTrue(back.isDailyDone(day, "song"))
        assertEquals(false, back.isDailyDone(day, "trial"))
        assertNull(back.dailyPick(day.minusDays(60), "song"))
    }

    @Test
    fun aPickDoesNotChangeOnceSaved() {
        val p = Progress()
        p.setDailyPick(day, "trial", "e3")
        p.setDailyPick(day, "trial", "e9") // già scelta: resta la prima
        assertEquals("e3", p.dailyPick(day, "trial"))
    }
}

class ResumeLessonTest {
    private fun song() = com.gianlucacelante.midipiano.song.Song("s", "t", null, com.gianlucacelante.midipiano.song.SongSource("t", "t"), emptyList())
    private fun ex(id: String) = ExerciseSlide(id, "Esercizio $id", "", song(), emptyList())
    private fun lesson(id: String, vararg ex: String) = Lesson(id, "Lezione $id", "", ex.map { ex(it) })
    private val courses = listOf(
        Course("c1", "Corso 1", "", 1, listOf(lesson("l1", "a1", "a2", "a3"), lesson("l2", "b1", "b2"))),
        Course("c2", "Corso 2", "", 1, listOf(lesson("l1", "c1", "c2"))),
    )

    @Test
    fun resumesAtTheFirstUnfinishedExerciseOfTheLastLesson() {
        val stars = mapOf("a1" to 3, "a3" to 1)
        val r = Courses.resume(courses, "a1") { stars[it] ?: 0 }!!
        assertEquals("a2", r.exercise.id)
        assertEquals("Corso 1", r.course.title)
        assertEquals("l1", r.lesson.id)
        assertEquals(2, r.done)
        assertEquals(3, r.total)
    }

    @Test
    fun whenTheLessonIsFinishedItGoesToTheNextLessonOfTheCourse() {
        val stars = mapOf("a1" to 3, "a2" to 2, "a3" to 3)
        val r = Courses.resume(courses, "a3") { stars[it] ?: 0 }!!
        assertEquals("b1", r.exercise.id)
        assertEquals("l2", r.lesson.id)
        assertEquals(0, r.done)
    }

    @Test
    fun whenTheCourseIsFinishedItGoesToTheNextCourse() {
        val stars = mapOf("a1" to 1, "a2" to 1, "a3" to 1, "b1" to 1, "b2" to 1)
        val r = Courses.resume(courses, "b2") { stars[it] ?: 0 }!!
        assertEquals("c1", r.exercise.id)
        assertEquals("Corso 2", r.course.title)
    }

    @Test
    fun nothingToResumeWithoutHistoryOrWhenEverythingIsDone() {
        assertNull(Courses.resume(courses, null) { 0 })
        assertNull(Courses.resume(courses, "zzz") { 0 }) // esercizio sconosciuto
        assertNull(Courses.resume(courses, "c2") { 3 })
    }

    @Test
    fun anExerciseLeftHalfwayIsTheOneToResume() {
        val stars = mapOf("a1" to 3)
        val r = Courses.resume(courses, "a3") { stars[it] ?: 0 }!! // aperto "a3" e lasciato: si riprende da a3, non da a2
        assertEquals("a3", r.exercise.id)
    }
}
