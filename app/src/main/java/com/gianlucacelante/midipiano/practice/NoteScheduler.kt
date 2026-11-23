package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote

/** Inizio o fine di una nota, a un certo istante del brano. */
data class NoteEvent(val timeSec: Double, val key: Int, val velocity: Int, val on: Boolean)

/**
 * Elenco ordinato di inizi e fine delle note, per far suonare il brano da solo (modalità Ascolta).
 * [due] restituisce gli eventi in una finestra di tempo, senza ripetizioni fra finestre consecutive.
 */
class NoteScheduler(notes: List<SongNote>, hand: Hand) {
    private val events: List<NoteEvent> = notes
        .filter { hand == Hand.BOTH || it.hand == hand }
        .flatMap {
            val end = it.startSec + maxOf(it.durationSec, MIN_DURATION_SEC)
            listOf(NoteEvent(it.startSec, it.key, it.velocity, on = true), NoteEvent(end, it.key, it.velocity, on = false))
        }
        // a parità di istante il rilascio precede l'attacco, così una nota ribattuta suona di nuovo
        .sortedWith(compareBy({ it.timeSec }, { it.on }))

    private val times = DoubleArray(events.size) { events[it].timeSec }

    /** Eventi con istante > [afterSec] e <= [upToSec]. */
    fun due(afterSec: Double, upToSec: Double): List<NoteEvent> {
        if (upToSec <= afterSec) return emptyList()
        val from = firstIndexAfter(afterSec)
        val out = ArrayList<NoteEvent>()
        var i = from
        while (i < events.size && times[i] <= upToSec) out.add(events[i++])
        return out
    }

    /** Primo indice con tempo > [t]. */
    private fun firstIndexAfter(t: Double): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private companion object {
        const val MIN_DURATION_SEC = 0.02
    }
}
