package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.notation.Fingering
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.MidiFileParser
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeftHandKeysTest {
    private fun n(t: Double, key: Int, hand: Hand) = SongNote(t, 0.4, key, 80, hand)
    // t=0: destra Do5 + sinistra Do3,Mi3 ; t=1: destra Re5 ; t=2: sinistra Sol2 ; t=3: destra Mi5 + sinistra Do3
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(
        n(0.0, 72, Hand.RIGHT), n(0.0, 48, Hand.LEFT), n(0.0, 52, Hand.LEFT),
        n(1.0, 74, Hand.RIGHT), n(2.0, 43, Hand.LEFT), n(3.0, 76, Hand.RIGHT), n(3.0, 48, Hand.LEFT)))

    @Test
    fun bothHandsShowEveryExpectedKeyWithItsHand() {
        val s = PracticeSession(song, Hand.BOTH)
        val e = s.expectedNotes()
        assertEquals(setOf(72, 48, 52), e.map { it.key }.toSet())
        assertEquals(Hand.LEFT, e.first { it.key == 48 }.hand)
        assertEquals(Hand.RIGHT, e.first { it.key == 72 }.hand)
    }

    @Test
    fun leftHandAloneOnlyAsksForLeftNotes() {
        val s = PracticeSession(song, Hand.LEFT)
        assertEquals(setOf(48, 52), s.expectedNotes().map { it.key }.toSet())
        assertTrue(s.expectedNotes().all { it.hand == Hand.LEFT })
        s.onNoteOn(48, 0L); s.onNoteOn(52, 0L)
        assertEquals(setOf(43), s.expectedNotes().map { it.key }.toSet()) // il passo dopo, a sinistra
    }

    @Test
    fun upcomingNotesIncludeTheLeftHandOfTheNextSteps() {
        val s = PracticeSession(song, Hand.BOTH)
        val up = s.upcomingNotes()
        assertEquals(setOf(74, 43), up.map { it.key }.toSet()) // passi a 1 s (destra) e 2 s (sinistra)
        assertEquals(Hand.LEFT, up.first { it.key == 43 }.hand)
    }

    @Test
    fun upcomingForLeftOnlyDoesNotShowTheRightHand() {
        val s = PracticeSession(song, Hand.LEFT)
        assertTrue(s.upcomingNotes().all { it.hand == Hand.LEFT })
    }

    @Test
    fun everyLeftNoteGetsAFinger() {
        val f = Fingering.assign(song.notes)
        for (x in song.notes) assertTrue(f.containsKey(Fingering.id(x.startSec, x.key)))
    }

    @Test
    fun bundledMidiSongsHaveALeftHandAndItStaysBelowMostOfTheRightHand() {
        val dir = File("src/main/assets/songs")
        val files = dir.listFiles { x -> x.extension == "mid" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val sg = MidiFileParser.parse(f.readBytes(), f.nameWithoutExtension, f.nameWithoutExtension, null, SongSource("t", "t"))
            val l = sg.notes.filter { it.hand == Hand.LEFT }
            val r = sg.notes.filter { it.hand == Hand.RIGHT }
            println("${f.name}: sinistra=${l.size} destra=${r.size} medie ${l.map { it.key }.average().toInt()} / ${r.map { it.key }.average().toInt()}")
            assertTrue("${f.name}: nessuna nota", l.size + r.size > 0)
            if (l.isNotEmpty() && r.isNotEmpty()) assertTrue("${f.name}: mani invertite", l.map { it.key }.average() < r.map { it.key }.average())
        }
    }
}
