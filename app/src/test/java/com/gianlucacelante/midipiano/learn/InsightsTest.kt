package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** I consigli a parole ricavati dalle statistiche. */
class InsightsTest {
    private val zone = ZoneOffset.UTC
    private var clock = 1_700_000_000_000L
    private fun rec(
        accuracy: Double = 0.9, hand: String = "BOTH", outcome: String = "passed", meanDev: Int = 0, noteHits: Int = 10, offBeat: Int = 0,
        holdShort: Int = 0, holdLong: Int = 0, restEarly: Int = 0, restLate: Int = 0, restInRest: Int = 0, hour: Int = 10,
        wrongKeys: Map<Int, Int> = emptyMap(), missedKeys: Map<Int, Int> = emptyMap(),
    ): RunRecord { clock += 3_600_000L; return RunRecord(clock - clock % 86_400_000L + hour * 3_600_000L + (clock % 3_600_000L), id = "e", mode = "TEMPO", hand = hand, outcome = outcome, accuracy = accuracy,
        meanDevMs = meanDev, noteHits = noteHits, offBeat = offBeat, holdShort = holdShort, holdLong = holdLong, restEarly = restEarly, restLate = restLate, restInRest = restInRest,
        wrongKeys = wrongKeys, missedKeys = missedKeys) }
    private fun tips(records: List<RunRecord>) = Insights.build(Analytics.compute(records, zone))

    @Test fun noDataSaysToKeepPlaying() {
        assertEquals(listOf("Suona qualche esercizio a tempo: le statistiche si riempiono man mano."), tips(emptyList()))
    }

    @Test fun earlyPlayerIsToldToWait() {
        val t = tips(List(4) { rec(meanDev = -45) })
        assertTrue(t.any { it.contains("anticipare") && it.contains("45") })
    }

    @Test fun latePlayerIsToldToAnticipate() {
        assertTrue(tips(List(4) { rec(meanDev = 60) }).any { it.contains("ritardo") })
    }

    @Test fun balancedTimingGetsAPraise() {
        assertTrue(tips(List(4) { rec(meanDev = 3) }).any { it.contains("tempo") && it.contains("preciso") })
    }

    @Test fun weakerHandIsNamedWhenTheGapIsBigEnough() {
        val t = tips(listOf(rec(hand = "RIGHT", accuracy = 0.95), rec(hand = "RIGHT", accuracy = 0.9), rec(hand = "LEFT", accuracy = 0.6), rec(hand = "LEFT", accuracy = 0.7)))
        assertTrue(t.any { it.contains("sinistra") && it.contains("destra") })
        val equal = tips(listOf(rec(hand = "RIGHT", accuracy = 0.9), rec(hand = "LEFT", accuracy = 0.88)))
        assertTrue(equal.none { it.contains("più debole") })
    }

    @Test fun shortNotesAreFlaggedWhenMuchMoreCommonThanLongOnes() {
        assertTrue(tips(List(3) { rec(holdShort = 4, holdLong = 0) }).any { it.contains("troppo presto") && it.contains("note") })
        assertTrue(tips(List(3) { rec(holdShort = 0, holdLong = 4) }).any { it.contains("troppo a lungo") })
    }

    @Test fun restTroubleIsFlaggedFromFiveCommentsOn() {
        assertTrue(tips(List(3) { rec(restEarly = 2) }).any { it.contains("pause") })
        assertTrue(tips(listOf(rec(restEarly = 1))).none { it.contains("pause") })
    }

    @Test fun frequentAbandonmentSuggestsSlowingDown() {
        val runs = List(6) { rec(outcome = "abandoned") } + List(6) { rec() }
        assertTrue(tips(runs).any { it.contains("a metà") })
    }

    @Test fun improvementAndDeclineAreReported() {
        val up = List(5) { rec(accuracy = 0.5) } + List(5) { rec(accuracy = 0.8) }
        assertTrue(tips(up).any { it.contains("migliorando") && it.contains("30") })
        val down = List(5) { rec(accuracy = 0.9) } + List(5) { rec(accuracy = 0.6) }
        assertTrue(tips(down).any { it.contains("peggio") })
    }

    @Test fun weakKeysAreNamedInItalian() {
        val t = tips(listOf(rec(wrongKeys = mapOf(45 to 5, 47 to 1), missedKeys = mapOf(48 to 2))))
        assertTrue(t.any { it.contains("la") && it.contains("do") && it.contains("note che sbagli") })
    }

    @Test fun bestPartOfDayIsMentionedOnlyWhenDifferenceIsClear() {
        val clear = tips(listOf(rec(hour = 8, accuracy = 0.95), rec(hour = 9, accuracy = 0.9), rec(hour = 21, accuracy = 0.6), rec(hour = 22, accuracy = 0.65)))
        assertTrue(clear.any { it.contains("mattina") })
        val flat = tips(listOf(rec(hour = 8, accuracy = 0.9), rec(hour = 21, accuracy = 0.89)))
        assertTrue(flat.none { it.contains("Rendi di più") })
    }

    @Test fun noteNamesForKeys() {
        assertEquals("do", Insights.keyName(48)); assertEquals("sol♯", Insights.keyName(56))
    }
}
