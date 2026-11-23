package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifica con il parser vero che tutti i corsi inclusi si leggano e siano coerenti. */
class BundledCoursesTest {
    private val dir = File("src/main/assets/courses")
    private fun courses() = Courses.parse(File(dir, "index.json").readText()) { File(dir, it).readText() }

    @Test
    fun everyCourseLoadsWithLessonsAndExercises() {
        val all = courses()
        assertEquals(23, all.size) // 3 corsi base + 9 di progressione + "Leggere la musica" + "Pedale e dinamica" + 9 corsi aggiunti
        all.forEach { c ->
            assertTrue("${c.id}: poche lezioni", c.lessons.size in 5..6)
            assertTrue("${c.id}: nessun esercizio", c.exercises.isNotEmpty())
        }
    }

    @Test
    fun slideIdsAreUniqueAcrossAllCourses() {
        val ids = courses().flatMap { c -> c.lessons.flatMap { l -> l.slides.map { it.id } } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyExerciseIsPlayableAndItsStepsMatchTheHandsInTheSong() {
        for (e in courses().flatMap { it.exercises }) {
            assertTrue("${e.id}: senza note", e.song.notes.isNotEmpty())
            assertTrue("${e.id}: durata non valida", e.song.durationSec in 1.0..120.0)
            assertTrue("${e.id}: passi mancanti", e.steps.size in 1..5) // fino a 4 nel JSON, più il passo a velocità normale aggiunto dopo una prova rallentata finale
            for (s in e.steps) {
                if (s.hand != Hand.BOTH) assertTrue("${e.id}: nessuna nota per la mano ${s.hand}", e.song.notes.any { it.hand == s.hand })
                assertTrue("${e.id}: velocità non valida", s.speed in 0.25..1.5)
            }
            assertTrue("${e.id}: battute senza stanghette", e.song.barLines.isNotEmpty() || e.song.durationSec < e.song.barDurationSec * 1.5)
        }
    }

    @Test
    fun nextExerciseFollowsTheCourseOrderAndEndsAtTheLast() {
        val all = courses()
        val ex = all.first().exercises
        assertEquals(ex[1].id, Courses.nextExercise(all, ex[0].id)?.id)
        assertEquals(null, Courses.nextExercise(all, all.last().exercises.last().id))
        assertEquals(null, Courses.nextExercise(all, "inesistente"))
    }

    @Test
    fun firstIncompleteSkipsWhatIsAlreadyDone() {
        val all = courses()
        val ex = all.flatMap { it.exercises }
        assertEquals(ex[0].id, Courses.firstIncomplete(all) { 0 }?.id)
        assertEquals(ex[2].id, Courses.firstIncomplete(all) { if (it == ex[0].id || it == ex[1].id) 3 else 0 }?.id)
        assertEquals(null, Courses.firstIncomplete(all) { 1 })
    }

    @Test
    fun theTotalNumberOfExercisesIsAsExpected() {
        assertEquals(300, courses().sumOf { it.exercises.size })
    }
}
