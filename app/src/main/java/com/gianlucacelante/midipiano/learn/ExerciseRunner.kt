package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand

/** Un passo di un esercizio: come suonare (mano, modalità, velocità), quante volte, e cosa dire. */
data class LearnStep(val hand: Hand, val mode: StudyMode, val speed: Double, val loops: Int, val text: String) {
    /** La prova «a tempo» rallentata è facoltativa: si può saltare senza perdere l'esercizio. */
    val optional: Boolean get() = mode == StudyMode.TEMPO && speed < 0.99
}

sealed interface RunOutcome {
    data class NextRun(val runsLeft: Int) : RunOutcome // giro riuscito, ne servono altri
    data class Retry(val runsLeft: Int) : RunOutcome // giro non riuscito: si riprova
    data class StepAdvanced(val newIndex: Int) : RunOutcome
    data class Done(val stars: Int) : RunOutcome
}

/** In un esercizio, trascinare lo spartito all'indietro (di più di mezzo secondo) ricomincia il giro; in avanti non si salta (si prenderebbero le stelle senza suonare). */
fun seekRestartsExerciseRun(targetSec: Double, currentSec: Double, finished: Boolean): Boolean = !finished && targetSec < currentSec - 0.5

/** Fa avanzare un esercizio passo dopo passo in base ai giri riusciti. Logica pura. */
class ExerciseRunner(private val steps: List<LearnStep>, startIndex: Int = 0) {
    var stepIndex: Int = startStep(startIndex, steps.size)
        private set
    private var runs = 0
    private var lastStars = 1
    var isDone: Boolean = steps.isEmpty()
        private set

    companion object {
        /** Il passo da cui riprendere un esercizio: quello salvato, riportato dentro l'esercizio se nel frattempo è cambiato. */
        fun startStep(saved: Int, stepCount: Int): Int = if (stepCount <= 0) 0 else saved.coerceIn(0, stepCount - 1)
    }

    val currentStep: LearnStep? get() = steps.getOrNull(stepIndex)
    val runsInStep: Int get() = runs
    val totalSteps: Int get() = steps.size

    fun onRunFinished(passed: Boolean, stars: Int): RunOutcome {
        val required = currentStep?.loops?.coerceAtLeast(1) ?: 1
        if (!passed) return RunOutcome.Retry(required - runs)
        runs++
        lastStars = stars.coerceIn(1, 3)
        if (runs < required) return RunOutcome.NextRun(required - runs)
        if (stepIndex == steps.lastIndex) {
            isDone = true
            return RunOutcome.Done(stars.coerceIn(1, 3))
        }
        stepIndex++
        runs = 0
        return RunOutcome.StepAdvanced(stepIndex)
    }

    /**
     * Salta il passo corrente se è facoltativo (la prova rallentata): si va al successivo o, se era l'ultimo, l'esercizio
     * si chiude con le stelle dell'ultimo giro riuscito. Null se il passo non è facoltativo.
     */
    fun skipOptionalStep(): RunOutcome? {
        val step = currentStep ?: return null
        if (!step.optional || isDone) return null
        runs = 0
        if (stepIndex == steps.lastIndex) {
            isDone = true
            return RunOutcome.Done(lastStars)
        }
        stepIndex++
        return RunOutcome.StepAdvanced(stepIndex)
    }
}
