package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Le statistiche (logica pura) calcolate dai record dei giri. */
class AnalyticsTest {
    private val zone = ZoneOffset.UTC
    private fun day(d: Int, hour: Int = 10) = LocalDate.of(2026, 11, d).atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun rec(
        id: String = "e1", day: Int = 1, hour: Int = 10, outcome: String = "passed", accuracy: Double = 0.9, hand: String = "BOTH", mode: String = "TEMPO",
        hits: Int = 9, misses: Int = 1, wrong: Int = 1, speed: Double = 1.0, source: String = "exercise", meanDev: Int = 0, early: Int = 0, late: Int = 0, offBeat: Int = 0, noteHits: Int = 9,
    ) = RunRecord(whenMillis = day(day, hour), source = source, id = id, title = id.uppercase(), mode = mode, hand = hand, speed = speed, outcome = outcome,
        hits = hits, misses = misses, wrong = wrong, accuracy = accuracy, noteHits = noteHits, early = early, late = late, offBeat = offBeat, meanDevMs = meanDev)

    @Test fun overviewCountsRunsAndOutcomes() {
        val s = Analytics.compute(listOf(rec(outcome = "passed"), rec(outcome = "failed"), rec(outcome = "abandoned"), rec(outcome = "finished", source = "song")), zone)
        assertEquals(4, s.runs); assertEquals(2, s.finished); assertEquals(1, s.failed); assertEquals(1, s.abandoned)
    }

    @Test fun averageAccuracyIgnoresAbandonedAndWaitRuns() {
        val s = Analytics.compute(listOf(rec(accuracy = 0.8), rec(accuracy = 0.6), rec(outcome = "abandoned", accuracy = 0.0), rec(mode = "WAIT", accuracy = 1.0)), zone)
        assertEquals(0.7, s.avgAccuracy!!, 1e-9)
    }

    @Test fun noRecordsNoStats() {
        val s = Analytics.compute(emptyList(), zone)
        assertEquals(0, s.runs); assertNull(s.avgAccuracy); assertTrue(s.hardest.isEmpty()); assertNull(s.improvement)
    }

    @Test fun hardestExercisesNeedAtLeastTwoAttemptsAndRankByFailureAndAccuracy() {
        val runs = listOf(
            rec("facile", outcome = "passed", accuracy = 0.98), rec("facile", outcome = "passed", accuracy = 0.95),
            rec("duro", outcome = "failed", accuracy = 0.5), rec("duro", outcome = "abandoned", accuracy = 0.4), rec("duro", outcome = "passed", accuracy = 0.85),
            rec("unico", outcome = "failed", accuracy = 0.1),
        )
        val h = Analytics.compute(runs, zone).hardest
        assertEquals(listOf("duro"), h.map { it.id }) // «unico» ha un solo tentativo, «facile» va benissimo: niente da rivedere
        assertEquals(3, h[0].attempts); assertEquals(2.0 / 3.0, h[0].failRate, 1e-9)
    }

    @Test fun timingProfileSaysIfYouTendToBeEarlyOrLate() {
        val s = Analytics.compute(listOf(rec(meanDev = -40, early = 6, late = 1, offBeat = 7, noteHits = 10), rec(meanDev = -20, early = 4, late = 1, offBeat = 5, noteHits = 10)), zone)
        val t = s.timing
        assertEquals(-30.0, t.meanDevMs, 1e-9) // media pesata sulle note: qui uguali
        assertEquals(10, t.early); assertEquals(2, t.late)
        assertEquals(0.6, t.offBeatPct, 1e-9)
        assertEquals("in anticipo", t.tendency)
    }

    @Test fun timingTendencyIsBalancedWhenCloseToZero() {
        assertEquals("a tempo", Analytics.compute(listOf(rec(meanDev = 5, early = 2, late = 2, offBeat = 4, noteHits = 10)), zone).timing.tendency)
        assertEquals("in ritardo", Analytics.compute(listOf(rec(meanDev = 60, early = 0, late = 5, offBeat = 5, noteHits = 10)), zone).timing.tendency)
    }

    @Test fun restAndHoldErrorsAreTotalled() {
        val a = rec().copy(restEarly = 2, restLate = 1, restInRest = 3, holdShort = 4, holdLong = 2)
        val b = rec().copy(restEarly = 1, holdShort = 1)
        val t = Analytics.compute(listOf(a, b), zone).timing
        assertEquals(3, t.restEarly); assertEquals(1, t.restLate); assertEquals(3, t.restInRest); assertEquals(5, t.holdShort); assertEquals(2, t.holdLong)
    }

