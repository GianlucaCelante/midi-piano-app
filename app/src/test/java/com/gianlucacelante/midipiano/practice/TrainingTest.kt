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
        assertEquals(1, ChordQuiz.adjustLevel(2, 2, 6))
        assertEquals(ChordQuiz.MAX_LEVEL, ChordQuiz.adjustLevel(ChordQuiz.MAX_LEVEL, 10, 10))
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

class InversionQuizTest {
    @Test fun inversionKeysAndDetectionAgree() {
        for (r in listOf(0, 2, 4, 5, 7, 9, 11)) for (q in listOf(ChordQuality.MAJOR, ChordQuality.MINOR)) for (inv in 0..2) {
            val c = ChordDetector.identify(ChordDetector.keys(r, q, 60, inv))
            assertNotNull(c); assertEquals(r, c!!.root); assertEquals(q, c.quality); assertEquals(inv, c.inversion)
        }
    }
    @Test fun levelFourAsksForTheInversion() {
        val rnd = Random(5)
        repeat(100) {
            val q = ChordQuiz.next(4, rnd, null)
            assertTrue(q.inversion in 0..2)
            assertTrue(q.matches(ChordDetector.identify(q.keys())))
            assertFalse(q.matches(ChordDetector.identify(ChordDetector.keys(q.root, q.quality, 60, (q.inversion + 1) % 3))))
        }
    }
    @Test fun optionsAtLevelFourAreDistinct() {
        val rnd = Random(9)
        val a = ChordQuiz.next(4, rnd, null)
        val o = ChordQuiz.options(a, 4, rnd)
        assertEquals(4, o.toSet().size); assertTrue(a in o)
    }
}

class TouchTest {
    private fun r(goal: Touch.Goal, v: List<Int>, t: List<Int?> = v.map { null }) = Touch.evaluate(goal, v, t, v.size)
    @Test fun evenTouchPassesWhenSteady() {
        assertTrue(r(Touch.Goal.EVEN, listOf(80, 82, 78, 81, 79, 80, 83, 80))!!.passed)
        assertFalse(r(Touch.Goal.EVEN, listOf(40, 110, 60, 100, 45, 105, 70, 95))!!.passed)
    }
    @Test fun crescendoNeedsARealRise() {
        assertTrue(r(Touch.Goal.CRESC, listOf(40, 50, 60, 70, 80, 90, 100, 110))!!.passed)
        assertFalse(r(Touch.Goal.CRESC, listOf(80, 80, 82, 81, 80, 82, 81, 80))!!.passed) // piatto: nessun crescendo
        assertFalse(r(Touch.Goal.CRESC, listOf(110, 100, 90, 80, 70, 60, 50, 40))!!.passed)
        assertTrue(r(Touch.Goal.DIM, listOf(110, 100, 90, 80, 70, 60, 50, 40))!!.passed)
    }
    @Test fun targetCountsHowCloseTheForceIs() {
        val target = List(8) { 45 }
        assertTrue(r(Touch.Goal.TARGET, List(8) { 44 }, target)!!.passed)
        assertFalse(r(Touch.Goal.TARGET, List(8) { 110 }, target)!!.passed)
    }
    @Test fun noRealForceMeansNoJudgement() {
        assertNull(Touch.evaluate(Touch.Goal.EVEN, List(8) { 0 }, List(8) { null }, 8)) // tutto dallo schermo
        assertNull(Touch.evaluate(Touch.Goal.EVEN, listOf(80, 80), listOf(null, null), 2)) // troppo poche note
    }
    @Test fun parseGoal() { assertEquals(Touch.Goal.DIM, Touch.parse("dim")); assertNull(Touch.parse(null)); assertNull(Touch.parse("x")) }
}

