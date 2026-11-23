package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Il riepilogo di un singolo giorno per la vista a calendario. */
class DaySummaryTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: Int, hour: Int = 10, min: Int = 0) = LocalDate.of(2026, 11, d).atTime(hour, min).toInstant(zone).toEpochMilli()
    private fun rec(day: Int, id: String = "e1", title: String = "Esercizio 1", outcome: String = "passed", accuracy: Double = 0.9, mode: String = "TEMPO",
                    source: String = "exercise", wrong: Int = 1, misses: Int = 1, durationSec: Double = 60.0, meanDev: Int = 0, noteHits: Int = 10, hour: Int = 10) =
        RunRecord(whenMillis = at(day, hour), source = source, id = id, title = title, mode = mode, outcome = outcome, accuracy = accuracy, wrong = wrong, misses = misses,
            durationSec = durationSec, meanDevMs = meanDev, noteHits = noteHits)

    @Test fun summaryCountsOnlyThatDay() {
        val s = DaySummary.of(listOf(rec(1), rec(2, accuracy = 0.5), rec(2, outcome = "failed", accuracy = 0.7), rec(3)), LocalDate.of(2026, 11, 2), zone)
        assertEquals(2, s.runs); assertEquals(1, s.finished); assertEquals(1, s.failed); assertEquals(0, s.abandoned)
        assertEquals(0.6, s.accuracy!!, 1e-9)
        assertEquals(4, s.errors) // (1+1)+(1+1)
        assertEquals(120.0, s.playSec, 1e-9)
    }

    @Test fun emptyDayHasNoAccuracy() {
        val s = DaySummary.of(listOf(rec(1)), LocalDate.of(2026, 11, 5), zone)
        assertEquals(0, s.runs); assertNull(s.accuracy); assertTrue(s.items.isEmpty())
    }

    @Test fun itemsGroupByExerciseOrSongWithAttempts() {
        val s = DaySummary.of(listOf(rec(4, "a", "Alfa"), rec(4, "a", "Alfa", outcome = "failed"), rec(4, "b", "Beta"), rec(4, "s1", "Canzone", source = "song", outcome = "finished")), LocalDate.of(2026, 11, 4), zone)
        assertEquals(listOf("Alfa", "Beta", "Canzone"), s.items.map { it.title }.sorted())
        assertEquals(2, s.items.first { it.title == "Alfa" }.attempts)
        assertEquals(1, s.items.first { it.title == "Alfa" }.passed)
        assertEquals("song", s.items.first { it.title == "Canzone" }.source)
    }

    @Test fun waitRunsAreNotCountedInAccuracy() {
        val s = DaySummary.of(listOf(rec(6, mode = "WAIT", accuracy = 1.0), rec(6, accuracy = 0.8)), LocalDate.of(2026, 11, 6), zone)
        assertEquals(0.8, s.accuracy!!, 1e-9)
        assertEquals(2, s.runs)
    }

    @Test fun tendencyOfTheDayIsWeightedByNotes() {
        val s = DaySummary.of(listOf(rec(7, meanDev = -40, noteHits = 10), rec(7, meanDev = -20, noteHits = 30)), LocalDate.of(2026, 11, 7), zone)
        assertEquals(-25.0, s.meanDevMs!!, 1e-9)
    }

    @Test fun monthCellsMarkDaysWithRunsAndTheirAccuracy() {
        val cells = DaySummary.month(listOf(rec(1, accuracy = 0.9), rec(1, accuracy = 0.7), rec(10), rec(40 - 20, accuracy = 0.4)), 2026, 11, zone)
        assertEquals(30, cells.size)
        assertEquals(2, cells[0].runs); assertEquals(0.8, cells[0].accuracy!!, 1e-9)
        assertEquals(0, cells[1].runs); assertNull(cells[1].accuracy)
        assertEquals(1, cells[9].runs)
        assertEquals(LocalDate.of(2026, 11, 20), cells[19].date)
    }

    @Test fun mostActiveAndBestDaysOfAMonth() {
        val cells = DaySummary.month(listOf(rec(3), rec(3), rec(3), rec(8, accuracy = 0.99), rec(9, accuracy = 0.2)), 2026, 11, zone)
        assertEquals(LocalDate.of(2026, 11, 3), DaySummary.busiest(cells))
        assertEquals(LocalDate.of(2026, 11, 8), DaySummary.best(cells))
        assertNull(DaySummary.busiest(DaySummary.month(emptyList(), 2026, 11, zone)))
    }
}
