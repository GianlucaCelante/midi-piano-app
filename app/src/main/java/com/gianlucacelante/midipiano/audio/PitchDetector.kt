package com.gianlucacelante.midipiano.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Riconoscimento dell'altezza di una nota singola con il metodo YIN (de Cheveigné e Kawahara, 2002):
 * differenza cumulata normalizzata, soglia e interpolazione parabolica. Riconosce una nota alla
 * volta (melodie su un pianoforte acustico); gli accordi non sono supportati.
 */
object PitchDetector {
    data class Pitch(val frequency: Double, val midi: Int, val clarity: Double)

    fun rms(buf: FloatArray): Double {
        if (buf.isEmpty()) return 0.0
        var s = 0.0
        for (v in buf) s += v.toDouble() * v
        return sqrt(s / buf.size)
    }

    /** Altezza dominante in [buf], o null se il suono non è abbastanza intonato (silenzio, rumore). */
    fun detect(buf: FloatArray, sampleRate: Int, minFreq: Double = 60.0, maxFreq: Double = 1400.0, threshold: Double = 0.12): Pitch? {
        val tauMax = (sampleRate / minFreq).toInt().coerceAtMost(buf.size / 2)
        val tauMin = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
        val w = buf.size - tauMax
        if (w <= 0 || rms(buf) < 1e-4) return null

        // 1) differenza e 2) differenza cumulata normalizzata
        val d = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (i in 0 until w) {
                val diff = buf[i] - buf[i + tau]
                sum += diff.toDouble() * diff
            }
            d[tau] = sum
        }
        val cmnd = DoubleArray(tauMax + 1)
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += d[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else d[tau] * tau / running
        }

        // 3) primo minimo sotto soglia (e scende fino al minimo locale)
        var tauEstimate = -1
        var tau = tauMin
        while (tau < tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate < 0) return null

        // 4) interpolazione parabolica attorno al minimo
        val x0 = if (tauEstimate > 1) tauEstimate - 1 else tauEstimate
        val x2 = if (tauEstimate < tauMax) tauEstimate + 1 else tauEstimate
        val better = if (x0 == tauEstimate || x2 == tauEstimate) {
            tauEstimate.toDouble()
        } else {
            val s0 = cmnd[x0]; val s1 = cmnd[tauEstimate]; val s2 = cmnd[x2]
            val denom = 2 * (2 * s1 - s2 - s0)
            if (abs(denom) < 1e-12) tauEstimate.toDouble() else tauEstimate + (s2 - s0) / denom
        }
        val freq = sampleRate / better
        val midi = (69 + 12 * log2(freq / 440.0)).roundToInt()
        if (midi !in 21..108) return null
        return Pitch(freq, midi, (1.0 - cmnd[tauEstimate]).coerceIn(0.0, 1.0))
    }
}
