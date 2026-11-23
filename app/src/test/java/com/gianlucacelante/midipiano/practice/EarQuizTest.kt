package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EarQuizTest {
    @Test fun intervalsGrowWithTheLevel() {
        assertEquals(3, EarQuiz.intervalsFor(1).size)
        assertEquals(12, EarQuiz.intervalsFor(4).size)
        assertTrue(EarQuiz.intervalsFor(1).all { it in EarQuiz.intervalsFor(2) })
    }

    @Test fun questionsStayOnTheKeyboardAndOptionsContainTheAnswer() {
        val rnd = Random(7)
        var prev: EarQuiz.IntervalQuestion? = null
        repeat(500) {
            val lv = 1 + it % 4
            val q = EarQuiz.nextInterval(lv, rnd, prev)
            assertTrue(q.base in 48..83 && q.top in 48..83)
            assertTrue(q.semitones in EarQuiz.intervalsFor(lv))
            if (prev != null) assertTrue(q.semitones != prev!!.semitones)
            val o = EarQuiz.options(q, lv, rnd)
            assertEquals(4, o.toSet().size)
            assertTrue(q.name in o)
            prev = q
        }
    }

    @Test fun melodiesHaveTheRightLengthAndLeaps() {
        val rnd = Random(3)
        for (lv in 1..4) repeat(100) {
            val m = EarQuiz.nextMelody(lv, rnd)
            assertEquals(2 + lv, m.size)
            val leap = when (lv) { 1 -> 4; 2 -> 5; 3 -> 9; else -> 12 }
            m.zipWithNext().forEach { (a, b) -> assertTrue(a != b && kotlin.math.abs(a - b) <= leap) }
        }
    }

    @Test fun correctPrefixCountsInOrder() {
        assertEquals(3, EarQuiz.correctPrefix(listOf(60, 62, 64), listOf(60, 62, 64)))
        assertEquals(1, EarQuiz.correctPrefix(listOf(60, 62, 64), listOf(60, 64, 62)))
        assertEquals(0, EarQuiz.correctPrefix(listOf(60), emptyList()))
    }

    @Test fun levelMovesLikeTheChordQuiz() {
        assertEquals(2, EarQuiz.adjustLevel(1, 8, 10))
        assertEquals(1, EarQuiz.adjustLevel(1, 2, 6))
        assertEquals(4, EarQuiz.adjustLevel(4, 10, 10))
        assertEquals(2, EarQuiz.adjustLevel(2, 5, 8))
    }
}
