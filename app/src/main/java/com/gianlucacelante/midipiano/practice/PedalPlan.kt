package com.gianlucacelante.midipiano.practice

import kotlin.math.abs

/** Un cambio di pedale richiesto da un esercizio: a quale istante (secondi del brano) e se va premuto o alzato. */
data class PedalEvent(val timeSec: Double, val down: Boolean)

/** I cambi di pedale di un esercizio e il loro giudizio. Logica pura. */
class PedalPlan(events: List<PedalEvent>) {
    val events: List<PedalEvent> = events.sortedBy { it.timeSec }

    /** Il pedale deve essere giù (true) o su (false) all'istante [timeSec]. */
    fun stateAt(timeSec: Double): Boolean = events.lastOrNull { it.timeSec <= timeSec }?.down ?: false

    class Result(val matched: Int, val expected: Int, val extras: Int) {
        /** 1.0 se tutti i cambi sono giusti e non ce ne sono di inutili. */
        val score: Double get() = if (expected == 0) 1.0 else matched / (expected + extras * 0.5)
        val passed: Boolean get() = score >= PASS
        val text: String get() = if (passed) "Pedale: $matched cambi giusti su $expected." else "Pedale: $matched cambi giusti su $expected" + (if (extras > 0) ", $extras di troppo" else "") + ". Premilo dopo l'accordo e alzalo al cambio."
    }

    /** [actual]: i cambi fatti, come (istante, giù). Ogni cambio richiesto vale se ne esiste uno uguale entro [tolSec]. */
    fun judge(actual: List<Pair<Double, Boolean>>, tolSec: Double): Result {
        val used = BooleanArray(actual.size)
        var matched = 0
        for (e in events) {
            var best = -1
            for ((i, a) in actual.withIndex()) {
                if (used[i] || a.second != e.down || abs(a.first - e.timeSec) > tolSec) continue
                if (best < 0 || abs(a.first - e.timeSec) < abs(actual[best].first - e.timeSec)) best = i
            }
            if (best >= 0) { used[best] = true; matched++ }
        }
        return Result(matched, events.size, used.count { !it })
    }

    companion object { const val PASS = 0.7 }
}
