package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote

/** Nota da disegnare: [offsetSec] è la distanza dal "tempo corrente" (negativa = già iniziata). */
data class VisibleNote(val key: Int, val hand: Hand, val offsetSec: Double, val durationSec: Double)

object FallingNotes {
    private const val LOWEST = 21 // La0
    private const val HIGHEST = 108 // Do8
    private const val MIN_KEYS = 88 // tastiera intera da 88 tasti, come in Flowkey

    /** Note che iniziano entro [horizonSec] da [currentTimeSec], o che stanno ancora suonando. */
    fun visible(notes: List<SongNote>, currentTimeSec: Double, horizonSec: Double, hand: Hand): List<VisibleNote> =
        notes.asSequence()
            .filter { hand == Hand.BOTH || it.hand == hand }
            .filter { it.startSec - currentTimeSec <= horizonSec && it.startSec + it.durationSec > currentTimeSec }
            .map { VisibleNote(it.key, it.hand, it.startSec - currentTimeSec, it.durationSec) }
            .toList()

    /** Intervallo di tasti da mostrare: copre il brano, da un Do a un Si, almeno 3 ottave. */
    fun keyboardRange(notes: List<SongNote>): IntRange {
        if (notes.isEmpty()) return 48..83
        val lo = notes.minOf { it.key }.coerceIn(LOWEST, HIGHEST)
        val hi = notes.maxOf { it.key }.coerceIn(LOWEST, HIGHEST)
        var first = (lo / 12 * 12).coerceAtLeast(LOWEST)
        var last = (hi / 12 * 12 + 11).coerceAtMost(HIGHEST)
        while (last - first + 1 < MIN_KEYS && (first > LOWEST || last < HIGHEST)) {
            first = (first - 12).coerceAtLeast(LOWEST)
            if (last - first + 1 < MIN_KEYS) last = (last + 12).coerceAtMost(HIGHEST)
        }
        return first..last // sempre non vuoto: first <= lo <= hi <= last
    }
}
