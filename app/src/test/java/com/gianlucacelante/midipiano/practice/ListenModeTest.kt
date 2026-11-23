package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenModeTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun n(t: Double, d: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, d, key, 80, hand)

    // ---- sezioni ----

    @Test
    fun sectionsSplitTheBarsInGroups() {
        assertEquals(listOf(1..4, 5..8, 9..10), Sections.of(barCount = 10, perSection = 4))
        assertEquals(listOf(1..3), Sections.of(3, 4))
        assertEquals(listOf(1..1), Sections.of(1, 4))
    }

    @Test
    fun anEmptySongHasASingleSection() {
        assertEquals(listOf(1..1), Sections.of(0, 4))
    }

    @Test
    fun sectionLabelsShowTheBarRange() {
        assertEquals("Battute 1–4", Sections.label(1..4))
        assertEquals("Battuta 9", Sections.label(9..9))
    }

    // ---- pianificazione delle note ----

    @Test
    fun dueReturnsEventsInTheWindowAfterTheLowerBoundUpToTheUpperOne() {
        val s = NoteScheduler(listOf(n(1.0, 0.5, 60), n(2.0, 1.0, 62)), Hand.BOTH)
        val e = s.due(0.5, 1.5)
        assertEquals(listOf(1.0 to true, 1.5 to false), e.map { it.timeSec to it.on })
        assertTrue(s.due(1.5, 1.9).isEmpty()) // 1.5 escluso (già dato), niente fino a 2.0
    }

    @Test
    fun consecutiveWindowsNeverRepeatOrSkipEvents() {
        val s = NoteScheduler(listOf(n(0.0, 0.3, 60), n(0.5, 0.2, 62), n(0.9, 0.4, 64)), Hand.BOTH)
        val all = ArrayList<NoteEvent>()
        var t = -1.0
        while (t < 2.0) {
            all += s.due(t, t + 0.137)
            t += 0.137
        }
        assertEquals(6, all.size) // 3 note = 3 inizi + 3 fini
        assertEquals(all.sortedBy { it.timeSec }, all)
    }

    @Test
    fun theReleaseComesBeforeTheNextOnForTheSameKey() {
        val s = NoteScheduler(listOf(n(0.0, 1.0, 60), n(1.0, 1.0, 60)), Hand.BOTH)
        val atOne = s.due(0.5, 1.0)
        assertEquals(listOf(false, true), atOne.map { it.on }) // prima si rilascia, poi si ribatte
    }

    @Test
    fun theHandFilterKeepsOnlyThatHand() {
        val s = NoteScheduler(listOf(n(0.0, 1.0, 72, Hand.RIGHT), n(0.0, 1.0, 48, Hand.LEFT)), Hand.LEFT)
        assertEquals(setOf(48), s.due(-1.0, 5.0).map { it.key }.toSet())
    }

    @Test
    fun veryShortNotesStillReleaseAfterTheyStart() {
        val s = NoteScheduler(listOf(n(1.0, 0.0, 60)), Hand.BOTH)
        val e = s.due(0.0, 2.0)
        assertEquals(listOf(true, false), e.map { it.on })
        assertTrue(e[1].timeSec > e[0].timeSec)
    }

    // ---- modalità Ascolta nella sessione ----

    private val song = Song(
        "s", "t", null, SongSource("t", "t"),
        listOf(n(0.0, 1.0, 60), n(1.0, 1.0, 62), n(2.0, 1.0, 64)), secPerBeat = 0.25,
    )

    private fun listening(): PracticeSession {
        val s = PracticeSession(song, Hand.BOTH)
        s.countInEnabled = false
        s.setMode(StudyMode.LISTEN, 0L)
        return s
    }

    @Test
    fun listenModeAdvancesTheClockWithoutJudgingNotes() {
        val s = listening()
        s.play(0L)
        s.tick(ns(2.5))
        assertEquals(0, s.mistakes)
        assertEquals(0, s.misses)
    }

    @Test
    fun notesPlayedWhileListeningAreIgnored() {
        val s = listening()
        s.play(0L)
        assertEquals(NoteResult.IGNORED, s.onNoteOn(60, ns(0.1)))
        assertEquals(0, s.mistakes)
    }

    @Test
    fun expectedKeysAreTheNotesSoundingNow() {
        val s = listening()
        s.play(0L)
        s.tick(ns(1.5))
        assertEquals(setOf(62), s.expectedKeys)
    }

    @Test
    fun listenModeEndsAtTheEndOfTheSong() {
        val s = listening()
        s.play(0L)
        assertTrue(s.tick(ns(10.0)).finished)
        assertFalse(s.isPlaying)
    }
}
