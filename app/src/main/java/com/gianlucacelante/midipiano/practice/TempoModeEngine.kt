package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Step
import kotlin.math.abs

/** SKIPPED: nota saltata (prima del punto di partenza scelto): non conta né come colpita né come mancata. */
enum class HitState { PENDING, HIT, MISSED, SKIPPED }
enum class HitKind { HIT, WRONG }

data class HitOutcome(val kind: HitKind, val deviationSec: Double?)

/**
 * Valutazione a tempo: ogni nota del brano va suonata entro [toleranceSec] dall'istante previsto
 * (tempo del brano, già scalato dalla velocità). Le note non suonate diventano "mancate" quando la
 * finestra scade; le note fuori finestra o non attese sono "sbagliate". Logica pura.
 */
class TempoModeEngine(steps: List<Step>, var toleranceSec: Double = 0.2) {
    class Expected(val stepIndex: Int, val key: Int, val timeSec: Double, val noteStartSec: Double = timeSec) {
        var state: HitState = HitState.PENDING
            internal set
        var deviationSec: Double = 0.0
            internal set
    }

    val expected: List<Expected> = steps.flatMapIndexed { i, s -> s.notes.map { Expected(i, it.key, s.startSec, it.startSec) } }
        .sortedBy { it.timeSec }

    var wrong: Int = 0
        private set
    var hits: Int = 0
        private set
    var misses: Int = 0
        private set
    private var deviationSum = 0.0
    private var absDeviationSum = 0.0

    /** Note giudicate (colpite o mancate) / note totali giudicabili: 1.0 se non ce ne sono. */
    val accuracy: Double get() = if (hits + misses == 0) 1.0 else hits.toDouble() / (hits + misses)
    val meanDeviationSec: Double get() = if (hits == 0) 0.0 else deviationSum / hits
    val meanAbsDeviationSec: Double get() = if (hits == 0) 0.0 else absDeviationSum / hits

    fun stateOf(stepIndex: Int, key: Int): HitState? =
        expected.firstOrNull { it.stepIndex == stepIndex && it.key == key }?.state

    // Indice (nota, istante al millisecondo) -> atteso: per leggere lo stato di una nota mentre si disegna
    private val byKeyAndTime: Map<Long, Expected> =
        expected.associateBy { it.key * 10_000_000L + Math.round(it.noteStartSec * 1000) }

    fun stateAt(key: Int, timeSec: Double): HitState? = byKeyAndTime[key * 10_000_000L + Math.round(timeSec * 1000)]?.state

    /**
     * Segna come mancate le note la cui finestra è scaduta; restituisce quante nuove. Con [onlyBefore]
     * si giudicano solo le note precedenti a quell'istante (es. alla chiusura di un giro di loop).
     */
    fun onTick(songTimeSec: Double, onlyBefore: Double = Double.POSITIVE_INFINITY): Int {
        var n = 0
        for (e in expected) {
            if (e.timeSec >= onlyBefore) break
            if (e.timeSec + toleranceSec >= songTimeSec) break // ordinate: le successive sono ancora valide
            if (e.state == HitState.PENDING) {
                e.state = HitState.MISSED
                misses++
                n++
            }
        }
        return n
    }

    /** Pressioni un attimo prima della fine del giro: valgono per la nota dopo, applicata quando il giro ricomincia (nota, istante nel giro dopo). */
    private val earlyPresses = ArrayList<Pair<Expected, Double>>()

    /**
     * [loopStart]/[loopEnd]: se si suona in loop, un tasto premuto un attimo prima della fine del giro è la prima nota del giro dopo
     * (l'orologio ricomincia da capo ma la mano è già partita).
     */
    fun onNoteOn(key: Int, songTimeSec: Double, loopStart: Double = Double.NaN, loopEnd: Double = Double.NaN): HitOutcome {
        val match = expected
            .filter { it.state == HitState.PENDING && it.key == key && abs(it.timeSec - songTimeSec) <= toleranceSec }
            .minByOrNull { it.timeSec } // la più vecchia non ancora giudicata: con note ripetute vicine una pressione in ritardo non "ruba" la nota dopo
        if (match == null && !loopStart.isNaN() && !loopEnd.isNaN() && songTimeSec >= loopEnd - toleranceSec && songTimeSec < loopEnd + 1e-9) {
            val virtualTime = songTimeSec - (loopEnd - loopStart) // dove sarebbe nel giro dopo
            val next = expected
                .filter { it.state != HitState.SKIPPED && it.key == key && it.timeSec >= loopStart - 1e-6 && it.timeSec < loopEnd && abs(it.timeSec - virtualTime) <= toleranceSec }
                .minByOrNull { it.timeSec }
            if (next != null) {
                earlyPresses.add(next to virtualTime)
                return HitOutcome(HitKind.HIT, virtualTime - next.timeSec)
            }
        }
        if (match == null) {
            wrong++
            return HitOutcome(HitKind.WRONG, null)
        }
        match.state = HitState.HIT
        match.deviationSec = songTimeSec - match.timeSec
        hits++
        deviationSum += match.deviationSec
        absDeviationSum += abs(match.deviationSec)
        return HitOutcome(HitKind.HIT, match.deviationSec)
    }

    /** Esclude dalla valutazione le note ancora in attesa che precedono [timeSec] (dopo un salto di posizione). */
    fun skipBefore(timeSec: Double) {
        for (e in expected) {
            if (e.timeSec >= timeSec - 1e-6) break // tutto ciò che precede il punto di partenza, anche dentro la finestra di tolleranza
            if (e.state == HitState.PENDING) e.state = HitState.SKIPPED
        }
    }

    /** Riparte da zero per le note nell'intervallo [startSec, endSec) (es. a ogni giro di loop). */
    fun resetRange(startSec: Double, endSec: Double) {
        for (e in expected) {
            if (e.timeSec < startSec || e.timeSec >= endSec) continue
            when (e.state) {
                HitState.HIT -> {
                    hits--
                    deviationSum -= e.deviationSec
                    absDeviationSum -= abs(e.deviationSec)
                }
                HitState.MISSED -> misses--
                HitState.PENDING, HitState.SKIPPED -> Unit
            }
            e.state = HitState.PENDING
        }
        for ((e, v) in earlyPresses) { // le pressioni anticipate del giro appena chiuso valgono per questo
            if (e.state != HitState.PENDING) continue
            e.state = HitState.HIT
            e.deviationSec = v - e.timeSec
            hits++
            deviationSum += e.deviationSec
            absDeviationSum += abs(e.deviationSec)
        }
        earlyPresses.clear()
        wrong = 0
    }
}
