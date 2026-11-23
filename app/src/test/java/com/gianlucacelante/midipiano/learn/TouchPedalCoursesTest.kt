package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.Touch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TouchPedalCoursesTest {
    private val dir = File("src/main/assets/courses")
    private fun exercises() = Courses.parse(File(dir, "index.json").readText()) { File(dir, it).readText() }.flatMap { it.exercises }

    @Test fun touchAndPedalExercisesAreReadFromTheCourses() {
        val ex = exercises()
        assertEquals(setOf(Touch.Goal.TARGET, Touch.Goal.EVEN, Touch.Goal.DIM), ex.mapNotNull { it.touch }.toSet())
        val pedal = ex.filter { it.pedal != null }
        assertEquals(2, pedal.size)
        for (p in pedal) {
            // il pedale si alterna giù/su e cade dentro la durata dell'esercizio
            val evs = p.pedal!!.events
            assertTrue(evs.zipWithNext().all { (a, b) -> a.down != b.down })
            assertTrue(evs.last().timeSec <= p.song.durationSec)
        }
    }

    @Test fun everyTouchTargetExerciseHasWrittenForces() {
        for (e in exercises().filter { it.touch == Touch.Goal.TARGET }) {
            val v = e.song.notes.map { it.velocity }
            assertNotNull(e.id); assertTrue("${e.id}: nessuna dinamica scritta", v.isNotEmpty())
        }
    }
}
