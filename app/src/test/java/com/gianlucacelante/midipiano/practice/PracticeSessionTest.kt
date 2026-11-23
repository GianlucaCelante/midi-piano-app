package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {
    private fun n(t: Double, key: Int, hand: Hand) = SongNote(t, 0.5, key, 80, hand)

    // t=0: DO destra (72) + DO sinistra (48); t=1: RE destra (74); t=2: SOL sinistra (43)
    private val song = Song(
        "s", "Prova", null, SongSource("test", "test"),
        listOf(n(0.0, 72, Hand.RIGHT), n(0.0, 48, Hand.LEFT), n(1.0, 74, Hand.RIGHT), n(2.0, 43, Hand.LEFT)),
    )

    @Test
    fun bothHandsRequiresEveryNoteOfTheStep() {
        val s = PracticeSession(song, Hand.BOTH)
        assertEquals(3, s.totalSteps)
        assertEquals(setOf(72, 48), s.expectedKeys)
        s.onNoteOn(72)
        assertEquals(setOf(48), s.expectedKeys)
        s.onNoteOn(48)
        assertEquals(1, s.stepIndex)
    }

    @Test
    fun singleHandOnlyAsksForThatHand() {
        val right = PracticeSession(song, Hand.RIGHT)
        assertEquals(2, right.totalSteps)
        assertEquals(setOf(72), right.expectedKeys)
        val left = PracticeSession(song, Hand.LEFT)
        assertEquals(setOf(48), left.expectedKeys)
    }

    @Test
    fun progressGoesFromZeroToOne() {
        val s = PracticeSession(song, Hand.RIGHT)
        assertEquals(0f, s.progress, 1e-6f)
        s.onNoteOn(72)
        assertEquals((1.0 / song.durationSec).toFloat(), s.progress, 1e-6f) // la posizione è il tempo del passo atteso (stessa della barra)
        s.onNoteOn(74)
        assertEquals(1f, s.progress, 1e-6f)
        assertTrue(s.isFinished)
    }

    @Test
    fun changingHandKeepsThePlaceInWaitMode() {
        val s = PracticeSession(song, Hand.BOTH)
        s.onNoteOn(72); s.onNoteOn(48) // il primo passo è fatto: si aspetta il Re (1 s)
        s.setHand(Hand.LEFT)
        assertEquals(setOf(43), s.expectedKeys) // il primo passo della sinistra da 1 s in poi (Sol, a 2 s)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun mistakesAreCountedAndResetOnRestart() {
        val s = PracticeSession(song, Hand.RIGHT)
        s.onNoteOn(60)
        s.onNoteOn(61)
        assertEquals(2, s.mistakes)
        s.restart()
        assertEquals(0, s.mistakes)
        assertFalse(s.isFinished)
    }

    @Test
    fun seekJumpsToAFractionOfTheSongAndKeepsTheHand() {
        val s = PracticeSession(song, Hand.RIGHT) // 2 passi
        s.onNoteOn(72)
        s.seekTo(0f)
        assertEquals(0, s.stepIndex)
        assertEquals(setOf(72), s.expectedKeys)
        s.seekTo(0.5f)
        assertEquals(1, s.stepIndex)
        assertEquals(setOf(74), s.expectedKeys)
        assertEquals(Hand.RIGHT, s.hand)
    }

    @Test
    fun seekIsClampedAndResetsMistakes() {
        val s = PracticeSession(song, Hand.RIGHT)
        s.onNoteOn(60)
        s.seekTo(5f)
        assertEquals(1, s.stepIndex) // l'ultimo passo, non oltre la fine
        s.seekTo(-1f)
        assertEquals(0, s.stepIndex)
        assertEquals(0, s.mistakes)
    }

    @Test
    fun currentTimeFollowsTheCurrentStep() {
        val s = PracticeSession(song, Hand.RIGHT)
        assertEquals(0.0, s.currentTimeSec, 1e-9)
        s.onNoteOn(72)
        assertEquals(1.0, s.currentTimeSec, 1e-9)
    }
}
