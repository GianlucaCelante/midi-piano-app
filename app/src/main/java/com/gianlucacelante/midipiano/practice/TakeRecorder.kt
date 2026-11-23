package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource

/**
 * Registra i tasti premuti durante un'esecuzione (con i tempi reali) per poterla riascoltare.
 * Le note si ricostruiscono accoppiando ogni pressione al suo rilascio; si tiene al massimo [maxNotes] note.
 */
class TakeRecorder(private val maxNotes: Int = 20_000) {
    private class Press(val key: Int, val velocity: Int, val startNanos: Long, var endNanos: Long = -1L)

    private val presses = ArrayList<Press>()
    private val open = HashMap<Int, Press>() // tasto -> pressione ancora senza rilascio

    val isEmpty: Boolean get() = presses.isEmpty()

    fun clear() { presses.clear(); open.clear() }

    fun noteOn(key: Int, velocity: Int, nowNanos: Long) {
        if (presses.size >= maxNotes) return
        val p = Press(key, velocity, nowNanos)
        presses.add(p)
        open[key] = p
    }

    fun noteOff(key: Int, nowNanos: Long) {
        val p = open.remove(key) ?: return
        p.endNanos = nowNanos
    }

    /** L'esecuzione come brano da riascoltare (null se non c'è nulla). */
    fun toSong(): Song? {
        if (presses.isEmpty()) return null
        val t0 = presses.minOf { it.startNanos }
        val notes = presses.map { p ->
            val start = (p.startNanos - t0) / 1e9
            val dur = if (p.endNanos >= 0) (p.endNanos - p.startNanos) / 1e9 else DEFAULT_LENGTH_SEC
            SongNote(start, dur.coerceAtLeast(0.05), p.key, p.velocity.coerceIn(1, 127), Hand.RIGHT)
        }.sortedWith(compareBy({ it.startSec }, { it.key }))
        return Song("take", "La tua esecuzione", null, SongSource("registrazione", "personale"), notes)
    }

    private companion object { const val DEFAULT_LENGTH_SEC = 0.4 }
}
