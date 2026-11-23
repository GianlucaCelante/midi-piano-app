package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Step
import kotlin.math.abs

/** SKIPPED: nota saltata (prima del punto di partenza scelto): non conta né come colpita né come mancata. */
enum class HitState { PENDING, HIT, MISSED, SKIPPED }
enum class HitKind { HIT, WRONG }

/**
 * Esito di una pressione. Se è sbagliata ma il tasto è quello di una nota vicina (entro [TempoModeEngine.NEAR_SEC]) non ancora
 * colpita, [nearMissSec] dice di quanto (istante - nota; negativo = troppo presto) e [nearNoteSec] è l'istante di quella nota.
 */
data class HitOutcome(
    val kind: HitKind, val deviationSec: Double?, val nearMissSec: Double? = null, val nearNoteSec: Double? = null,
    /** Della nota colpita: inizio e durata scritti (secondi del brano), per controllare quanto a lungo la si tiene. */
    val noteStartSec: Double? = null, val noteDurationSec: Double? = null,
    /** Tasto sbagliato premuto subito dopo un altro sbagliato (stesso tentativo di accordo): segnalato ma non conta come errore nuovo. */
    val foldedIntoPreviousError: Boolean = false,
)

/**
 * Valutazione a tempo: ogni nota del brano va suonata entro [toleranceSec] dall'istante previsto
 * (tempo del brano, già scalato dalla velocità). Le note non suonate diventano "mancate" quando la
 * finestra scade; le note fuori finestra o non attese sono "sbagliate". Logica pura.
 */
class TempoModeEngine(steps: List<Step>, var toleranceSec: Double = 0.2) {
    class Expected(val stepIndex: Int, val key: Int, val timeSec: Double, val noteStartSec: Double = timeSec, val durationSec: Double = 0.0) {
        var state: HitState = HitState.PENDING
            internal set
        var deviationSec: Double = 0.0
            internal set
    }

    val expected: List<Expected> = steps.flatMapIndexed { i, s -> s.notes.map { Expected(i, it.key, s.startSec, it.startSec, it.durationSec) } }
        .sortedBy { it.timeSec }

    // ---- punteggio a unità: tutte le note di un passo (un accordo) sono un solo colpo o un solo errore ----
    private enum class StepOutcome { UNRESOLVED, HIT, MISS, SKIPPED }
    private val stepCount = (expected.maxOfOrNull { it.stepIndex } ?: -1) + 1
    private val stepTime = DoubleArray(stepCount) { Double.NaN }
    private val stepTotal = IntArray(stepCount)
    private val stepPending = IntArray(stepCount)
    private val stepMissed = IntArray(stepCount)
    private val stepSkipped = IntArray(stepCount)
    private val stepOutcome = Array(stepCount) { StepOutcome.UNRESOLVED }
    init {
        for (e in expected) { stepTotal[e.stepIndex]++; stepPending[e.stepIndex]++; stepTime[e.stepIndex] = e.timeSec }
    }

    private fun bump(e: Expected, state: HitState, d: Int) {
        when (state) {
            HitState.PENDING -> stepPending[e.stepIndex] += d
            HitState.MISSED -> stepMissed[e.stepIndex] += d
            HitState.SKIPPED -> stepSkipped[e.stepIndex] += d
            HitState.HIT -> Unit
        }
    }

    /** Cambia lo stato di una nota tenendo aggiornati i conteggi dei passi (colpiti/mancati). */
    private fun setState(e: Expected, new: HitState) {
        val old = e.state
        if (old == new) return
        bump(e, old, -1); bump(e, new, +1)
        e.state = new
        val i = e.stepIndex
        val outcome = when {
            stepPending[i] > 0 -> StepOutcome.UNRESOLVED
            stepSkipped[i] == stepTotal[i] -> StepOutcome.SKIPPED
            stepMissed[i] > 0 -> StepOutcome.MISS
            else -> StepOutcome.HIT
        }
        val prev = stepOutcome[i]
        if (outcome == prev) return
        if (prev == StepOutcome.HIT) hits-- else if (prev == StepOutcome.MISS) misses--
        if (outcome == StepOutcome.HIT) hits++ else if (outcome == StepOutcome.MISS) misses++
        stepOutcome[i] = outcome
    }

