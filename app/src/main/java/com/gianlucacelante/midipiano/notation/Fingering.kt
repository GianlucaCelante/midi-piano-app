package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import kotlin.math.abs

/**
 * Diteggiatura di base per chi comincia: a ogni nota un dito da 1 (pollice) a 5 (mignolo), per mano.
 * Non è la diteggiatura "d'autore": serve a capire dove sta la mano. Le melodie si risolvono con un
 * percorso a costo minimo (dita vicine per note vicine, nessun salto brusco), gli accordi con
 * schemi fissi (pollice-medio-mignolo...). La mano sinistra è lo specchio della destra.
 */
object Fingering {
    /** Chiave di una nota: istante in millisecondi e tasto. */
    fun id(startSec: Double, key: Int): Long = Math.round(startSec * 1000) * 1000L + key

    private const val SAME_STEP_SEC = 0.012
    private const val SEMITONES_PER_FINGER = 1.8 // distanza media fra due dita vicine
    private val BLACK = setOf(1, 3, 6, 8, 10)

    fun assign(notes: List<SongNote>): Map<Long, Int> {
        val out = HashMap<Long, Int>(notes.size * 2)
        for (hand in listOf(Hand.RIGHT, Hand.LEFT)) {
            val mine = notes.filter { it.hand == hand }.sortedWith(compareBy({ it.startSec }, { it.key }))
            if (mine.isEmpty()) continue
            // passi: note quasi simultanee (accordi)
            val steps = ArrayList<List<SongNote>>()
            for (n in mine) {
                val last = steps.lastOrNull()
                if (last != null && n.startSec - last.first().startSec <= SAME_STEP_SEC) steps[steps.lastIndex] = last + n else steps.add(listOf(n))
            }
            var i = 0
            while (i < steps.size) {
                if (steps[i].size > 1) {
                    chord(steps[i], hand, out); i++
                } else {
                    var j = i
                    while (j < steps.size && steps[j].size == 1) j++
                    run(steps.subList(i, j).map { it[0] }, hand, out)
                    i = j
                }
            }
        }
        return out
    }

    private fun chord(step: List<SongNote>, hand: Hand, out: MutableMap<Long, Int>) {
        val keys = step.map { it.key }.distinct().sorted()
        val span = keys.last() - keys.first()
        val pattern = when (keys.size) {
            2 -> if (span <= 4) listOf(1, 3) else if (span <= 7) listOf(1, 4) else listOf(1, 5)
            3 -> listOf(1, 3, 5)
            4 -> listOf(1, 2, 3, 5)
            else -> listOf(1, 2, 3, 4, 5)
        }
        keys.forEachIndexed { idx, k ->
            val f = pattern.getOrNull(idx) ?: return@forEachIndexed
            val finger = if (hand == Hand.RIGHT) f else 6 - f
            for (n in step) if (n.key == k) out[id(n.startSec, n.key)] = finger
        }
    }

    /** Melodia di note singole: percorso di costo minimo (Viterbi) sui 5 dita. */
    private fun run(seq: List<SongNote>, hand: Hand, out: MutableMap<Long, Int>) {
        val n = seq.size
        val cost = Array(n) { DoubleArray(6) { Double.MAX_VALUE } }
        val from = Array(n) { IntArray(6) }
        for (f in 1..5) cost[0][f] = black(seq[0].key, f)
        for (t in 1 until n) {
            val moved = seq[t].key - seq[t - 1].key
            for (f2 in 1..5) {
                for (f1 in 1..5) {
                    if (cost[t - 1][f1] == Double.MAX_VALUE) continue
                    val c = cost[t - 1][f1] + transition(moved, f1, f2, hand) + black(seq[t].key, f2)
                    if (c < cost[t][f2]) { cost[t][f2] = c; from[t][f2] = f1 }
                }
            }
        }
        var f = (1..5).minByOrNull { cost[n - 1][it] }!!
        for (t in n - 1 downTo 0) {
            out[id(seq[t].startSec, seq[t].key)] = f
            f = from[t][f]
        }
    }

    private fun black(key: Int, finger: Int): Double =
        if (key % 12 in BLACK) (if (finger == 1) 1.5 else if (finger == 5) 0.8 else 0.0) else 0.0

    private fun transition(moved: Int, f1: Int, f2: Int, hand: Hand): Double {
        val df = f2 - f1
        if (moved == 0) return if (df == 0) 0.0 else 4.0 // stessa nota: stesso dito
        if (abs(moved) > 8) { // salto grande: la mano cambia posizione. Il dito cambia (mai lo stesso su due note lontane)
            // e di preferenza si va dal pollice al mignolo salendo (destra), come nelle ottave; a sinistra al contrario
            val m = if (hand == Hand.RIGHT) moved else -moved
            val ideal = if (m > 0) f2 > f1 else f2 < f1
            return if (f1 == f2) 4.0 else if (ideal) 1.0 else 2.0
        }
        if (df == 0) return 6.0 // due note diverse con lo stesso dito
        // verso della mano: > 0 quando la nota "sale" nel senso delle dita (destra: dita crescenti verso l'acuto)
        val m = if (hand == Hand.RIGHT) moved else -moved
        val crosses = (m > 0) != (df > 0)
        if (crosses) {
            // un dito non può scendere mentre la nota sale: serve il passaggio del pollice (sotto in salita, sopra in discesa)
            val thumbUnder = m > 0 && f2 == 1 && f1 in 2..4
            val thumbOver = m < 0 && f1 == 1 && f2 in 2..4
            return if ((thumbUnder || thumbOver) && abs(moved) <= 2) 1.8 else 8.0
        }
        val expected = df * SEMITONES_PER_FINGER * (if (hand == Hand.RIGHT) 1 else -1)
        val diff = moved - expected
        return diff * diff * 0.15
    }
}
