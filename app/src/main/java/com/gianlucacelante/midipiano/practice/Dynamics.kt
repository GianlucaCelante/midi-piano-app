package com.gianlucacelante.midipiano.practice

/** Forza di un tasto (velocity MIDI 1-127) come segno di dinamica, e confronto fra suonato e atteso. */
object Dynamics {
    enum class Match { OK, NEAR, OFF }

    private val LIMITS = intArrayOf(33, 49, 64, 80, 96) // sotto: pp, p, mp, mf, f; da 96 in su: ff
    private val NAMES = arrayOf("pp", "p", "mp", "mf", "f", "ff")

    fun level(velocity: Int): Int = LIMITS.indexOfFirst { velocity < it }.let { if (it < 0) NAMES.lastIndex else it }

    fun label(velocity: Int): String = NAMES[level(velocity)]

    fun compare(played: Int, expected: Int): Match = when (kotlin.math.abs(level(played) - level(expected))) {
        0 -> Match.OK
        1 -> Match.NEAR
        else -> Match.OFF
    }

    /** Un brano ha una dinamica scritta solo se le forze delle sue note variano davvero (i brani generati hanno tutte la stessa). */
    fun isExpressive(velocities: List<Int>): Boolean = velocities.isNotEmpty() && velocities.max() - velocities.min() >= 20
}
