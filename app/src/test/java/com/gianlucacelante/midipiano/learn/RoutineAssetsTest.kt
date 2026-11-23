package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Le routine quotidiane incluse: sempre a due mani, esercizi veri e con id propri. */
class RoutineAssetsTest {
    private val dir = File("src/main/assets/routines")
    private fun routines() = Courses.parse(File(dir, "index.json").readText()) { File(dir, it).readText() }

    @Test fun thereIsAtLeastTheBeginnerRoutine() {
        val r = routines()
        assertTrue(r.isNotEmpty())
        assertEquals("routine-base", r.first().id)
        assertTrue("servono almeno 4 esercizi", r.first().exercises.size >= 4)
    }

    @Test fun everyExerciseUsesBothHandsAtEveryStep() {
        for (e in routines().flatMap { it.exercises }) {
            assertTrue("${e.id}: nota per mano", e.song.notes.any { it.hand == Hand.RIGHT } && e.song.notes.any { it.hand == Hand.LEFT })
            assertTrue("${e.id}: tutti i passi a due mani", e.steps.all { it.hand == Hand.BOTH })
            assertTrue("${e.id}: durata", e.song.durationSec in 5.0..90.0)
        }
    }

    @Test fun noTwoHandsPlayTheSameKeyAtTheSameTime() {
        // con lo stesso tasto in due mani nello stesso istante l'accordo non si potrebbe suonare
        for (e in routines().flatMap { it.exercises }) {
            val clash = e.song.notes.groupBy { Math.round(it.startSec * 1000) }.values.any { g -> g.map { it.key }.let { it.size != it.toSet().size } }
            assertTrue("${e.id}: stesso tasto due volte insieme", !clash)
        }
    }

    @Test fun idsDoNotCollideWithTheCourses() {
        val cdir = File("src/main/assets/courses")
        val courseIds = Courses.parse(File(cdir, "index.json").readText()) { File(cdir, it).readText() }.flatMap { it.exercises }.map { it.id }.toSet()
        val ids = routines().flatMap { it.exercises }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in courseIds })
    }

    @Test fun everyRoutineExerciseHasASlowThenNormalSpeedProgression() {
        for (e in routines().flatMap { it.exercises }) {
            assertEquals(StudyModeAlias.WAIT, StudyModeAlias.of(e.steps.first().mode))
            assertEquals(1.0, e.steps.last().speed, 0.0)
        }
    }
}

private object StudyModeAlias {
    val WAIT = com.gianlucacelante.midipiano.practice.StudyMode.WAIT
    fun of(m: com.gianlucacelante.midipiano.practice.StudyMode) = m
}
