package com.gianlucacelante.midipiano.notation

import kotlin.math.pow

/**
 * Spaziatura orizzontale di uno spartito, in "spazi del rigo", ricavata dalle misure su Flowkey:
 * la distanza fra due note dipende dal VALORE della nota (non dai secondi) con una legge concava
 * (ogni raddoppio del valore dà ~1,5 volte lo spazio: corcheia ≈ 4,7, semiminima ≈ 7,1, minima ≈ 10,6),
 * e dopo ogni stanghetta c'è un respiro di [BAR_PAD] spazi prima della prima nota.
 *
 * - [xAt]: posizione di una nota (o di un istante) sul foglio, lineare nel tempo fra due note.
 * - [scrollAt]: posizione di scorrimento = [xAt] ammorbidita con una media mobile di [SMOOTH_SEC]: la velocità
 *   varia in modo graduale invece di saltare a ogni nota. Una nota resta al cursore con scarto di pochi pixel.
 * - [barX]: posizione della stanghetta che apre la battuta.
 */
class SpacingMap private constructor(
    private val times: DoubleArray,
    private val xs: DoubleArray,
    private val barStarts: DoubleArray,
    val hasEnd: Boolean = false,
    private val barExtra: Map<Double, Double> = emptyMap(), // spazio in più dopo certe stanghette (cambio di metro o armatura), per istante
) {
    private fun extraAt(barStartSec: Double): Double {
        if (barExtra.isEmpty()) return 0.0
        for ((t, e) in barExtra) if (kotlin.math.abs(t - barStartSec) < 1e-6) return e
        return 0.0
    }

    /** Posizione della stanghetta finale (solo se la mappa ha il punto finale). */
    val endX: Double get() = xs.lastOrNull() ?: 0.0

    private val integral = DoubleArray(times.size) // integrale di xAt da times[0] a times[i]

    init {
        for (i in 1 until times.size) {
            val dt = times[i] - times[i - 1]
            integral[i] = integral[i - 1] + (xs[i - 1] + xs[i]) / 2.0 * dt
        }
    }

    fun xAt(timeSec: Double): Double = when {
        times.isEmpty() -> timeSec * BASE_PER_SEC
        timeSec <= times.first() -> xs.first() + (timeSec - times.first()) * BASE_PER_SEC
        timeSec >= times.last() -> xs.last() + (timeSec - times.last()) * BASE_PER_SEC
        else -> {
            val i = lastIndexAtOrBefore(times, timeSec)
            val f = (timeSec - times[i]) / (times[i + 1] - times[i])
            xs[i] + f * (xs[i + 1] - xs[i])
        }
    }

    fun timeAt(x: Double): Double = when {
        times.isEmpty() -> x / BASE_PER_SEC
        x <= xs.first() -> times.first() + (x - xs.first()) / BASE_PER_SEC
        x >= xs.last() -> times.last() + (x - xs.last()) / BASE_PER_SEC
        else -> {
            val i = lastIndexAtOrBefore(xs, x)
            val f = (x - xs[i]) / (xs[i + 1] - xs[i])
            times[i] + f * (times[i + 1] - times[i])
        }
    }

    /** Integrale di [xAt] da times[0] fino a [t] (esatto: la mappa è lineare a tratti). */
    private fun cumulative(t: Double): Double {
        if (times.isEmpty()) return 0.5 * BASE_PER_SEC * t * t
        val first = times.first()
        val last = times.last()
        return when {
            t <= first -> xs.first() * (t - first) + 0.5 * BASE_PER_SEC * (t - first) * (t - first)
            t >= last -> integral.last() + xs.last() * (t - last) + 0.5 * BASE_PER_SEC * (t - last) * (t - last)
            else -> {
                val i = lastIndexAtOrBefore(times, t)
                val dt = t - times[i]
                val slope = (xs[i + 1] - xs[i]) / (times[i + 1] - times[i])
                integral[i] + xs[i] * dt + 0.5 * slope * dt * dt
            }
        }
    }

    /** Posizione di scorrimento al tempo [timeSec]: media di [xAt] su una finestra di [SMOOTH_SEC] centrata. */
    fun scrollAt(timeSec: Double): Double {
        val h = SMOOTH_SEC / 2.0
        return (cumulative(timeSec + h) - cumulative(timeSec - h)) / SMOOTH_SEC
    }

    /** x della stanghetta che apre la battuta a [barStartSec]: [BAR_PAD] prima della prima nota che la segue. */
    fun barX(barStartSec: Double): Double {
        if (times.isEmpty()) return barStartSec * BASE_PER_SEC
        if (barStartSec > times.last() + 1e-6) return xs.last() + (barStartSec - times.last()) * BASE_PER_SEC
        var lo = 0
        var hi = times.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] >= barStartSec - 1e-6) hi = mid else lo = mid + 1
        }
        return xs[lo] - BAR_PAD - extraAt(barStartSec)
    }

    private fun lastIndexAtOrBefore(a: DoubleArray, v: Double): Int {
        var lo = 0
        var hi = a.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (a[mid] <= v) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        const val BASE_PER_SEC = 8.0 // fuori dalle note (conto alla rovescia, fine brano): spazi per secondo
        const val MIN_GAP = 3.0 // distanza minima fra due punti consecutivi
        const val EIGHTH_SPACE = 4.7 // spazio dopo una corcheia (misurato su Flowkey)
        const val BAR_PAD = 3.0 // respiro dopo la stanghetta
        const val SMOOTH_SEC = 0.4 // finestra dell'ammorbidimento dello scorrimento
        private const val SAME_POINT_SEC = 0.012
        private const val DOT_EXTRA = 0.9
        private const val ACCIDENTAL_EXTRA = 1.3
        private const val SHIFTED_EXTRA = 1.1

        /** Spazio per una durata di [beats] quarti: legge concava, ×1,5 a ogni raddoppio. */
        fun space(beats: Double): Double = EIGHTH_SPACE * (2.0 * beats.coerceAtLeast(1.0 / 16)).pow(0.585)

        /** [endSec]: se maggiore dell'ultima nota aggiunge un punto finale (la doppia stanghetta), oltre il quale non c'è nulla. */
        fun build(
            chords: List<Chord>, secPerBeat: Double = 0.5, barStarts: List<Double> = emptyList(), endSec: Double = -1.0,
            beatsBetween: ((Double, Double) -> Double)? = null,
            barExtra: Map<Double, Double> = emptyMap(),
        ): SpacingMap {
            if (chords.isEmpty()) return SpacingMap(DoubleArray(0), DoubleArray(0), barStarts.toDoubleArray(), barExtra = barExtra)
            val sorted = chords.sortedBy { it.startSec }
            val pointTimes = ArrayList<Double>()
            val groups = ArrayList<MutableList<Chord>>()
            for (c in sorted) {
                if (pointTimes.isNotEmpty() && c.startSec - pointTimes.last() <= SAME_POINT_SEC) {
                    groups.last().add(c)
                } else {
                    pointTimes.add(c.startSec)
                    groups.add(mutableListOf(c))
                }
            }
            val dotted = groups.map { g -> g.any { c -> c.notes.any { it.dotted } } }
            val before = groups.map { g ->
                g.maxOf { c -> (if (c.notes.any { it.accidental != null }) ACCIDENTAL_EXTRA else 0.0) + (if (c.notes.any { it.shifted }) SHIFTED_EXTRA else 0.0) }
            }
            val times = DoubleArray(pointTimes.size) { pointTimes[it] }
            val xs = DoubleArray(pointTimes.size)
            val spb = if (secPerBeat > 0) secPerBeat else 0.5
            val bars = barStarts.sorted()
            for (i in 1 until pointTimes.size) {
                val beats = beatsBetween?.invoke(times[i - 1], times[i]) ?: ((times[i] - times[i - 1]) / spb)
                val crossedBar = bars.firstOrNull { it > times[i - 1] + 1e-6 && it <= times[i] + 1e-6 }
                val crossesBar = crossedBar != null
                val extra = if (crossedBar != null) barExtra.entries.firstOrNull { kotlin.math.abs(it.key - crossedBar) < 1e-6 }?.value ?: 0.0 else 0.0
                val natural = space(beats) + (if (crossesBar) BAR_PAD else 0.0) + extra
                val needed = MIN_GAP + (if (dotted[i - 1]) DOT_EXTRA else 0.0) + before[i] + (if (crossesBar) BAR_PAD else 0.0) + extra
                xs[i] = xs[i - 1] + maxOf(natural, needed)
            }
            if (endSec > times.last() + 1e-6) { // punto finale: la stanghetta di chiusura sta dopo l'ultima nota, per il suo valore
                val endBeats = beatsBetween?.invoke(times.last(), endSec) ?: ((endSec - times.last()) / spb)
                return SpacingMap(times + endSec, xs + (xs.last() + maxOf(space(endBeats), MIN_GAP)), bars.toDoubleArray(), hasEnd = true, barExtra = barExtra)
            }
            return SpacingMap(times, xs, bars.toDoubleArray(), barExtra = barExtra)
        }
    }
}
