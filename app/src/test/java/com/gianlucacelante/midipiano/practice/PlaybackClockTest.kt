package com.gianlucacelante.midipiano.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {
    private fun ns(sec: Double) = (sec * 1e9).toLong()

    @Test
    fun timeAdvancesAtRealSpeedWhileRunning() {
        val c = PlaybackClock(durationSec = 60.0)
        c.start(ns(10.0))
        assertEquals(2.0, c.tick(ns(12.0)).songTimeSec, 1e-9)
    }

    @Test
    fun pausedClockDoesNotMove() {
        val c = PlaybackClock(60.0)
        c.start(0L)
        c.pause(ns(3.0))
        assertEquals(3.0, c.tick(ns(100.0)).songTimeSec, 1e-9)
        assertFalse(c.running)
    }

    @Test
    fun speedScalesSongTimeAndCanChangeWhileRunning() {
        val c = PlaybackClock(60.0)
        c.setSpeed(0.5, 0L)
        c.start(0L)
        assertEquals(1.0, c.tick(ns(2.0)).songTimeSec, 1e-9) // 2 s reali a metà velocità
        c.setSpeed(1.0, ns(2.0))
        assertEquals(2.0, c.tick(ns(3.0)).songTimeSec, 1e-9)
    }

    @Test
    fun seekMovesThePosition() {
        val c = PlaybackClock(60.0)
        c.seek(30.0, 0L)
        assertEquals(30.0, c.tick(ns(5.0)).songTimeSec, 1e-9)
        c.start(ns(5.0))
        assertEquals(31.0, c.tick(ns(6.0)).songTimeSec, 1e-9)
    }

    @Test
    fun loopWrapsAroundAndReportsIt() {
        val c = PlaybackClock(60.0)
        c.setLoop(10.0, 14.0)
        c.seek(10.0, 0L)
        c.start(0L)
        val r = c.tick(ns(5.0)) // 5 s: passa il punto 14 -> torna a 10 + 1
        assertTrue(r.looped)
        assertEquals(11.0, r.songTimeSec, 1e-9)
        assertEquals(11.5, c.tick(ns(5.5)).songTimeSec, 1e-9)
    }

    @Test
    fun songEndsAtItsDurationAndStops() {
        val c = PlaybackClock(10.0)
        c.start(0L)
        val r = c.tick(ns(12.0))
        assertTrue(r.finished)
        assertEquals(10.0, r.songTimeSec, 1e-9)
        assertFalse(c.running)
    }

    @Test
    fun countInStartsFromANegativeTime() {
        val c = PlaybackClock(60.0)
        c.seek(-2.0, 0L)
        c.start(0L)
        assertEquals(-1.0, c.tick(ns(1.0)).songTimeSec, 1e-9)
        assertEquals(0.5, c.tick(ns(2.5)).songTimeSec, 1e-9)
    }

    @Test
    fun clearingTheLoopLetsTheSongContinue() {
        val c = PlaybackClock(60.0)
        c.setLoop(0.0, 2.0)
        c.setLoop(null, null)
        c.start(0L)
        val r = c.tick(ns(5.0))
        assertFalse(r.looped)
        assertEquals(5.0, r.songTimeSec, 1e-9)
    }
}
