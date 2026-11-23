package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode

/** Stelle (1–3) assegnate a un esercizio concluso. */
object Awards {
    fun stars(mode: StudyMode, speed: Double, accuracy: Double, mistakes: Int): Int = when (mode) {
        StudyMode.WAIT -> when {
            mistakes == 0 -> 3
            mistakes <= 2 -> 2
            else -> 1
        }
        else -> {
            val base = when {
                accuracy >= 0.95 -> 3
                accuracy >= 0.85 -> 2
                else -> 1
            }
            if (speed < 0.99) minOf(base, 2) else base // a velocità ridotta non si prende il massimo
        }
    }
}