    /** Colpi e errori (in passi) con istante in [startSec, endSec): per giudicare un giro di loop. */
    fun lapCounts(startSec: Double, endSec: Double): Pair<Int, Int> {
        var h = 0
        var m = 0
        for (i in 0 until stepCount) {
            val t = stepTime[i]
            if (t.isNaN() || t < startSec || t >= endSec) continue
            when (stepOutcome[i]) { StepOutcome.HIT -> h++; StepOutcome.MISS -> m++; else -> Unit }
        }
        return h to m
    }

    /** Istanti dei passi mancati (un accordo incompleto è un passo mancato), per la mappa degli errori. */
    fun missedStepTimes(): List<Double> = (0 until stepCount).filter { stepOutcome[it] == StepOutcome.MISS }.map { stepTime[it] }

    // istanti delle note attese, già in ordine: servono alla ricerca binaria della finestra (prima si scorreva tutto a ogni fotogramma)
    private val times = DoubleArray(expected.size) { expected[it].timeSec }

    /** Le note ancora in attesa con [fromSec] <= istante <= [toSec] (estremi compresi), in ordine. */
    fun pendingBetween(fromSec: Double, toSec: Double): List<Expected> {
        var lo = 0
        var hi = times.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (times[m] < fromSec) lo = m + 1 else hi = m } // primo con istante >= fromSec
        var out: ArrayList<Expected>? = null
        var i = lo
        while (i < times.size && times[i] <= toSec) {
            val e = expected[i++]
            if (e.state == HitState.PENDING) (out ?: ArrayList<Expected>(8).also { out = it }).add(e)
        }
        return out ?: emptyList()
    }

    companion object {
        /** Quanto lontano (in secondi del brano) si cerca la nota giusta per spiegare un tasto fuori tempo. */
        const val NEAR_SEC = 2.0
        /** Tasti sbagliati entro questa frazione della tolleranza dal precedente sono lo stesso gesto (un accordo con tasti vicini di troppo). */
        const val CLUSTER_FACTOR = 0.75
    }

    private var lastWrongSec = Double.NaN

    var wrong: Int = 0
        private set
    /** Passi colpiti per intero (un accordo completo è un colpo solo). */
    var hits: Int = 0
        private set
    /** Passi mancati, anche solo in parte (un accordo incompleto è un errore solo). */
    var misses: Int = 0
        private set
    /** Note singole colpite: serve alla media degli scarti di tempo. */
    var noteHits: Int = 0
        private set
    private var deviationSum = 0.0
    private var absDeviationSum = 0.0

    /** Note giudicate (colpite o mancate) / note totali giudicabili: 1.0 se non ce ne sono. */
    val accuracy: Double get() = if (hits + misses == 0) 1.0 else hits.toDouble() / (hits + misses)
    val meanDeviationSec: Double get() = if (noteHits == 0) 0.0 else deviationSum / noteHits
    val meanAbsDeviationSec: Double get() = if (noteHits == 0) 0.0 else absDeviationSum / noteHits

    /** Distanza media fra la prima e l'ultima nota colpita di ogni accordo (passi con almeno due note colpite); null se non ce ne sono. */
    fun chordSpreadSec(): Double? {
        val spreads = expected.filter { it.state == HitState.HIT }.groupBy { it.stepIndex }.values.filter { it.size >= 2 }
            .map { g -> g.maxOf { it.deviationSec } - g.minOf { it.deviationSec } }
        return if (spreads.isEmpty()) null else spreads.average()
    }

    fun stateOf(stepIndex: Int, key: Int): HitState? =
        expected.firstOrNull { it.stepIndex == stepIndex && it.key == key }?.state

    // Indice (nota, istante al millisecondo) -> atteso: per leggere lo stato di una nota mentre si disegna
    private val byKeyAndTime: Map<Long, Expected> =
        expected.associateBy { it.key * 10_000_000L + Math.round(it.noteStartSec * 1000) }

    /** Scarti (secondi del brano; negativo = in anticipo) di tutte le note colpite. */
    fun hitDeviations(): List<Double> = expected.filter { it.state == HitState.HIT }.map { it.deviationSec }

    /** Quante volte ogni nota è stata mancata (per i tasti più difficili). */
    fun missedKeyCounts(): Map<Int, Int> {
        val m = HashMap<Int, Int>()
        for (e in expected) if (e.state == HitState.MISSED) m.merge(e.key, 1, Int::plus)
        return m
    }

    /** Di quanto (secondi del brano; negativo = in anticipo) una nota colpita è stata suonata fuori dall'istante previsto; null se non colpita. */
    fun deviationAt(key: Int, timeSec: Double): Double? = byKeyAndTime[key * 10_000_000L + Math.round(timeSec * 1000)]?.takeIf { it.state == HitState.HIT }?.deviationSec

    fun stateAt(key: Int, timeSec: Double): HitState? = byKeyAndTime[key * 10_000_000L + Math.round(timeSec * 1000)]?.state

    /**
     * Segna come mancate le note la cui finestra è scaduta; restituisce quante nuove. Con [onlyBefore]
     * si giudicano solo le note precedenti a quell'istante (es. alla chiusura di un giro di loop).
     */
    fun onTick(songTimeSec: Double, onlyBefore: Double = Double.POSITIVE_INFINITY): Int {
        val before = misses
        for (e in expected) {
            if (e.timeSec >= onlyBefore) break
            if (e.timeSec + toleranceSec >= songTimeSec) break // ordinate: le successive sono ancora valide
            if (e.state == HitState.PENDING) setState(e, HitState.MISSED)
        }
        return misses - before // passi nuovi mancati
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
                return HitOutcome(HitKind.HIT, virtualTime - next.timeSec, noteStartSec = next.noteStartSec, noteDurationSec = next.durationSec)
            }
        }
        if (match == null) {
            val folded = !lastWrongSec.isNaN() && abs(songTimeSec - lastWrongSec) <= toleranceSec * CLUSTER_FACTOR
            if (!folded) { lastWrongSec = songTimeSec; wrong++ } // la finestra resta ancorata al primo tasto del gesto
            // il tasto giusto ma fuori tempo (troppo presto o troppo tardi rispetto a una pausa): si ricorda di quanto
            val near = expected.filter { it.key == key && it.state != HitState.HIT && it.state != HitState.SKIPPED && abs(it.timeSec - songTimeSec) <= NEAR_SEC }
                .minByOrNull { abs(it.timeSec - songTimeSec) }
            return if (near == null) HitOutcome(HitKind.WRONG, null, foldedIntoPreviousError = folded)
            else HitOutcome(HitKind.WRONG, null, songTimeSec - near.timeSec, near.timeSec, foldedIntoPreviousError = folded)
        }
        setState(match, HitState.HIT)
        match.deviationSec = songTimeSec - match.timeSec
        noteHits++
        deviationSum += match.deviationSec
        absDeviationSum += abs(match.deviationSec)
        return HitOutcome(HitKind.HIT, match.deviationSec, noteStartSec = match.noteStartSec, noteDurationSec = match.durationSec)
    }

    /** Esclude dalla valutazione le note ancora in attesa che precedono [timeSec] (dopo un salto di posizione). */
    fun skipBefore(timeSec: Double) {
        for (e in expected) {
            if (e.timeSec >= timeSec - 1e-6) break // tutto ciò che precede il punto di partenza, anche dentro la finestra di tolleranza
            if (e.state == HitState.PENDING) setState(e, HitState.SKIPPED)
        }
    }

    /** Riparte da zero per le note nell'intervallo [startSec, endSec) (es. a ogni giro di loop). */
    fun resetRange(startSec: Double, endSec: Double) {
        for (e in expected) {
            if (e.timeSec < startSec || e.timeSec >= endSec) continue
            if (e.state == HitState.HIT) {
                noteHits--
                deviationSum -= e.deviationSec
                absDeviationSum -= abs(e.deviationSec)
            }
            setState(e, HitState.PENDING)
        }
        for ((e, v) in earlyPresses) { // le pressioni anticipate del giro appena chiuso valgono per questo
            if (e.state != HitState.PENDING) continue
            setState(e, HitState.HIT)
            e.deviationSec = v - e.timeSec
            noteHits++
            deviationSum += e.deviationSec
            absDeviationSum += abs(e.deviationSec)
        }
        earlyPresses.clear()
        wrong = 0
        lastWrongSec = Double.NaN
    }
}
