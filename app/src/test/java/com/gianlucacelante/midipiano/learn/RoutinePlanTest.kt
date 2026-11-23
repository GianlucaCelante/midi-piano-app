package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Quali esercizi della routine fare oggi, da dove ripartire e cosa viene dopo. */
class RoutinePlanTest {
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(SongNote(0.0, 1.0, 60, 80, Hand.RIGHT, beats = 1.0)), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 1.0)
    private fun ex(id: String) = ExerciseSlide(id, id, "", song, listOf(LearnStep(Hand.BOTH, StudyMode.WAIT, 1.0, 1, "")))
    private val course = Course("r", "Routine", "", 1, listOf(Lesson("l", "L", "", (1..5).map { ex("e$it") })))

    @Test fun sizeLimitsTheQueueAndZeroMeansAll() {
        assertEquals(listOf("e1", "e2", "e3"), RoutinePlan.queue(course, 3).map { it.id })
        assertEquals(5, RoutinePlan.queue(course, 0).size)
        assertEquals(5, RoutinePlan.queue(course, 99).size)
        assertEquals(5, RoutinePlan.queue(course, -2).size) // un valore negativo vale «tutti»
    }

    @Test fun nextTodoSkipsWhatIsDoneToday() {
        val q = RoutinePlan.queue(course, 4)
        assertEquals("e1", RoutinePlan.nextTodo(q, emptySet())!!.id)
        assertEquals("e3", RoutinePlan.nextTodo(q, setOf("e1", "e2"))!!.id)
        assertEquals("e1", RoutinePlan.nextTodo(q, setOf("e2", "e3", "e4"))!!.id) // si riprende dal primo mancante
        assertNull(RoutinePlan.nextTodo(q, setOf("e1", "e2", "e3", "e4")))
    }

    @Test fun nextAfterFollowsTheQueueOrderAndSkipsDone() {
        val q = RoutinePlan.queue(course, 4)
        assertEquals("e2", RoutinePlan.nextAfter(q, "e1", emptySet())!!.id)
        assertEquals("e3", RoutinePlan.nextAfter(q, "e1", setOf("e2"))!!.id)
        assertNull(RoutinePlan.nextAfter(q, "e4", emptySet()))
        assertNull(RoutinePlan.nextAfter(q, "sconosciuto", emptySet()))
    }

    @Test fun completeWhenEveryExerciseOfTheQueueIsDone() {
        val q = RoutinePlan.queue(course, 3)
        assertEquals(false, RoutinePlan.isComplete(q, setOf("e1", "e2")))
        assertEquals(true, RoutinePlan.isComplete(q, setOf("e1", "e2", "e3", "extra")))
        assertEquals(false, RoutinePlan.isComplete(emptyList(), emptySet())) // una routine vuota non è «completata»
    }

    @Test fun planWithoutDifficultiesIsTheBaseRoutine() {
        assertEquals(listOf("e1", "e2", "e3", "e4", "e5"), RoutinePlan.planIds(course, 0, emptyList()))
    }

    @Test fun planReplacesTheLastSlotsWithHardExercisesOutsideTheBase() {
        val ids = RoutinePlan.planIds(course, 5, listOf("x1", "e2", "x2", "x3"))
        assertEquals(listOf("e1", "e2", "e3", "x1", "x2"), ids) // al massimo due mirati; e2 è già nella base
    }

    @Test fun smallRoutinesKeepMostlyTheBase() {
        assertEquals(listOf("e1", "e2", "x1"), RoutinePlan.planIds(course, 3, listOf("x1", "x2")))
        assertEquals(listOf("e1"), RoutinePlan.planIds(course, 1, listOf("x1")))
    }
}
