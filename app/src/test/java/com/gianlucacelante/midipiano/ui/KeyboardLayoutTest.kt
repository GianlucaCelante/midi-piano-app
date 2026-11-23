package com.gianlucacelante.midipiano.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    // 36..47 = un'ottava (Do2..Si2): 7 tasti bianchi in 700 px -> 100 px ciascuno
    private val layout = KeyboardLayout(36..47, widthPx = 700f, heightPx = 200f)

    @Test
    fun whiteKeysShareTheWidthEvenly() {
        assertEquals(100f, layout.whiteKeyWidth, 1e-4f)
        val c = layout.rect(36)
        assertEquals(0f, c.left, 1e-4f)
        assertEquals(100f, c.width, 1e-4f)
        assertFalse(c.isBlack)
        assertEquals(100f, layout.rect(38).left, 1e-4f) // Re
    }

    @Test
    fun blackKeysSitOnTheBoundaryBetweenWhiteKeys() {
        val cSharp = layout.rect(37)
        assertTrue(cSharp.isBlack)
        assertEquals(100f, cSharp.left + cSharp.width / 2, 1e-4f) // a cavallo tra Do e Re
        assertTrue(cSharp.width < 100f)
        assertTrue(cSharp.height < 200f)
    }

    @Test
    fun keyAtPrefersBlackKeysInTheirArea() {
        assertEquals(37, layout.keyAt(100f, 20f)) // in alto sul confine Do/Re: Do diesis
        assertEquals(36, layout.keyAt(50f, 20f)) // in alto ma lontano dal nero: Do
        assertEquals(36, layout.keyAt(100f - 5f, 190f)) // in basso: tasto bianco
        assertEquals(38, layout.keyAt(100f + 5f, 190f))
    }

    @Test
    fun keyAtOutsideReturnsNull() {
        assertNull(layout.keyAt(-1f, 10f))
        assertNull(layout.keyAt(701f, 10f))
        assertNull(layout.keyAt(10f, 201f))
    }

    @Test
    fun notesOutsideTheRangeHaveNoRect() {
        assertNull(layout.rectOrNull(35))
        assertNull(layout.rectOrNull(48))
    }

    @Test
    fun rangeStartingOnABlackKeyStillLaysOut() {
        val l = KeyboardLayout(37..48, 700f, 200f)
        assertTrue(l.rect(37).left >= -l.rect(37).width) // il nero iniziale può sporgere a sinistra
        assertEquals(7, l.whiteKeyCount)
    }
}
