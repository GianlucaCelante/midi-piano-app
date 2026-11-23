package com.gianlucacelante.midipiano.learn

import java.util.Locale

/** Le righe di registro dei giri di esercizio: servono ad analizzare una sessione dopo. Logica pura (testo). */
object RunLog {
    private val it = Locale.ITALIAN

    fun finished(exerciseId: String, step: Int, mode: String, speed: Double, hits: Int, misses: Int, wrong: Int, accuracy: Double, stars: Int, passed: Boolean, outcome: String): String =
        String.format(it, "fine giro esercizio=%s passo=%d modo=%s vel=%.2f colpite=%d mancate=%d sbagliate=%d precisione=%d%% stelle=%d riuscito=%s esito=%s",
            exerciseId, step + 1, mode, speed, hits, misses, wrong, Math.round(accuracy * 100).toInt(), stars, if (passed) "sì" else "no", outcome)

    fun abandoned(exerciseId: String, step: Int, mode: String, atSec: Double, hits: Int, wrong: Int, reason: String): String =
        String.format(it, "giro abbandonato esercizio=%s passo=%d modo=%s a=%.1fs colpite=%d sbagliate=%d motivo=%s", exerciseId, step + 1, mode, atSec, hits, wrong, reason)
}
