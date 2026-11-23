package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * I tasti attesi e quelli in arrivo, a tempo, devono restare identici a una ricerca ingenua su tutte le note
 * (la versione veloce usa la ricerca binaria e indici per tasto).
 */
class UpcomingScanTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()

    private fun randomSong(seed: Int, count: Int = 500, length: Double = 240.0): Song {
        val rnd = Random(seed)
        val notes = ArrayList<SongNote>()
        var t = 0.0
        while (notes.size < count) {
            t += listOf(0.25, 0.25, 0.5, 0.5, 1.0, 1.5)[rnd.nextInt(6)] * length / count * 2
            val chord = if (rnd.nextInt(4) == 0) 3 else if (rnd.nextInt(3) == 0) 2 else 1
            val base = 48 + rnd.nextInt(30)
            repeat(chord) { i -> notes += SongNote(t, 0.4, base + i * 4, 80, if (base + i * 4 < 60) Hand.LEFT else Hand.RIGHT, beats = 1.0) }
        }
        return Song("r$seed", "t", null, SongSource("t", "t"), notes.sortedWith(compareBy({ it.startSec }, { it.key })), barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)
    }

    private fun expectedRef(s: PracticeSession, song: Song, t: Double): Set<Int> =
        song.notes.filter { s.hand == Hand.BOTH || it.hand == s.hand }
            .filter { s.hitStateOf(it.key, it.startSec) == HitState.PENDING && it.startSec >= t - 0.2 && it.startSec <= t + 0.4 }
            .map { it.key }.toSet()

    private fun upcomingRef(s: PracticeSession, song: Song, t: Double, now: Set<Int>): List<SongNote> {
        val list = song.notes.filter { s.hand == Hand.BOTH || it.hand == s.hand }
            .filter { s.hitStateOf(it.key, it.startSec) == HitState.PENDING && it.startSec > t + 0.4 && it.startSec <= t + 2.0 + 0.2 }
        val times = list.map { it.startSec }.distinct().sorted().take(2)
        return list.filter { it.startSec in times }.filter { it.key !in now }.distinctBy { it.key }
    }

    @Test fun expectedAndUpcomingMatchTheNaiveScanThroughoutASong() {
        for (seed in 1..3) {
            val song = randomSong(seed)
            val s = PracticeSession(song)
            s.countInEnabled = false
            s.setMode(StudyMode.TEMPO, 0L)
            s.play(0L)
            var t = 0.0
            val rnd = Random(seed * 7)
            var checks = 0
            while (t < 230.0) {
                s.tick(ns(t))
                // ogni tanto si suona giusta una nota attesa: così alcune diventano «colpite» e non più in attesa
                if (rnd.nextInt(3) == 0) s.expectedKeys.firstOrNull()?.let { s.onNoteOn(it, ns(t)) }
                val now = s.currentTimeSec
                val exp = s.expectedKeys
                assertEquals("attesi a t=$now (seed $seed)", expectedRef(s, song, now), exp)
                val up = s.upcomingNotes()
                val ref = upcomingRef(s, song, now, exp)
                assertEquals("in arrivo a t=$now (seed $seed)", ref.map { it.key to it.startSec }, up.map { it.key to it.startSec })
                // la nota scelta per ogni tasto atteso è la più vicina nel tempo
                for (n in s.expectedNotes()) assertTrue(n.key in exp)
                checks++
                t += 0.173
            }
            assertTrue(checks > 1000)
        }
    }

    @Test fun expectedNotesPickTheClosestNoteOfEachKey() {
        val song = randomSong(9)
        val s = PracticeSession(song)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        var t = 0.0
        while (t < 200.0) {
            s.tick(ns(t))
            val now = s.currentTimeSec
            for (n in s.expectedNotes()) {
                val best = song.notes.filter { it.key == n.key }.minByOrNull { kotlin.math.abs(it.startSec - now) }!!
                assertEquals(best.startSec, n.startSec, 1e-9)
            }
            t += 0.31
        }
    }
}