    @Test fun handsAreComparedOnTimedRuns() {
        val s = Analytics.compute(listOf(rec(hand = "RIGHT", accuracy = 0.9), rec(hand = "RIGHT", accuracy = 0.8), rec(hand = "LEFT", accuracy = 0.5), rec(hand = "BOTH", accuracy = 0.7)), zone)
        assertEquals(0.85, s.byHand["RIGHT"]!!.accuracy, 1e-9); assertEquals(2, s.byHand["RIGHT"]!!.runs)
        assertEquals(0.5, s.byHand["LEFT"]!!.accuracy, 1e-9)
        assertEquals("LEFT", s.weakestHand)
    }

    @Test fun dailyTrendAveragesPerDayInOrder() {
        val s = Analytics.compute(listOf(rec(day = 3, accuracy = 0.5), rec(day = 1, accuracy = 0.8), rec(day = 1, accuracy = 0.6), rec(day = 3, accuracy = 0.7, wrong = 5)), zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), s.daily.map { it.date })
        assertEquals(0.7, s.daily[0].accuracy, 1e-9); assertEquals(2, s.daily[0].runs)
        assertEquals(8, s.daily[1].errors) // errori = sbagliati + mancati: (1+1) + (5+1)
        assertEquals(LocalDate.of(2026, 11, 1), s.bestDay); assertEquals(LocalDate.of(2026, 11, 3), s.worstDay)
    }

    @Test fun improvementComparesRecentRunsWithTheOnesBefore() {
        val before = (1..5).map { rec(day = it, accuracy = 0.5) }
        val recent = (6..10).map { rec(day = it, accuracy = 0.8) }
        val imp = Analytics.compute(before + recent, zone).improvement!!
        assertEquals(0.8, imp.recent, 1e-9); assertEquals(0.5, imp.previous, 1e-9); assertEquals(0.3, imp.delta, 1e-9)
    }

    @Test fun improvementNeedsEnoughRuns() {
        assertNull(Analytics.compute(listOf(rec(accuracy = 0.5), rec(accuracy = 0.9)), zone).improvement)
    }

    @Test fun weakKeysAddWrongAndMissedAcrossRuns() {
        val a = rec().copy(wrongKeys = mapOf(45 to 3, 47 to 1), missedKeys = mapOf(48 to 2))
        val b = rec().copy(wrongKeys = mapOf(45 to 1), missedKeys = mapOf(48 to 1, 50 to 1))
        val k = Analytics.compute(listOf(a, b), zone).weakKeys
        assertEquals(45, k[0].key); assertEquals(4, k[0].wrong)
        assertEquals(48, k[1].key); assertEquals(3, k[1].missed)
        assertTrue(k.size <= 8)
    }

    @Test fun partOfDayShowsWhenYouPlayBest() {
        val s = Analytics.compute(listOf(rec(hour = 8, accuracy = 0.9), rec(hour = 9, accuracy = 0.8), rec(hour = 21, accuracy = 0.5), rec(hour = 22, accuracy = 0.6)), zone)
        assertEquals("mattina", s.bestPartOfDay)
        assertEquals(0.85, s.byPartOfDay["mattina"]!!, 1e-9)
    }

    @Test fun speedBucketsShowHowYouDoAtEachSpeed() {
        val s = Analytics.compute(listOf(rec(speed = 0.5, accuracy = 0.9), rec(speed = 0.75, accuracy = 0.8), rec(speed = 1.0, accuracy = 0.6), rec(speed = 1.0, accuracy = 0.7)), zone)
        assertEquals(0.65, s.bySpeed[1.0]!!, 1e-9); assertEquals(0.9, s.bySpeed[0.5]!!, 1e-9)
    }

    @Test fun abandonRateAndBars() {
        val s = Analytics.compute(listOf(rec(outcome = "abandoned"), rec(outcome = "abandoned"), rec(outcome = "passed"), rec(outcome = "failed")), zone)
        assertEquals(0.5, s.abandonRate, 1e-9)
    }

    @Test fun weakBarsPerExerciseAccumulate() {
        val a = rec("x").copy(barErrors = mapOf(2 to 3, 3 to 1)); val b = rec("x").copy(barErrors = mapOf(2 to 2))
        val w = Analytics.compute(listOf(a, b, rec("y")), zone).weakBarsByExercise["x"]!!
        assertEquals(listOf(2, 3), w.map { it.first }); assertEquals(5, w[0].second)
        assertNotNull(Analytics.compute(listOf(a), zone).weakBarsByExercise["x"])
    }

    @Test fun hardestListRanksSeveralAndSkipsPerfectOnes() {
        val runs = listOf(
            rec("medio", outcome = "failed", accuracy = 0.7), rec("medio", outcome = "passed", accuracy = 0.9),
            rec("duro", outcome = "failed", accuracy = 0.4), rec("duro", outcome = "failed", accuracy = 0.5),
            rec("perfetto", outcome = "passed", accuracy = 1.0), rec("perfetto", outcome = "passed", accuracy = 1.0),
        )
        assertEquals(listOf("duro", "medio"), Analytics.compute(runs, zone).hardest.map { it.id })
    }
}
