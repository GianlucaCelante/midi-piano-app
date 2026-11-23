package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.BarReport

/**
 * Tiene il conto dei giri falliti (finiti male o abbandonati) di uno stesso passo di esercizio e dei punti dove si sbaglia di più.
 * Dopo [threshold] giri falliti l'app propone di ripassare le battute deboli. Logica pura.
 */
class StruggleTracker(private val threshold: Int = 3) {
    private var fails = 0
    private val errorsPerBar = HashMap<Int, Int>()

    val active: Boolean get() = fails >= threshold

    fun onFailedRun(errors: Map<Int, Int>) {
        fails++
        for ((bar, n) in errors) errorsPerBar.merge(bar, n, Int::plus)
    }

    /** Un giro riuscito: la difficoltà è superata, si riparte da zero. */
    fun onGoodRun() = reset()

    fun reset() { fails = 0; errorsPerBar.clear() }

    /** Le due battute consecutive con più errori accumulati, se si sta faticando e c'è un punto debole preciso. */
    fun reviewRange(barCount: Int): IntRange? =
        if (!active || errorsPerBar.isEmpty()) null else BarReport.reviewRange(BarReport.fromCounts(barCount, emptyMap(), errorsPerBar))
}
