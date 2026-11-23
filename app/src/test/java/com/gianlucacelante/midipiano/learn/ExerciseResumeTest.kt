package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I passi superati di un esercizio restano salvati: riaprendolo si riparte da dove si era arrivati. */
class ExerciseResumeTest {
    private fun step(loops: Int = 1, mode: StudyMode = StudyMode.WAIT, speed: Double = 1.0) = LearnStep(Hand.RIGHT, mode, speed, loops, "t")
    private val steps = listOf(step(), step(mode = StudyMode.TEMPO, speed = 0.75), step(mode = StudyMode.TEMPO))

    @Test fun progressRemembersTheStepReachedPerExercise() {
        val p = Progress()
        assertEquals(0, p.stepOf("a/l1/s1"))
        p.setStep("a/l1/s1", 2); p.setStep("b/l1/s1", 1)
        assertEquals(2, p.stepOf("a/l1/s1")); assertEquals(1, p.stepOf("b/l1/s1"))
        val back = Progress.fromJson(p.toJson())
        assertEquals(2, back.stepOf("a/l1/s1")); assertEquals(1, back.stepOf("b/l1/s1"))
    }

    @Test fun finishingAnExerciseClearsItsSavedStep() {
        val p = Progress()
        p.setStep("a", 2)
        p.clearStep("a")
        assertEquals(0, p.stepOf("a"))
        assertFalse(Progress.fromJson(p.toJson()).toJson().contains("\"a\":2"))
    }

    @Test fun stepZeroIsNotStored() {
        val p = Progress()
        p.setStep("a", 0)
        assertFalse(p.toJson().contains("\"steps\":{\"a\""))
    }

    @Test fun savedStepIsClampedToTheExercise() {
        assertEquals(0, ExerciseRunner.startStep(saved = -3, stepCount = 3))
        assertEquals(1, ExerciseRunner.startStep(saved = 1, stepCount = 3))
        assertEquals(2, ExerciseRunner.startStep(saved = 9, stepCount = 3)) // l'esercizio è stato accorciato: l'ultimo passo
        assertEquals(0, ExerciseRunner.startStep(saved = 5, stepCount = 0))
    }

    @Test fun runnerCanStartFromASavedStep() {
        val r = ExerciseRunner(steps, startIndex = 1)
        assertEquals(1, r.stepIndex)
        assertEquals(StudyMode.TEMPO, r.currentStep!!.mode)
        assertEquals(RunOutcome.StepAdvanced(2), r.onRunFinished(true, 3))
        assertTrue(r.onRunFinished(true, 2) is RunOutcome.Done)
    }

    @Test fun runnerWithoutStartStillBeginsAtZero() {
        assertEquals(0, ExerciseRunner(steps).stepIndex)
    }
}
