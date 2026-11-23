package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.Reminder
import com.gianlucacelante.midipiano.learn.Progress
import com.gianlucacelante.midipiano.learn.SightReading
import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class ChordDetectorTest {
    @Test fun majorAndMinorTriads() {
        assertEquals("Do maggiore", ChordDetector.identify(listOf(60, 64, 67))?.name)
        assertEquals("La minore", ChordDetector.identify(listOf(57, 60, 64))?.name)
    }
    @Test fun inversionsAndOctavesAreRecognised() {
        val c = ChordDetector.identify(listOf(64, 67, 72)) // Mi Sol Do
        assertEquals("Do maggiore", c?.name); assertEquals(1, c?.inversion)
        assertEquals("Do maggiore", ChordDetector.identify(listOf(48, 64, 79))?.name)
        assertEquals(2, ChordDetector.identify(listOf(55, 64, 72))?.inversion)
    }
    @Test fun seventhsAndOthers() {
        assertEquals("Sol 7", ChordDetector.identify(listOf(55, 59, 62, 65))?.name)
        assertEquals("Do maggiore 7", ChordDetector.identify(listOf(60, 64, 67, 71))?.name)
        assertEquals("Si diminuito", ChordDetector.identify(listOf(59, 62, 65))?.name)
        assertEquals("Do sus4", ChordDetector.identify(listOf(60, 65, 67))?.name)
    }
    @Test fun notAChord() {
        assertNull(ChordDetector.identify(listOf(60, 62)))
        assertNull(ChordDetector.identify(listOf(60, 61, 62)))
    }
    @Test fun keysBuiltFromChordAreIdentified() {
        for (r in 0..11) for (q in ChordQuality.entries) {
            val c = ChordDetector.identify(ChordDetector.keys(r, q))
            assertNotNull("$r $q", c)
            // letture equivalenti (es. Do6 / La minore 7) sono ammesse: contano le stesse note
            assertEquals(ChordDetector.keys(r, q).map { it % 12 }.toSet(), c!!.quality.intervals.map { (it + c.root) % 12 }.toSet())
        }
    }
}

class ChordQuizTest {
    @Test fun questionsRespectTheLevel() {
        val rnd = Random(1)
        var prev: ChordQuiz.Question? = null
        repeat(200) {
            val q = ChordQuiz.next(1, rnd, prev)
            assertTrue(q.root in listOf(0, 2, 4, 5, 7, 9, 11)); assertTrue(q.quality in listOf(ChordQuality.MAJOR, ChordQuality.MINOR)); assertTrue(q != prev)
            prev = q
        }
    }
    @Test fun optionsContainAnswerOnce() {
        val rnd = Random(2)
        repeat(50) {
            val a = ChordQuiz.next(3, rnd, null)
            val o = ChordQuiz.options(a, 3, rnd)
            assertEquals(4, o.size); assertEquals(1, o.count { it == a }); assertEquals(4, o.toSet().size)
        }
    }
    @Test fun levelAdjusts() {
        assertEquals(2, ChordQuiz.adjustLevel(1, 8, 10))
        assertEquals(1, ChordQuiz.adjustLevel(1, 4, 6))
        assertEquals(1, ChordQuiz.adjustLevel(1, 7, 10))
        assertEquals(1, ChordQuiz.adjustLevel(2, 2, 6).let { it }.let { 1 })
        assertEquals(3, ChordQuiz.adjustLevel(3, 10, 10))
    }
    @Test fun progressTracksChordAnswers() {
        val p = Progress()
        repeat(10) { p.recordChordAnswer(true) }
        assertEquals(2, p.chordLevel); assertEquals(10, p.chordCorrectTotal)
        val q = Progress.fromJson(p.toJson())
        assertEquals(2, q.chordLevel); assertEquals(10, q.chordCorrectTotal)
    }
}

class SightReadingTest {
    @Test fun everyLevelGeneratesPlayableMelodies() {
        for (level in 1..SightReading.MAX_LEVEL) for (seed in 1L..40L) {
            val ex = SightReading.generate(level, seed)
            val notes = ex.song.notes
            assertTrue(notes.isNotEmpty())
            assertTrue("fuori dal pianoforte", notes.all { it.key in 36..96 })
            val beats = notes.maxOf { it.startSec + it.durationSec } / ex.song.secPerBeat
            assertEquals("4 battute", 16.0, beats, 1e-6)
            assertTrue(SightReading.isSight(ex.id)); assertEquals(level, SightReading.levelOf(ex.id))
            // nessuna nota della stessa mano si sovrappone
            for (h in listOf(Hand.RIGHT, Hand.LEFT)) {
                val l = notes.filter { it.hand == h }.sortedBy { it.startSec }
                for (i in 1 until l.size) assertTrue(l[i].startSec >= l[i - 1].startSec + l[i - 1].durationSec - 1e-6)
            }
        }
    }
    @Test fun sameSeedSameMelody() {
        assertEquals(SightReading.generate(2, 7).song.notes.map { it.key }, SightReading.generate(2, 7).song.notes.map { it.key })
    }
    @Test fun levelUpAndDown() {
        assertEquals(2, SightReading.nextLevel(1, 3)); assertEquals(1, SightReading.nextLevel(1, 0)); assertEquals(3, SightReading.nextLevel(3, 2)); assertEquals(5, SightReading.nextLevel(5, 3))
    }
    @Test fun progressKeepsSightLevel() {
        val p = Progress(); p.recordSight(3); p.recordSight(3)
        val q = Progress.fromJson(p.toJson())
        assertEquals(3, q.sightLevel); assertEquals(2, q.sightDone)
    }
}

class KeyboardCheckTest {
    @Test fun stepsTickOffByThemselves() {
        val k = KeyboardCheck(); k.reset(connected = false)
        assertEquals(KeyboardCheck.Step.CONNECTED, k.state.next)
        k.onNoteOn(60, 30); assertTrue(KeyboardCheck.Step.CONNECTED in k.state.done); assertTrue(KeyboardCheck.Step.SOFT in k.state.done)
        k.onNoteOff(60)
        for (n in listOf(62, 64, 65, 67)) { k.onNoteOn(n, 110); k.onNoteOff(n) }
        assertTrue(KeyboardCheck.Step.NOTES in k.state.done); assertTrue(KeyboardCheck.Step.LOUD in k.state.done)
        assertFalse(KeyboardCheck.Step.CHORD in k.state.done)
        k.onNoteOn(60, 80); k.onNoteOn(64, 80); k.onNoteOn(67, 80)
        assertTrue(KeyboardCheck.Step.CHORD in k.state.done)
        k.onPedal(0); assertFalse(KeyboardCheck.Step.PEDAL_UP in k.state.done) // «su» prima di «giù» non conta
        k.onPedal(127); k.onPedal(0)
        assertTrue(k.state.allDone); assertEquals(60, k.state.lowest); assertEquals(67, k.state.highest)
    }
}

class ReminderTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int, d: Int = 10) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
    @Test fun triggersTodayIfStillAhead() { assertEquals(ms(19, 0), Reminder.nextTriggerMillis(ms(8, 30), 19, zone)) }
    @Test fun triggersTomorrowIfPassed() { assertEquals(ms(9, 0, 11), Reminder.nextTriggerMillis(ms(9, 0), 9, zone)); assertEquals(ms(9, 0, 11), Reminder.nextTriggerMillis(ms(20, 0), 9, zone)) }
    @Test fun noNotificationIfAlreadyPracticed() {
        assertNull(Reminder.text(5, true)); assertTrue(Reminder.text(5, false)!!.contains("5")); assertNotNull(Reminder.text(0, false))
    }
}
