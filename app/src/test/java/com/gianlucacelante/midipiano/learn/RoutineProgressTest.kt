package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** La routine quotidiana: cosa è stato fatto oggi, giorni completati e serie consecutiva. */
class RoutineProgressTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun exercisesDoneTodayAreRememberedPerDay() {
        val p = Progress()
        p.markRoutineExercise(today, "a"); p.markRoutineExercise(today, "b"); p.markRoutineExercise(today.minusDays(1), "c")
        assertEquals(setOf("a", "b"), p.routineExercisesDone(today))
        assertEquals(setOf("c"), p.routineExercisesDone(today.minusDays(1)))
        assertEquals(emptySet<String>(), p.routineExercisesDone(today.plusDays(1)))
    }

    @Test fun completedDaysAndStreak() {
        val p = Progress()
        for (d in 0..2) p.markRoutineComplete(today.minusDays(d.toLong()))
        p.markRoutineComplete(today.minusDays(4)) // buco: la serie si ferma
        assertTrue(p.routineCompleted(today))
        assertEquals(3, p.routineStreak(today))
        assertEquals(4, p.routineDaysDone())
    }

    @Test fun streakSurvivesUntilTheEndOfTheDay() {
        val p = Progress()
        p.markRoutineComplete(today.minusDays(1)); p.markRoutineComplete(today.minusDays(2))
        assertFalse(p.routineCompleted(today))
        assertEquals(2, p.routineStreak(today)) // oggi non è ancora fatta ma la serie di ieri non si perde
        assertEquals(0, Progress().routineStreak(today))
    }

    @Test fun routineDataSurvivesSaveAndLoad() {
        val p = Progress()
        p.markRoutineExercise(today, "x"); p.markRoutineComplete(today)
        val back = Progress.fromJson(p.toJson())
        assertEquals(setOf("x"), back.routineExercisesDone(today))
        assertTrue(back.routineCompleted(today))
    }

    @Test fun oldExerciseMarksAreDroppedButCompletedDaysStay() {
        val p = Progress()
        val old = LocalDate.now().minusDays(200)
        p.markRoutineExercise(old, "x"); p.markRoutineComplete(old)
        val back = Progress.fromJson(p.toJson())
        assertEquals(emptySet<String>(), back.routineExercisesDone(old))
        assertTrue(back.routineCompleted(old))
    }
}
