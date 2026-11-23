package com.gianlucacelante.midipiano.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class PitchDetectionTest {
    private val sr = 48000

    /** Un frammento di nota di pianoforte (dal nostro sintetizzatore), preso dopo l'attacco. */
    private fun noteFrame(key: Int, size: Int = 4096, velocity: Int = 90): FloatArray {
        val synth = PianoSynth(sr)
        synth.noteOn(key, velocity)
        val skip = FloatArray(sr / 20).also { synth.render(it, it.size) } // 50 ms: oltre l'attacco
        val out = FloatArray(size)
        synth.render(out, size)
        return out
    }

    @Test
    fun detectsPianoNotesAcrossTheKeyboard() {
        for (key in intArrayOf(36, 45, 57, 60, 64, 69, 76, 84)) {
            val p = PitchDetector.detect(noteFrame(key), sr)
            assertNotNull("nota $key non riconosciuta", p)
            assertEquals("nota $key", key, p!!.midi)
        }
    }

    @Test
    fun frequencyIsPrecise() {
        val p = PitchDetector.detect(noteFrame(69), sr)!! // La4
        assertEquals(440.0, p.frequency, 3.0) // l'inarmonicità sposta di poco
        assertTrue(p.clarity > 0.8)
    }

    @Test
    fun silenceHasNoPitch() {
        assertNull(PitchDetector.detect(FloatArray(4096), sr))
    }

    @Test
    fun noiseHasNoPitch() {
        val r = Random(1)
        assertNull(PitchDetector.detect(FloatArray(4096) { (r.nextFloat() - 0.5f) * 0.5f }, sr))
    }

    @Test
    fun rmsMeasuresTheLoudness() {
        assertEquals(0.0, PitchDetector.rms(FloatArray(100)), 1e-12)
        assertEquals(sqrt(0.5), PitchDetector.rms(floatArrayOf(1f, -1f, 0f, 0f)), 1e-6)
    }
}