class PedalPlanTest {
    private val plan = PedalPlan(listOf(PedalEvent(0.5, true), PedalEvent(4.0, false), PedalEvent(4.5, true), PedalEvent(8.0, false)))
    @Test fun expectedStateFollowsTheEvents() {
        assertFalse(plan.stateAt(0.0)); assertTrue(plan.stateAt(1.0)); assertFalse(plan.stateAt(4.2)); assertTrue(plan.stateAt(5.0)); assertFalse(plan.stateAt(9.0))
    }
    @Test fun rightChangesPass() {
        val r = plan.judge(listOf(0.7 to true, 3.9 to false, 4.6 to true, 8.2 to false), 0.5)
        assertEquals(4, r.matched); assertEquals(0, r.extras); assertTrue(r.passed)
    }
    @Test fun noPedalFails() {
        val r = plan.judge(emptyList(), 0.5); assertEquals(0, r.matched); assertFalse(r.passed)
    }
    @Test fun lateOrWrongDirectionDoesNotCount() {
        val r = plan.judge(listOf(2.0 to true, 6.0 to false), 0.5)
        assertEquals(0, r.matched); assertEquals(2, r.extras)
    }
    @Test fun extraChangesLowerTheScore() {
        val clean = plan.judge(listOf(0.5 to true, 4.0 to false, 4.5 to true, 8.0 to false), 0.5)
        val noisy = plan.judge(listOf(0.5 to true, 1.5 to false, 2.0 to true, 4.0 to false, 4.5 to true, 6.0 to false, 6.5 to true, 8.0 to false), 0.5)
        assertTrue(noisy.score < clean.score)
    }
}

class TimingTest {
    @Test fun tendencyText() {
        assertTrue(Timing.summarize(-0.06, 20, null).notes[0].contains("anticipare di circa 60"))
        assertTrue(Timing.summarize(0.08, 20, null).notes[0].contains("ritardare di circa 80"))
        assertTrue(Timing.summarize(0.01, 20, null).notes[0].contains("preciso"))
        assertTrue(Timing.summarize(0.0, 3, null).notes.isEmpty()) // pochi dati
    }
    @Test fun chordSpreadText() {
        assertTrue(Timing.summarize(0.0, 20, 0.02).notes.last().contains("insieme"))
        assertTrue(Timing.summarize(0.0, 20, 0.12).notes.last().contains("120 ms"))
    }
    @Test fun engineMeasuresMeanAndChordSpread() {
        val steps = listOf(
            com.gianlucacelante.midipiano.song.Step(1.0, listOf(note(60, 1.0), note(64, 1.0), note(67, 1.0))),
            com.gianlucacelante.midipiano.song.Step(2.0, listOf(note(60, 2.0))),
        )
        val e = TempoModeEngine(steps, 0.3)
        e.onNoteOn(60, 1.02); e.onNoteOn(64, 1.06); e.onNoteOn(67, 1.10); e.onNoteOn(60, 2.04)
        assertEquals(0.08, e.chordSpreadSec()!!, 1e-9)
        assertEquals((0.02 + 0.06 + 0.10 + 0.04) / 4, e.meanDeviationSec, 1e-9)
    }
    private fun note(key: Int, start: Double) = com.gianlucacelante.midipiano.song.SongNote(start, 0.5, key, 80, com.gianlucacelante.midipiano.song.Hand.RIGHT)
}

class UpcomingWeightTest {
    @Test fun growsGraduallyFromNothingToTheMaximum() {
        assertEquals(0f, upcomingWeightAt(2.0), 1e-6f) // a 2 s: invisibile
        assertEquals(0f, upcomingWeightAt(5.0), 1e-6f)
        assertEquals(0.62f, upcomingWeightAt(0.4), 1e-6f) // quando diventa «attesa»: il massimo prima del pieno
        var prev = -1f
        var t = 2.0
        while (t >= 0.4) { val w = upcomingWeightAt(t); assertTrue(w >= prev); prev = w; t -= 0.05 } // sempre crescente avvicinandosi
        // i passi tra un fotogramma e l'altro (1/30 s) sono piccoli: niente scatti
        for (i in 0 until 48) assertTrue(upcomingWeightAt(2.0 - i / 30.0) - upcomingWeightAt(2.0 - (i + 1) / 30.0) > -0.02f)
        assertTrue(Math.abs(upcomingWeightAt(1.2) - upcomingWeightAt(1.2 - 1 / 30.0)) < 0.03f)
    }
}
