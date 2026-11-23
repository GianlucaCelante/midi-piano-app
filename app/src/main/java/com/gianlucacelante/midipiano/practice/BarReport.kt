package com.gianlucacelante.midipiano.practice

/** Esito di una battuta a fine brano: note attese, errori (mancate o sbagliate) e un voto da 0 a 1. */
data class BarStat(val bar: Int, val notes: Int, val errors: Int) {
    val quality: Double get() = if (notes == 0) 1.0 else (1.0 - errors.toDouble() / maxOf(notes, errors)).coerceIn(0.0, 1.0)
}

/** Mappa degli errori per battuta e scelta del tratto da ripassare. Logica pura. */
object BarReport {
    fun fromCounts(barCount: Int, notesPerBar: Map<Int, Int>, errorsPerBar: Map<Int, Int>): List<BarStat> =
        (1..barCount).map { BarStat(it, notesPerBar[it] ?: 0, errorsPerBar[it] ?: 0) }

    /**
     * Il tratto da ripassare: la finestra di due battute consecutive con più errori (a parità, la prima).
     * Null se non ci sono errori. Il loop dell'app è un intervallo continuo, quindi si sceglie la coppia peggiore.
     */
    fun reviewRange(stats: List<BarStat>): IntRange? {
        if (stats.none { it.errors > 0 }) return null
        if (stats.size == 1) return 1..1
        var best = 0
        var bestSum = -1
        for (i in 0 until stats.size - 1) {
            val sum = stats[i].errors + stats[i + 1].errors
            if (sum > bestSum) { bestSum = sum; best = i }
        }
        return stats[best].bar..stats[best + 1].bar
    }
}
