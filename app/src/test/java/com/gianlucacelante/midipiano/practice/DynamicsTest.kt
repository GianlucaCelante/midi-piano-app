package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicsTest {
    @Test
    fun velocitiesMapToTheUsualMarkings() {
        assertEquals("pp", Dynamics.label(20))
        assertEquals("p", Dynamics.label(45))
        assertEquals("mp", Dynamics.label(60))
        assertEquals("mf", Dynamics.label(75))
        assertEquals("f", Dynamics.label(90))
        assertEquals("ff", Dynamics.label(120))
    }

    @Test
    fun comparisonSaysOkNearOrOff() {
        assertEquals(Dynamics.Match.OK, Dynamics.compare(played = 75, expected = 78))
        assertEquals(Dynamics.Match.NEAR, Dynamics.compare(played = 60, expected = 75)) // mp contro mf
        assertEquals(Dynamics.Match.OFF, Dynamics.compare(played = 30, expected = 110))
    }

    @Test
    fun onlySongsWithRealVariationAreCompared() {
        assertFalse(Dynamics.isExpressive(listOf(80, 80, 80, 80)))
        assertFalse(Dynamics.isExpressive(listOf(80, 85, 82)))
        assertTrue(Dynamics.isExpressive(listOf(50, 80, 100)))
    }
}

class InlineVelocityTest {
    private fun parse(json: String) = com.gianlucacelante.midipiano.learn.InlineSongParser.parse(org.json.JSONObject(json), "x")

    @Test
    fun anExerciseNoteCanCarryItsOwnVelocity() {
        val s = parse("""{"bpm":60,"notes":[{"beat":0,"dur":1,"key":60,"hand":"R","vel":40},{"beat":1,"dur":1,"key":62,"hand":"R","vel":110},{"beat":2,"dur":1,"key":64,"hand":"R"}]}""")
        assertEquals(listOf(40, 110, 80), s.notes.map { it.velocity })
    }

    @Test
    fun velocityIsClampedToTheMidiRange() {
        val s = parse("""{"bpm":60,"notes":[{"beat":0,"dur":1,"key":60,"hand":"R","vel":0},{"beat":1,"dur":1,"key":62,"hand":"R","vel":300}]}""")
        assertEquals(listOf(1, 127), s.notes.map { it.velocity })
    }

    @Test
    fun anExerciseWithWrittenDynamicsIsExpressive() {
        val s = parse("""{"bpm":60,"notes":[{"beat":0,"dur":1,"key":60,"hand":"R","vel":40},{"beat":1,"dur":1,"key":62,"hand":"R","vel":100}]}""")
        assertTrue(Dynamics.isExpressive(s.notes.map { it.velocity }))
    }
}
