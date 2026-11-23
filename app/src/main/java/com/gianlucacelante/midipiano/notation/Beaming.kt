package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Song
import kotlin.math.floor

/** Accordi collegati da una trave. [maxLevel]: 1 = croma, 2 = semicroma, 3 = biscroma. */
data class BeamGroup(val chords: List<Chord>) {
    val maxLevel: Int = chords.maxOf { it.flags }
}

object Beaming {
    /** Quanto oltre la durata scritta può cadere l'attacco successivo per restare "attaccato" (staccato, abbellimenti). */
    private const val CONTIGUITY = 1.3

    /**
     * Raggruppa in travi gli accordi brevi consecutivi di un rigo ([chords] ordinati, di una sola chiave):
     * stesso movimento (quarto, o quarto col punto nei tempi composti), senza pause in mezzo.
     */
    fun groups(chords: List<Chord>, song: Song): List<BeamGroup> {
        val pickup = (song.barLines.firstOrNull() ?: Double.MAX_VALUE) < song.barDurationSec * 0.9
        fun beatQuartersAt(sec: Double): Double {
            val (num, den) = song.timeSignatureAt(sec)
            return if (den == 8 && num % 3 == 0) 1.5 else 4.0 / den
        }
        val tol = 0.03 // anticipo/ritardo tollerato (sec) per un MIDI suonato e non quantizzato
        fun cell(c: Chord): Pair<Int, Long> {
            val bar = song.barAt(c.startSec)
            // con un levare la prima battuta comincia prima di zero: i movimenti si contano dal suo inizio "virtuale"
            val start = if (bar == 1 && pickup) song.barLines[0] - song.barDurationSec else song.barStartSec(bar)
            return bar to floor(song.beatsBetween(start, c.startSec + tol) / beatQuartersAt(c.startSec) + 1e-6).toLong()
        }

        val result = ArrayList<BeamGroup>()
        var current = ArrayList<Chord>()
        fun flush() {
            if (current.size >= 2) result.add(BeamGroup(current))
            current = ArrayList()
        }
        for (c in chords) {
            val beamable = c.hasStem && c.flags >= 1
            if (!beamable) {
                flush()
                continue
            }
            val last = current.lastOrNull()
            val continues = last != null && cell(last) == cell(c) &&
                c.startSec - last.startSec <= (song.secAfter(last.startSec, last.beats * CONTIGUITY) - last.startSec)
            if (!continues) flush()
            current.add(c)
        }
        flush()
        return result
    }

    /** Accordi che fanno parte di una trave: non ricevono le code singole. */
    fun beamed(groups: List<BeamGroup>): Set<Chord> = groups.flatMap { it.chords }.toSet()
}
