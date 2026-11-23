package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsTest {
    @Test
    fun theNeedleMovesFromLeftToRightWithTheSpeed() {
        assertEquals(-70f, SpeedDial.needleAngleDegrees(0.5), 1e-4f)
        assertEquals(0f, SpeedDial.needleAngleDegrees(0.75), 1e-4f)
        assertEquals(70f, SpeedDial.needleAngleDegrees(1.0), 1e-4f)
    }

    @Test
    fun speedsOutsideTheRangeAreClamped() {
        assertEquals(-70f, SpeedDial.needleAngleDegrees(0.1), 1e-4f)
        assertEquals(70f, SpeedDial.needleAngleDegrees(2.0), 1e-4f)
        assertTrue(SpeedDial.needleAngleDegrees(0.6) < SpeedDial.needleAngleDegrees(0.9))
    }
}
