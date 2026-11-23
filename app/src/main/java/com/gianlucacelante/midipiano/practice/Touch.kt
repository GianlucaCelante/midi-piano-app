package com.gianlucacelante.midipiano.practice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Valutazione del «tocco» (la forza dei tasti) in un esercizio. Logica pura.
 *  - TARGET: ogni nota ha la sua forza scritta (p, mf, f…): conta quanto ci si avvicina.
 *  - EVEN: tutte le note con forza simile (scale regolari).
 *  - CRESC / DIM: la forza sale (o scende) un po' alla volta.
 */
object Touch {
    enum class Goal { TARGET, EVEN, CRESC, DIM }

    const val PASS = 0.6
    /** Sotto questa quota di note con la forza vera (tastiera MIDI) il tocco non si valuta: i tasti sullo schermo non hanno forza. */
    const val MIN_MIDI_SHARE = 0.5

    fun parse(s: String?): Goal? = when (s) { "target" -> Goal.TARGET; "even" -> Goal.EVEN; "cresc" -> Goal.CRESC; "dim" -> Goal.DIM; else -> null }

    class Result(val score: Double, val passed: Boolean, val text: String)

    /**
     * [played]: forza di ogni nota colpita (0 se arrivata dallo schermo); [targets]: forza scritta di quella nota (null se non ce n'è);
     * [hits]: note colpite in tutto. Null se non c'è abbastanza materiale per giudicare.
     */
    fun evaluate(goal: Goal, played: List<Int>, targets: List<Int?>, hits: Int): Result? {
        val real = played.indices.filter { played[it] > 0 }
        if (hits < 4 || real.size < hits * MIN_MIDI_SHARE || real.size < 4) return null
        val v = real.map { played[it] }
        val score = when (goal) {
            Goal.TARGET -> {
                val pairs = real.mapNotNull { i -> targets.getOrNull(i)?.let { played[i] to it } }
                if (pairs.isEmpty()) return null
                pairs.sumOf { (p, t) -> when (Dynamics.compare(p, t)) { Dynamics.Match.OK -> 1.0; Dynamics.Match.NEAR -> 0.5; Dynamics.Match.OFF -> 0.0 } } / pairs.size
            }
            Goal.EVEN -> {
                val mean = v.average()
                val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
                (1.0 - sd / 20.0).coerceIn(0.0, 1.0) // scarto di 8 → 60 %
            }
            Goal.CRESC, Goal.DIM -> {
                val dir = if (goal == Goal.CRESC) 1 else -1
                val ok = (1 until v.size).count { (v[it] - v[it - 1]) * dir >= -2 }.toDouble() / (v.size - 1)
                val third = maxOf(1, v.size / 3)
                val change = (v.takeLast(third).average() - v.take(third).average()) * dir // quanto è salita (o scesa) la forza
                if (change < 15) minOf(ok, 0.4) else ok
            }
        }
        val passed = score >= PASS
        val pct = (score * 100).toInt()
        val text = when (goal) {
            Goal.TARGET -> if (passed) "Forza giusta: $pct%." else "Forza lontana da quella richiesta ($pct%): guarda il segno in alto a destra."
            Goal.EVEN -> if (passed) "Forza regolare: $pct%." else "Forza irregolare ($pct%): cerca lo stesso peso su ogni nota."
            Goal.CRESC -> if (passed) "Crescendo riuscito: $pct%." else "Il crescendo non è chiaro ($pct%): parti piano e aumenta un po' alla volta."
            Goal.DIM -> if (passed) "Diminuendo riuscito: $pct%." else "Il diminuendo non è chiaro ($pct%): parti forte e cala un po' alla volta."
        }
        return Result(score, passed, text)
    }
}
