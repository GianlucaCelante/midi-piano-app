package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ogni giro lascia un record completo; si scrive e si rilegge senza perdere niente (e senza rompersi con righe rovinate). */
class RunRecordTest {
    private fun rec(id: String = "a/l1/s1", outcome: String = "passed") = RunRecord(
        whenMillis = 1_700_000_000_000L, source = "exercise", id = id, title = "Titolo, con \"virgolette\"", step = 2, mode = "TEMPO", hand = "BOTH", speed = 0.75,
        outcome = outcome, durationSec = 31.5, hits = 7, misses = 1, wrong = 3, accuracy = 0.875, stars = 2,
        noteHits = 12, early = 2, late = 3, offBeat = 5, meanDevMs = -34, meanAbsDevMs = 90,
        restEarly = 1, restLate = 0, restInRest = 2, holdShort = 4, holdLong = 1,
        barErrors = mapOf(2 to 3, 4 to 1), wrongKeys = mapOf(45 to 2, 47 to 1), missedKeys = mapOf(48 to 1), routine = true,
    )

    @Test fun jsonRoundTripKeepsEveryField() {
        val r = rec()
        assertEquals(r, RunRecord.fromJson(r.toJson()))
    }

    @Test fun oneRecordIsOneLine() {
        assertTrue(!rec().toJson().contains('\n'))
    }

    @Test fun damagedLinesAreSkippedNotFatal() {
        val lines = listOf(rec("a").toJson(), "{rovinata", "", rec("b").toJson())
        assertEquals(listOf("a", "b"), RunRecord.parseLines(lines).map { it.id })
    }

    @Test fun missingOptionalFieldsFallBackToDefaults() {
        val old = RunRecord.fromJson("""{"t":1,"s":"song","id":"x","o":"finished"}""")!!
        assertEquals("x", old.id); assertEquals(0, old.hits); assertEquals(emptyMap<Int, Int>(), old.barErrors); assertEquals(false, old.routine)
    }

    @Test fun keepsOnlyTheMostRecentRecordsWhenTooMany() {
        val many = (1..50).map { rec("e$it").copy(whenMillis = it.toLong()) }
        assertEquals((41..50).map { "e$it" }, RunRecord.capped(many, 10).map { it.id })
        assertEquals(50, RunRecord.capped(many, 100).size)
    }
}
