package com.gianlucacelante.midipiano.practice

import kotlin.math.abs

/** Che cosa è andato storto rispetto a una pausa dello spartito. */
enum class TimingKind { EARLY, LATE, IN_REST, TOO_SHORT, TOO_LONG }

/**
 * Un tasto suonato fuori tempo rispetto a una pausa: [beats] di scarto (in movimenti, quindi indipendenti dal tempo del brano),
 * [seconds] reali (per i registri) e l'intervallo [fromSec]..[toSec] (secondi del brano) in cui cadono le pause da evidenziare.
 */
data class TimingFeedback(
    val kind: TimingKind, val seconds: Double, val fromSec: Double, val toSec: Double, val id: Long = 0, val beats: Double = 0.0,
    /** Quanto dura la pausa scritta (in battiti), se si sa: serve a dire «la pausa dura 1 battito» invece di mischiarla con la nota prima. */
    val restBeats: Double = 0.0,
) {
    /** Etichetta brevissima per il segnalino sullo spartito (il dettaglio in battiti resta in [message], per i registri). */
    val shortLabel: String
        get() = when (kind) {
            TimingKind.EARLY -> "Troppo presto"
            TimingKind.LATE -> "Troppo tardi"
            TimingKind.IN_REST -> "Pausa"
            TimingKind.TOO_SHORT -> "Nota troppo corta"
            TimingKind.TOO_LONG -> "Nota troppo lunga"
        }

    val message: String
        get() = when (kind) {
            TimingKind.EARLY -> "Troppo presto di ${RestTiming.beatsPhrase(beats)}" + if (restBeats > 0.0) ": la pausa dura ${RestTiming.beatsPhrase(restBeats)}" else ""
            TimingKind.LATE -> "In ritardo di ${RestTiming.beatsPhrase(beats)} dopo la pausa" + if (restBeats > 0.0) " di ${RestTiming.beatsPhrase(restBeats)}" else ""
            TimingKind.TOO_SHORT -> "Nota troppo corta: tienila premuta ancora ${RestTiming.beatsPhrase(beats)}"
            TimingKind.TOO_LONG -> "Nota troppo lunga: rilasciala ${RestTiming.beatsPhrase(beats)} prima"
            TimingKind.IN_REST -> if (restBeats > 0.0) "Qui c'è una pausa di ${RestTiming.beatsPhrase(restBeats)}: aspetta ancora ${RestTiming.beatsPhrase(beats)}" else "Qui c'è una pausa: aspetta ancora ${RestTiming.beatsPhrase(beats)}"
        }

    /** La pausa [restStartSec]..[restEndSec] ricade nell'intervallo da evidenziare. */
    fun covers(restStartSec: Double, restEndSec: Double): Boolean = restStartSec < toSec - 1e-6 && restEndSec > fromSec + 1e-6
}

/** Giudizi sulle pause. Logica pura: i tempi sono in secondi del brano, gli scarti si dicono in battiti (i tic del metronomo). */
object RestTiming {
    /** Sotto questo scarto un tasto giusto un po' fuori tempo non merita un commento sulla pausa. */
    const val MIN_NEAR_MISS_SEC = 0.05
    /** «Impara»: anticipo minimo per segnalare, in secondi e in frazione di movimento. */
    const val WAIT_EARLY_MIN_SEC = 0.3
    const val WAIT_EARLY_MIN_BEATS = 0.25
    /** «Impara»: un ritardo si segnala solo se supera sia 2 s sia 3 movimenti (chi legge con calma o riflette non va rimproverato). */
    const val WAIT_LATE_MIN_SEC = 2.0
    const val WAIT_LATE_MIN_BEATS = 3.0

    /** Uno scarto in battiti, a parole: «mezzo battito», «1 battito e mezzo», «2 battiti» (arrotondato al quarto di battito). */
    fun beatsPhrase(beats: Double): String {
        val q = Math.round(beats.coerceAtLeast(0.0) * 4).toInt() // quarti di movimento
        if (q == 0) return "un attimo"
        val whole = q / 4
        val frac = when (q % 4) { 1 -> "un quarto"; 2 -> "mezzo"; 3 -> "tre quarti"; else -> "" }
        return when {
            whole == 0 -> if (q == 2) "mezzo battito" else "$frac di battito"
            frac.isEmpty() -> if (whole == 1) "1 battito" else "$whole battiti"
            else -> (if (whole == 1) "1 battito" else "$whole battiti") + " e $frac"
        }
    }

    /** «A tempo»: il tasto giusto è stato premuto fuori finestra ([deviationSec] = istante - nota; negativo = in anticipo). */
    fun nearMiss(deviationSec: Double, noteSec: Double, secPerBeat: Double, restStartSec: Double? = null, restBeats: Double = 0.0): TimingFeedback? {
        val d = abs(deviationSec)
        if (d < MIN_NEAR_MISS_SEC) return null
        val beats = d / secPerBeat
        return if (deviationSec < 0) TimingFeedback(TimingKind.EARLY, d, noteSec - d, noteSec, beats = beats, restBeats = restBeats)
        else TimingFeedback(TimingKind.LATE, d, restStartSec ?: (noteSec - 2.0), noteSec, beats = beats, restBeats = restBeats) // la pausa che precede la nota
    }

    /** Un tasto qualsiasi premuto mentre non c'è nulla da suonare: la pausa dura ancora fino a [nextNoteSec]. */
    fun inRest(pressSec: Double, nextNoteSec: Double, secPerBeat: Double, restBeats: Double = 0.0): TimingFeedback {
        val left = (nextNoteSec - pressSec).coerceAtLeast(0.0)
        return TimingFeedback(TimingKind.IN_REST, left, pressSec, nextNoteSec, beats = left / secPerBeat, restBeats = restBeats)
    }

    /**
     * «Impara»: fra due note consecutive lo spartito vuole [expectedGapSec]; l'utente ne ha lasciati [actualGapSec].
     * Si giudica solo se in mezzo c'è una pausa ([hadRest]).
     */
    fun waitGap(expectedGapSec: Double, actualGapSec: Double, hadRest: Boolean, secPerBeat: Double, nextNoteSec: Double, restBeats: Double = 0.0): TimingFeedback? {
        if (!hadRest) return null
        val early = expectedGapSec - actualGapSec
        if (early >= maxOf(WAIT_EARLY_MIN_SEC, WAIT_EARLY_MIN_BEATS * secPerBeat)) return TimingFeedback(TimingKind.EARLY, early, nextNoteSec - early, nextNoteSec, beats = early / secPerBeat, restBeats = restBeats)
        val late = actualGapSec - expectedGapSec
        if (late >= maxOf(WAIT_LATE_MIN_SEC, WAIT_LATE_MIN_BEATS * secPerBeat)) return TimingFeedback(TimingKind.LATE, late, nextNoteSec - expectedGapSec, nextNoteSec, beats = late / secPerBeat, restBeats = restBeats)
        return null
    }
}
