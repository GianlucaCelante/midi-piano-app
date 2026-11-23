package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Song

/** Livello di difficoltà di un brano (1 = molto facile ... 5 = impegnativo), dalla sua difficoltà tecnica media per battuta. */
object Difficulty {
    fun of(song: Song): Int {
        val scores = TechnicalDifficulty.barScores(song).filter { it > 0.0 }
        if (scores.isEmpty()) return 1
        return level(scores.average())
    }

    /** Dal punteggio medio al livello 1-5 (le soglie 1,2 / 2,0 / 3,2 / 5,0 dividono i brani inclusi in cinque fasce). */
    fun level(mean: Double): Int = when {
        mean < 1.2 -> 1
        mean < 2.0 -> 2
        mean < 3.2 -> 3
        mean < 5.0 -> 4
        else -> 5
    }
}
