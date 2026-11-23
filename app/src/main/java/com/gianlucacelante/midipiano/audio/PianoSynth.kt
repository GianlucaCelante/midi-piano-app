package com.gianlucacelante.midipiano.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sintetizzatore di piano additivo (senza campioni): ogni nota è la somma di alcune armoniche,
 * leggermente inarmoniche come le corde vere, che decadono a velocità diverse (le alte più in
 * fretta, le note acute più in fretta delle gravi). Al rilascio lo smorzatore spegne il suono.
 * Puro Kotlin, senza dipendenze Android: si prova con i test e gira sul thread audio.
 *
 * Non è thread-safe: chiamare [noteOn]/[noteOff]/[render] dallo stesso thread (l'AudioEngine
 * accoda i comandi).
 */
class PianoSynth(private val sampleRate: Int) {
    companion object {
        const val MAX_VOICES = 24
        private const val PARTIALS = 6
        private const val TABLE_SIZE = 2048
        private const val MASTER_GAIN = 0.55f
        private const val RELEASE_SEC = 0.09
        private const val SILENCE = 1e-4f

        private val SINE_TABLE = FloatArray(TABLE_SIZE + 1) { sin(2.0 * PI * it / TABLE_SIZE).toFloat() }
    }

    private class Voice(partials: Int) {
        var active = false
        var releasing = false
        var held = false // tasto lasciato ma pedale giù: suona finché il pedale non si alza
        var key = 0
        var age = 0L
        val phase = FloatArray(partials) // frazione di giro 0..1
        val inc = FloatArray(partials)
        val amp = FloatArray(partials)
        val decay = FloatArray(partials) // moltiplicatore per campione dell'inviluppo
        val env = FloatArray(partials)
        var attackLeft = 0
        var attackStep = 0f
        var attackGain = 0f
    }

    private class Click(val freq: Float, var amp: Float) {
        var phase = 0f
    }

    private val voices = Array(MAX_VOICES) { Voice(PARTIALS) }
    private val clicks = ArrayList<Click>()
    private var clock = 0L
    private val releaseMul = exp(-1.0 / (RELEASE_SEC * sampleRate)).toFloat()
    private val clickDecay = exp(-1.0 / (0.008 * sampleRate)).toFloat()

    val activeVoices: Int get() = voices.count { it.active }
    private var sustain = false

    fun noteOn(key: Int, velocity: Int) {
        val v = voices.firstOrNull { it.active && it.key == key } // stesso tasto: si riusa la voce
            ?: voices.firstOrNull { !it.active }
            ?: voices.filter { it.releasing }.minByOrNull { it.env[0] } // si ruba la più spenta...
            ?: voices.minByOrNull { it.age } // ...o la più vecchia
            ?: return
        start(v, key, velocity.coerceIn(1, 127))
    }

    fun noteOff(key: Int) {
        for (v in voices) if (v.active && v.key == key) { if (sustain) v.held = true else v.releasing = true }
    }

    /** Pedale di risonanza: giù = le note lasciate continuano a suonare; su = si spengono (se non sono più tenute col dito). */
    fun setSustain(on: Boolean) {
        sustain = on
        if (!on) for (v in voices) if (v.held) { v.held = false; v.releasing = true }
    }

    fun allNotesOff() {
        sustain = false
        for (v in voices) if (v.active) { v.held = false; v.releasing = true }
    }

    /** Un colpo di metronomo: breve "tic" (più acuto e forte se [accent]). */
    fun click(accent: Boolean) {
        if (clicks.size < 8) clicks.add(Click(if (accent) 1500f else 1000f, if (accent) 0.5f else 0.35f))
    }

    /** Scrive [frames] campioni mono in [out] (sommandosi a ciò che c'è: passarlo a zero). */
    fun render(out: FloatArray, frames: Int) {
        for (v in voices) if (v.active) renderVoice(v, out, frames)
        if (clicks.isNotEmpty()) renderClicks(out, frames)
        for (i in 0 until frames) { // limitatore dolce: non satura mai
            val x = out[i] * MASTER_GAIN
            out[i] = x / (1f + abs(x))
        }
        clock += frames
    }

    private fun start(v: Voice, key: Int, velocity: Int) {
        val f0 = 440.0 * 2.0.pow((key - 69) / 12.0)
        val loud = (velocity / 127.0).pow(1.4)
        val brightness = 0.55 + 0.45 * velocity / 127.0 // più forte = più armoniche
        val tauFund = (5.0 * 2.0.pow(-(key - 48) / 18.0)).coerceIn(0.5, 6.0) // le note acute si spengono prima
        val inharmonicity = 0.0004 * (1 + (key - 21) / 40.0)
        for (k in 0 until PARTIALS) {
            val n = k + 1
            val f = f0 * n * sqrt(1.0 + inharmonicity * n * n)
            v.inc[k] = if (f < sampleRate / 2.0) (f / sampleRate).toFloat() else 0f // niente aliasing
            v.amp[k] = (loud * 0.45 / n.toDouble().pow(1.15) * brightness.pow(k.toDouble())).toFloat()
            val tau = tauFund / (1.0 + 0.9 * k)
            v.decay[k] = exp(-1.0 / (tau * sampleRate)).toFloat()
            v.env[k] = 1f
            v.phase[k] = 0f
        }
        v.key = key
        v.active = true
        v.releasing = false
        v.held = false
        v.age = clock
        v.attackLeft = (0.003 * sampleRate).toInt().coerceAtLeast(1)
        v.attackStep = 1f / v.attackLeft
        v.attackGain = 0f
    }

    private fun renderVoice(v: Voice, out: FloatArray, frames: Int) {
        val rel = if (v.releasing) releaseMul else 1f
        for (i in 0 until frames) {
            var sum = 0f
            for (k in 0 until PARTIALS) {
                val inc = v.inc[k]
                if (inc == 0f) continue
                val p = v.phase[k]
                val pos = p * TABLE_SIZE
                val idx = pos.toInt()
                val frac = pos - idx
                sum += (SINE_TABLE[idx] + (SINE_TABLE[idx + 1] - SINE_TABLE[idx]) * frac) * v.amp[k] * v.env[k]
                var np = p + inc
                if (np >= 1f) np -= 1f
                v.phase[k] = np
                v.env[k] *= v.decay[k] * rel
            }
            if (v.attackLeft > 0) {
                v.attackGain += v.attackStep
                v.attackLeft--
                sum *= v.attackGain
            }
            out[i] += sum
        }
        if (v.env[0] < SILENCE) v.active = false
    }

    private fun renderClicks(out: FloatArray, frames: Int) {
        val it = clicks.iterator()
        while (it.hasNext()) {
            val c = it.next()
            for (i in 0 until frames) {
                out[i] += sin(2.0 * PI * c.phase).toFloat() * c.amp
                c.phase += c.freq / sampleRate
                if (c.phase >= 1f) c.phase -= 1f
                c.amp *= clickDecay
            }
            if (c.amp < 0.0005f) it.remove()
        }
    }
}
