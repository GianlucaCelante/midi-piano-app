package com.gianlucacelante.midipiano.notation

import kotlin.math.abs
import kotlin.math.ln

/**
 * Dal MIDI alla durata scritta. Un file MIDI registra quanto la nota *suona*, non il valore
 * notato: nei passaggi staccati suona molto meno. Si assume che un attacco successivo vicino
 * indichi un valore pieno (articolazione), mentre una pausa lunga resta una pausa.
 */
object NoteValues {
    /** Valori in quarti: dalla biscroma alla semibreve, con i valori puntati più comuni. */
    private val VALUES = doubleArrayOf(0.125, 0.25, 0.375, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0)

    /** Oltre questa distanza (in quarti) dall'attacco successivo si considera una pausa, non un legato. */
    private const val MAX_ARTICULATION_GAP = 2.0
    private const val OVERLAP_MAX = 1.4
    private const val CLEAN_TOLERANCE = 0.12
    private const val MIN_REST_GAP = 0.45

    /** Valore notato più vicino (confronto sul rapporto: 0,4 è più vicino a 0,5 che a 0,25). */
    fun quantize(beats: Double): Double {
        val b = beats.coerceAtLeast(VALUES.first() / 2)
        return VALUES.minByOrNull { abs(ln(b / it)) }!!
    }

    private fun near(v: Double, target: Double) = abs(v - target) < 0.04

    /** [startBeats]: posizione dell'attacco in quarti dall'inizio del brano; serve a riconoscere le terzine (distanze di 1/3 o 2/3). */
    fun notated(playedBeats: Double, nextOnsetBeats: Double?, startBeats: Double = Double.NaN): Double {
        if (nextOnsetBeats != null && !startBeats.isNaN() && playedBeats <= nextOnsetBeats * 1.25) {
            val f = startBeats - Math.floor(startBeats)
            val onGrid = near(f, 0.0) || near(f, 1.0) || near(f, 1.0 / 3) || near(f, 2.0 / 3)
            if (onGrid) {
                if (near(nextOnsetBeats, 1.0 / 3)) return 1.0 / 3
                if (near(nextOnsetBeats, 2.0 / 3)) return 2.0 / 3
            }
        }
        if (nextOnsetBeats == null || nextOnsetBeats > MAX_ARTICULATION_GAP) return quantize(playedBeats)
        // sovrapposizione leggera (legato): vale la distanza dalla nota successiva, non il suono lungo (1,3 quarti non è una semiminima col punto)
        if (playedBeats >= nextOnsetBeats) return quantize(if (playedBeats <= nextOnsetBeats * OVERLAP_MAX) nextOnsetBeats else playedBeats)
        // una pausa vera: la nota dura quanto un valore netto (mezzo quarto, un quarto...) e il vuoto che segue è almeno una croma
        val q = quantize(playedBeats)
        val clean = abs(playedBeats / q - 1.0) < CLEAN_TOLERANCE
        if (clean && nextOnsetBeats - playedBeats >= MIN_REST_GAP) return q
        return quantize(nextOnsetBeats) // staccato: si scrive il valore pieno
    }
}
