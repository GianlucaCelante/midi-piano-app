package com.gianlucacelante.midipiano.practice

/** Divisione del brano in sezioni di battute, per il menu "Parti" e per scegliere dove ripetere. */
object Sections {
    fun of(barCount: Int, perSection: Int = 4): List<IntRange> {
        if (barCount <= 1) return listOf(1..1)
        val size = perSection.coerceAtLeast(1)
        return (1..barCount step size).map { start -> start..minOf(start + size - 1, barCount) }
    }

    fun label(range: IntRange): String =
        if (range.first == range.last) "Battuta ${range.first}" else "Battute ${range.first}–${range.last}"
}
