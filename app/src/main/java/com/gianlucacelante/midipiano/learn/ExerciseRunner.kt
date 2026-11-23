package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand

/** Un passo di un esercizio: come suonare (mano, modalità, velocità), quante volte, e cosa dire. */
data class LearnStep(val hand: Hand, val mode: StudyMode, val speed: Double, val loops: Int, val text: String)

sealed interface RunOutcome {
    data class NextRun(val runsLeft: Int) : RunOutcome // giro riuscito, ne servono altri
    data class Retry(val runsLeft: Int) : RunOutcome // giro non riuscito: si riprova
    data class StepAdvanced(val newIndex: Int) : RunOutcome
    data class Done(val stars: Int) : RunOutcome
}

/** Fa avanzare un esercizio passo dopo passo in base ai giri riusciti. Logica pura. */
class ExerciseRunner(private val steps: List<LearnStep>) {
    var stepIndex: Int = 0
        private set
    private var runs = 0
    var isDone: Boolean = steps.isEmpty()
        private set

    val currentStep: LearnStep? get() = steps.getOrNull(stepIndex)
    val runsInStep: Int get() = runs
    val totalSteps: Int get() = steps.size

    fun onRunFinished(passed: Boolean, stars: Int): RunOutcome {
        val required = currentStep?.loops?.coerceAtLeast(1) ?: 1
        if (!passed) return RunOutcome.Retry(required - runs)
        runs++
        if (runs < required) return RunOutcome.NextRun(required - runs)
        if (stepIndex == steps.lastIndex) {
            isDone = true
            return RunOutcome.Done(stars.coerceIn(1, 3))
        }
        stepIndex++
        runs = 0
        return RunOutcome.StepAdvanced(stepIndex)
    }
}
