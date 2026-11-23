package com.gianlucacelante.midipiano.audio

import kotlin.math.log10
import kotlin.math.roundToInt

sealed interface MicEvent {
    data class On(val midi: Int, val velocity: Int) : MicEvent
    data class Off(val midi: Int) : MicEvent
}

/**
 * Trasforma la sequenza di fotogrammi del microfono (volume e altezza riconosciuta) in note premute
 * e rilasciate: una nota comincia quando la stessa altezza resta stabile per qualche fotogramma, finisce
 * quando cambia o quando il suono sparisce; un forte aumento di volume sulla stessa altezza è un nuovo colpo.
 */
class NoteTracker(
    private val minRms: Double = 0.01,
    private val stableFrames: Int = 2,
    private val releaseFrames: Int = 3,
    private val retriggerFactor: Double = 2.2,
) {
    private var candidate = -1
    private var candidateCount = 0
    private var current: Int? = null
    private var silent = 0
    private var lastRms = 0.0

    fun onFrame(rms: Double, pitch: PitchDetector.Pitch?): List<MicEvent> {
        val events = ArrayList<MicEvent>(2)
        val usable = pitch != null && rms >= minRms
        if (!usable) {
            candidate = -1
            candidateCount = 0
            silent++
            if (silent >= releaseFrames) current?.let { events.add(MicEvent.Off(it)); current = null }
            lastRms = rms
            return events
        }
        silent = 0
        val midi = pitch!!.midi
        if (midi == candidate) candidateCount++ else { candidate = midi; candidateCount = 1 }

        if (candidateCount >= stableFrames && current != midi) {
            current?.let { events.add(MicEvent.Off(it)) }
            events.add(MicEvent.On(midi, velocityFor(rms)))
            current = midi
        } else if (current == midi && lastRms > 0.0 && rms > lastRms * retriggerFactor) { // nuovo colpo sulla stessa nota
            events.add(MicEvent.Off(midi))
            events.add(MicEvent.On(midi, velocityFor(rms)))
        }
        lastRms = rms
        return events
    }

    private fun velocityFor(rms: Double): Int = (60 + 20 * log10(rms.coerceAtLeast(1e-4)) + 20).roundToInt().coerceIn(1, 127)

}
