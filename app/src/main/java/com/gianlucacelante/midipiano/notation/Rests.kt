package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Song

data class StaffRest(val clef: Clef, val startSec: Double, val beats: Double, val endSec: Double = startSec)

object Rests {
    /** Valori di pausa in quarti, dal più lungo: semibreve, minima col punto, minima, ... biscroma. */
    private val VALUES = doubleArrayOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.25, 0.125)

    /** Sotto questa frazione di quarto un vuoto non è una pausa (articolazione, imprecisione). */
    private const val MIN_GAP_BEATS = 0.2

    /**
     * Pause di un rigo: i vuoti fra la fine scritta di un accordo e l'attacco del successivo (e quello
     * iniziale), divisi alle stanghette e scomposti in valori di pausa. [chords] ordinati, di una sola chiave.
     */
    fun compute(chords: List<Chord>, song: Song): List<StaffRest> {
        if (chords.isEmpty()) return emptyList()
        val clef = chords.first().clef
        val result = ArrayList<StaffRest>()
        var cursor = 0.0
        for (c in chords) {
            addGap(clef, cursor, c.startSec, song, result)
            cursor = maxOf(cursor, song.secAfter(c.startSec, c.longestBeats))
        }
        // fino alla fine dell'ultima battuta: un rigo che tace mentre l'altro continua chiude con le sue pause
        val barStarts = listOf(0.0) + song.barLines
        val lastBarEnd = barStarts.last() + song.barDurationSec
        val songEnd = song.notes.maxOfOrNull { it.startSec + it.durationSec } ?: 0.0
        if (song.barLines.isNotEmpty() && songEnd > cursor + 1e-6 && lastBarEnd > cursor + 1e-6 && song.beatsBetween(cursor, lastBarEnd) < song.beatsBetween(0.0, song.barDurationSec) * 2.0) {
            addGap(clef, cursor, lastBarEnd, song, result)
        }
        return result
    }

    private fun addGap(clef: Clef, from: Double, to: Double, song: Song, out: MutableList<StaffRest>) {
        // si divide il vuoto alle stanghette di battuta: una pausa non le attraversa
        val cuts = (listOf(from) + song.barLines.filter { it > from + 1e-6 && it < to - 1e-6 } + listOf(to))
        for ((a, b) in cuts.zipWithNext()) {
            var remaining = song.beatsBetween(a, b)
            if (remaining < MIN_GAP_BEATS) continue
            var t = a
            while (remaining >= MIN_GAP_BEATS) {
                val v = VALUES.firstOrNull { it <= remaining + 1e-6 } ?: break
                out.add(StaffRest(clef, t, v, song.secAfter(t, v)))
                t = song.secAfter(t, v)
                remaining -= v
            }
        }
    }
}
