package com.gianlucacelante.midipiano.practice

/** Durata delle note: quanto a lungo si tiene premuto un tasto rispetto a quanto scritto. Logica pura. */
object HoldTiming {
    /** Sotto mezzo battito scritto (staccato, note velocissime) la durata non si controlla. */
    const val MIN_JUDGED_BEATS = 0.5
    /** «Troppo corta»: tenuta meno di questa frazione del dovuto e con almeno mezzo battito in meno. */
    const val SHORT_RATIO = 0.6
    const val MIN_SHORT_BEATS = 0.5
    /** «Troppo lunga» (solo se dopo c'è una pausa): oltre questa frazione e con almeno un battito in più. */
    const val LONG_RATIO = 1.6
    const val MIN_LONG_BEATS = 1.0

    /**
     * [heldSec] e [expectedSec] sono secondi reali (la velocità è già applicata), [secPerBeat] il battito in secondi reali.
     * [restFollows]: dopo la nota c'è una pausa, quindi tenerla di più è un errore; altrimenti la nota successiva la «copre» e si tollera.
     * Gli istanti del brano servono a dire quale pausa evidenziare sullo spartito.
     */
    fun judge(
        heldSec: Double, expectedSec: Double, secPerBeat: Double, restFollows: Boolean,
        noteStartSec: Double = 0.0, noteEndSec: Double = 0.0, restEndSec: Double = 0.0,
    ): TimingFeedback? {
        if (expectedSec / secPerBeat < MIN_JUDGED_BEATS) return null
        val missing = expectedSec - heldSec
        if (heldSec < expectedSec * SHORT_RATIO && missing / secPerBeat >= MIN_SHORT_BEATS) {
            return TimingFeedback(TimingKind.TOO_SHORT, missing, noteStartSec, noteStartSec, beats = missing / secPerBeat)
        }
        val extra = heldSec - expectedSec
        if (restFollows && heldSec > expectedSec * LONG_RATIO && extra / secPerBeat >= MIN_LONG_BEATS) {
            return TimingFeedback(TimingKind.TOO_LONG, extra, noteEndSec, restEndSec, beats = extra / secPerBeat)
        }
        return null
    }
}
