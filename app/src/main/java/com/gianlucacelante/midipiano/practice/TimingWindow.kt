package com.gianlucacelante.midipiano.practice

/**
 * Finestra di tolleranza di «A tempo». Fissa a 0,2 s reali andava bene per i brani veloci, ma in uno lento (o rallentato) un anticipo
 * naturale di un quarto di battito veniva segnato sbagliato: la finestra cresce con il battito reale, entro un minimo e un massimo.
 */
object TimingWindow {
    const val MIN_REAL_SEC = 0.2
    const val MAX_REAL_SEC = 0.4
    const val BEAT_FRACTION = 0.25

    /** Tolleranza in secondi reali: un quarto del battito reale (che a velocità ridotta dura di più), fra 0,2 e 0,4 s. */
    fun realToleranceSec(secPerBeat: Double, speed: Double): Double =
        (BEAT_FRACTION * secPerBeat / speed.coerceAtLeast(0.05)).coerceIn(MIN_REAL_SEC, MAX_REAL_SEC)

    /** «Perfetto»: entro metà della finestra. Oltre, il colpo vale ma si colora diversamente (non perfetto). */
    const val PERFECT_FRACTION = 0.5

    fun perfectSongSec(secPerBeat: Double, speed: Double): Double = songToleranceSec(secPerBeat, speed) * PERFECT_FRACTION

    /** La stessa tolleranza in secondi del brano (che scorrono a [speed] volte il tempo reale). */
    fun songToleranceSec(secPerBeat: Double, speed: Double): Double = realToleranceSec(secPerBeat, speed) * speed
}
