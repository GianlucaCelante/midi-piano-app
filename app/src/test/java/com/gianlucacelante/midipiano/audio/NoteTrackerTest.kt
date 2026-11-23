package com.gianlucacelante.midipiano.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTrackerTest {
    private fun pitch(midi: Int) = PitchDetector.Pitch(440.0, midi, 0.95)

    @Test
    fun aStablePitchStartsOneNoteOnce() {
        val t = NoteTracker()
        val events = (1..6).flatMap { t.onFrame(0.2, pitch(60)) }
        assertEquals(listOf(MicEvent.On(60, events.filterIsInstance<MicEvent.On>().single().velocity)), events)
    }

    @Test
    fun aSingleFrameBlipIsIgnored() {
        val t = NoteTracker()
        assertTrue(t.onFrame(0.2, pitch(60)).isEmpty()) // un solo fotogramma: non basta
        assertTrue(t.onFrame(0.2, null).isEmpty())
        assertTrue(t.onFrame(0.2, pitch(62)).isEmpty())
    }

    @Test
    fun changingPitchReleasesTheOldNoteAndStartsTheNewOne() {
        val t = NoteTracker()
        repeat(4) { t.onFrame(0.2, pitch(60)) }
        val events = (1..4).flatMap { t.onFrame(0.2, pitch(64)) }
        assertEquals(listOf(MicEvent.Off(60), MicEvent.On(64, (events[1] as MicEvent.On).velocity)), events)
    }

    @Test
    fun silenceReleasesTheNoteAfterAFewFrames() {
        val t = NoteTracker()
        repeat(4) { t.onFrame(0.2, pitch(60)) }
        val events = (1..6).flatMap { t.onFrame(0.0, null) }
        assertEquals(listOf<MicEvent>(MicEvent.Off(60)), events)
    }

    @Test
    fun aSuddenIncreaseOfLoudnessRetriggersTheSameNote() {
        val t = NoteTracker()
        repeat(4) { t.onFrame(0.05, pitch(60)) }
        val events = t.onFrame(0.5, pitch(60)) // nuovo colpo sullo stesso tasto
        assertEquals(listOf(MicEvent.Off(60)), events.filterIsInstance<MicEvent.Off>())
        assertTrue(events.any { it is MicEvent.On && it.midi == 60 })
    }

    @Test
    fun tooQuietSoundsAreNotNotes() {
        val t = NoteTracker()
        assertTrue((1..10).flatMap { t.onFrame(0.001, pitch(60)) }.isEmpty())
    }

    @Test
    fun velocityFollowsTheLoudness() {
        val soft = NoteTracker().let { t -> (1..4).flatMap { t.onFrame(0.03, pitch(60)) }.filterIsInstance<MicEvent.On>().single().velocity }
        val loud = NoteTracker().let { t -> (1..4).flatMap { t.onFrame(0.4, pitch(60)) }.filterIsInstance<MicEvent.On>().single().velocity }
        assertTrue(loud > soft)
        assertTrue(soft in 1..127 && loud in 1..127)
    }
}
