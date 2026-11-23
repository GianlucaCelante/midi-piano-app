package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackingTest {
    @Test
    fun theAppPlaysTheHandTheUserIsNotPlaying() {
        assertEquals(Hand.LEFT, Backing.handToPlay(Hand.RIGHT))
        assertEquals(Hand.RIGHT, Backing.handToPlay(Hand.LEFT))
        assertNull(Backing.handToPlay(Hand.BOTH))
    }

    @Test
    fun itOnlyPlaysInTempoModeWithSoundOnAndNotDuringPreviewOrCountIn() {
        assertTrue(Backing.active(enabled = true, mode = StudyMode.TEMPO, hand = Hand.RIGHT, sound = true, previewing = false, countingIn = false))
        assertFalse(Backing.active(false, StudyMode.TEMPO, Hand.RIGHT, true, false, false))
        assertFalse(Backing.active(true, StudyMode.WAIT, Hand.RIGHT, true, false, false))
        assertFalse(Backing.active(true, StudyMode.LISTEN, Hand.RIGHT, true, false, false))
        assertFalse(Backing.active(true, StudyMode.TEMPO, Hand.BOTH, true, false, false))
        assertFalse(Backing.active(true, StudyMode.TEMPO, Hand.RIGHT, false, false, false))
        assertFalse(Backing.active(true, StudyMode.TEMPO, Hand.RIGHT, true, true, false))
        assertFalse(Backing.active(true, StudyMode.TEMPO, Hand.RIGHT, true, false, true))
    }

    @Test
    fun theBackingSchedulerOnlyContainsTheOtherHandsNotes() {
        val notes = listOf(SongNote(0.0, 0.5, 72, 80, Hand.RIGHT), SongNote(0.0, 0.5, 48, 80, Hand.LEFT), SongNote(1.0, 0.5, 74, 80, Hand.RIGHT))
        val sched = NoteScheduler(notes, Backing.handToPlay(Hand.RIGHT)!!)
        assertEquals(listOf(48, 48), sched.due(-1.0, 5.0).map { it.key })
    }
}
