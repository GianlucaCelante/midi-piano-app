package com.gianlucacelante.midipiano.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PianoSynthTest {
    private val sr = 48000

    private fun render(synth: PianoSynth, seconds: Double): FloatArray {
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        var done = 0
        while (done < n) {
            val chunk = minOf(256, n - done)
            val buf = FloatArray(chunk)
            synth.render(buf, chunk)
            System.arraycopy(buf, 0, out, done, chunk)
            done += chunk
        }
        return out
    }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from).coerceAtLeast(1))
    }

    /** Ampiezza della componente a [freq] Hz (Goertzel semplificato con correlazione). */
    private fun magnitude(a: FloatArray, freq: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in a.indices) {
            val ph = 2 * PI * freq * i / sr
            re += a[i] * cos(ph)
            im += a[i] * sin(ph)
        }
        return sqrt(re * re + im * im) / a.size
    }

    @Test
    fun silentWithoutNotes() {
        val out = render(PianoSynth(sr), 0.2)
        assertEquals(0.0, rms(out), 1e-12)
    }

    @Test
    fun noteProducesSoundAtItsPitch() {
        val s = PianoSynth(sr)
        s.noteOn(69, 100) // La4 = 440 Hz
        val out = render(s, 0.5)
        assertTrue(rms(out) > 0.01)
        val at440 = magnitude(out, 440.0)
        assertTrue(at440 > 4 * magnitude(out, 415.3)) // Sol#4
        assertTrue(at440 > 4 * magnitude(out, 466.2)) // La#4
    }

    @Test
    fun anOctaveLowerIsHalfTheFrequency() {
        val s = PianoSynth(sr)
        s.noteOn(57, 100) // La3 = 220 Hz
        val out = render(s, 0.5)
        val at220 = magnitude(out, 220.0)
        assertTrue(at220 > 4 * magnitude(out, 207.65)) // Sol#3
        assertTrue(at220 > 4 * magnitude(out, 233.08)) // La#3
    }

    @Test
    fun soundDecaysWhileTheKeyIsHeld() {
        val s = PianoSynth(sr)
        s.noteOn(72, 100)
        val out = render(s, 3.0)
        assertTrue(rms(out, 0, sr / 10) > 2 * rms(out, 2 * sr, 3 * sr)) // i primi 0,1 s sono molto più forti dell'ultimo secondo
    }

    @Test
    fun releasingTheKeyDampsTheSoundQuickly() {
        val s = PianoSynth(sr)
        s.noteOn(60, 100)
        val held = render(s, 0.3)
        s.noteOff(60)
        val after = render(s, 0.6)
        assertTrue(rms(after, sr / 2, after.size) < 0.05 * rms(held, sr / 10, held.size))
    }

    @Test
    fun harderKeysAreLouder() {
        fun level(vel: Int): Double {
            val s = PianoSynth(sr)
            s.noteOn(64, vel)
            return rms(render(s, 0.2))
        }
        assertTrue(level(120) > 2 * level(40))
    }

    @Test
    fun neverClipsEvenWithManyLoudNotes() {
        val s = PianoSynth(sr)
        for (k in 40 until 70) s.noteOn(k, 127)
        val out = render(s, 0.3)
        assertTrue(out.all { abs(it) <= 1.0f })
        assertFalse(out.any { it.isNaN() })
    }

    @Test
    fun polyphonyIsLimitedAndOldestVoicesAreStolen() {
        val s = PianoSynth(sr)
        for (k in 21 until 109) s.noteOn(k, 90) // 88 note
        assertTrue(s.activeVoices <= PianoSynth.MAX_VOICES)
        val out = render(s, 0.1)
        assertTrue(rms(out) > 0.0)
    }

    @Test
    fun retriggeringTheSameKeyDoesNotLeakVoices() {
        val s = PianoSynth(sr)
        repeat(50) { s.noteOn(60, 100) }
        assertEquals(1, s.activeVoices)
    }

    @Test
    fun voicesEndAfterTheirSoundDiesOut() {
        val s = PianoSynth(sr)
        s.noteOn(60, 100)
        s.noteOff(60)
        render(s, 2.0)
        assertEquals(0, s.activeVoices)
    }

    @Test
    fun clickIsAShortBurst() {
        val s = PianoSynth(sr)
        s.click(accent = true)
        val out = render(s, 0.3)
        assertTrue(rms(out, 0, sr / 50) > 0.02) // primi 20 ms
        assertTrue(rms(out, sr / 5, out.size) < 0.001) // poi silenzio
    }

    @Test
    fun accentedClickIsHigherAndLouderThanAPlainOne() {
        fun peak(accent: Boolean): Float {
            val s = PianoSynth(sr)
            s.click(accent)
            return render(s, 0.05).maxOf { abs(it) }
        }
        assertTrue(peak(true) > peak(false))
    }

    @Test
    fun allNotesOffSilencesEverything() {
        val s = PianoSynth(sr)
        for (k in 50 until 60) s.noteOn(k, 100)
        s.allNotesOff()
        render(s, 1.0)
        assertEquals(0, s.activeVoices)
    }

    private fun rms(synth: PianoSynth, frames: Int): Double {
        val out = FloatArray(frames)
        synth.render(out, frames)
        return Math.sqrt(out.sumOf { (it * it).toDouble() } / frames)
    }

    @Test
    fun theSustainPedalKeepsReleasedNotesSounding() {
        fun tailLevel(pedal: Boolean): Double {
            val s = PianoSynth(44100)
            if (pedal) s.setSustain(true)
            s.noteOn(60, 100)
            rms(s, 4410)
            s.noteOff(60)
            rms(s, 13230)       // 0,3 s dopo il rilascio
            return rms(s, 2048)
        }
        val without = tailLevel(false)
        val with = tailLevel(true)
        assertTrue("senza pedale $without, con pedale $with", with > without * 20 && with > 1e-3)
    }

    @Test
    fun liftingThePedalReleasesTheHeldNotes() {
        val s = PianoSynth(44100)
        s.setSustain(true)
        s.noteOn(60, 100); rms(s, 4410)
        s.noteOff(60); rms(s, 8820)
        val held = rms(s, 2048)
        s.setSustain(false)
        rms(s, 22050)
        val after = rms(s, 2048)
        assertTrue("tenuta $held, rilasciata $after", after < held * 0.05)
    }

    @Test
    fun aKeyPressedAgainWhilePedalIsDownIsNotReleasedByTheOldNoteOff() {
        val s = PianoSynth(44100)
        s.setSustain(true)
        s.noteOn(60, 100); s.noteOff(60)
        s.noteOn(60, 100); rms(s, 4410)
        s.setSustain(false)
        assertTrue(rms(s, 2048) > 1e-3) // la nota è ancora tenuta col dito
    }
}
