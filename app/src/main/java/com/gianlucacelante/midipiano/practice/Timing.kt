package com.gianlucacelante.midipiano.practice

import kotlin.math.abs
import kotlin.math.roundToInt

/** Come hai tenuto il tempo: scarto medio dal momento giusto (negativo = in anticipo) e distanza fra le note degli accordi. */
data class TimingSummary(val meanMs: Int, val chordSpreadMs: Int?, val hits: Int) {
    /** Frasi da mostrare nel riepilogo (vuoto se i dati sono troppo pochi). */
    val notes: List<String> get() {
        if (hits < Timing.MIN_HITS) return emptyList()
        val out = ArrayList<String>()
        out += when {
            abs(meanMs) < Timing.STEADY_MS -> "Il tempo è preciso: non anticipi né ritardi."
            meanMs < 0 -> "Tendi ad anticipare di circa ${abs(meanMs)} ms."
            else -> "Tendi a ritardare di circa $meanMs ms."
        }
        chordSpreadMs?.let { out += if (it <= Timing.CHORD_OK_MS) "Gli accordi partono insieme." else "Le note degli accordi partono distanziate di circa $it ms: prova a premerle insieme." }
        return out
    }
}

object Timing {
    const val MIN_HITS = 8
    const val STEADY_MS = 25
    const val CHORD_OK_MS = 45

    fun summarize(meanDeviationSec: Double, hits: Int, chordSpreadSec: Double?): TimingSummary =
        TimingSummary((meanDeviationSec * 1000).roundToInt(), chordSpreadSec?.let { (it * 1000).roundToInt() }, hits)
}
