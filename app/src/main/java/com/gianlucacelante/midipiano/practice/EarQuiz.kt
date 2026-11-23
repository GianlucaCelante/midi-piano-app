package com.gianlucacelante.midipiano.practice

import kotlin.math.abs
import kotlin.random.Random

/** Allenamento dell'orecchio: riconoscere o suonare un intervallo, e ripetere una piccola melodia. Logica pura. */
object EarQuiz {
    const val MAX_LEVEL = 4

    val INTERVAL_NAMES = mapOf(
        1 to "seconda minore", 2 to "seconda maggiore", 3 to "terza minore", 4 to "terza maggiore", 5 to "quarta giusta", 6 to "tritono",
        7 to "quinta giusta", 8 to "sesta minore", 9 to "sesta maggiore", 10 to "settima minore", 11 to "settima maggiore", 12 to "ottava",
    )

    /** Gli intervalli del livello: pochi e ben diversi all'inizio, tutti alla fine. */
    fun intervalsFor(level: Int): List<Int> = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> listOf(4, 7, 12)
        2 -> listOf(3, 4, 5, 7, 12)
        3 -> listOf(2, 3, 4, 5, 7, 9, 12)
        else -> (1..12).toList()
    }

    data class IntervalQuestion(val base: Int, val semitones: Int, val up: Boolean) {
        val top: Int get() = if (up) base + semitones else base - semitones
        val name: String get() = INTERVAL_NAMES.getValue(semitones)
        /** Le note nell'ordine in cui si ascoltano. */
        fun keys(): List<Int> = listOf(base, top)
    }

    fun nextInterval(level: Int, rnd: Random, prev: IntervalQuestion?): IntervalQuestion {
        val pool = intervalsFor(level).filter { it != prev?.semitones }.ifEmpty { intervalsFor(level) }
        val semis = pool.random(rnd)
        val up = level < 4 || rnd.nextBoolean() // all'ultimo livello anche in discesa
        val lo = if (up) 52 else 52 + semis
        val hi = if (up) 71 - semis else 71
        return IntervalQuestion(rnd.nextInt(lo, hi + 1), semis, up)
    }

    /** Quattro risposte: quella giusta e tre vicine, in ordine casuale. */
    fun options(q: IntervalQuestion, level: Int, rnd: Random): List<String> {
        val others = intervalsFor(maxOf(level, 2)).filter { it != q.semitones }.shuffled(rnd).take(3)
        return (others + q.semitones).shuffled(rnd).map { INTERVAL_NAMES.getValue(it) }
    }

    /** Una melodia da ripetere: più lunga e con salti più grandi salendo di livello. */
    fun nextMelody(level: Int, rnd: Random, prev: List<Int>? = null): List<Int> {
        val lv = level.coerceIn(1, MAX_LEVEL)
        val pool = when (lv) {
            1 -> listOf(60, 62, 64, 67, 69)
            2 -> listOf(60, 62, 64, 65, 67, 69, 71, 72)
            3 -> listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76)
            else -> (55..76).toList()
        }
        val maxLeap = when (lv) { 1 -> 4; 2 -> 5; 3 -> 9; else -> 12 }
        val length = 2 + lv // 3, 4, 5, 6 note
        repeat(20) {
            val out = ArrayList<Int>()
            out += pool.random(rnd)
            while (out.size < length) {
                val c = pool.filter { it != out.last() && abs(it - out.last()) <= maxLeap }
                if (c.isEmpty()) break
                out += c.random(rnd)
            }
            if (out.size == length && out != prev) return out
        }
        return pool.take(length)
    }

    /** Quante note sono giuste, nell'ordine, dall'inizio della melodia. */
    fun correctPrefix(expected: List<Int>, played: List<Int>): Int {
        var n = 0
        while (n < expected.size && n < played.size && expected[n] == played[n]) n++
        return n
    }

    fun adjustLevel(level: Int, correct: Int, asked: Int): Int = when {
        asked >= 10 && correct >= 8 -> (level + 1).coerceAtMost(MAX_LEVEL)
        asked >= 6 && correct <= asked * 0.4 -> (level - 1).coerceAtLeast(1)
        else -> level
    }
}
