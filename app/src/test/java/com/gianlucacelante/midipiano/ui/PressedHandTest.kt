package com.gianlucacelante.midipiano.ui

import com.gianlucacelante.midipiano.song.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Un tasto premuto prende il colore della mano che lo ha premuto (più scuro del tasto «atteso»). */
class PressedHandTest {
    @Test fun theExpectedNoteSaysWhichHandPressedIt() {
        assertEquals(Hand.LEFT, pressedHand(note = 72, expected = Hand.LEFT, studied = Hand.BOTH))
        assertEquals(Hand.RIGHT, pressedHand(note = 40, expected = Hand.RIGHT, studied = Hand.BOTH))
    }

    @Test fun whenOneHandIsStudiedEveryPressIsThatHand() {
        assertEquals(Hand.LEFT, pressedHand(note = 80, expected = null, studied = Hand.LEFT))
        assertEquals(Hand.RIGHT, pressedHand(note = 40, expected = null, studied = Hand.RIGHT))
    }

    @Test fun withBothHandsAndNoExpectedNoteThePitchDecides() {
        assertEquals(Hand.LEFT, pressedHand(note = 48, expected = null, studied = Hand.BOTH))
        assertEquals(Hand.RIGHT, pressedHand(note = 60, expected = null, studied = Hand.BOTH)) // il Do centrale è della destra
        assertEquals(Hand.RIGHT, pressedHand(note = 72, expected = null, studied = null))
    }

    @Test fun pressedColorsAreDarkerThanTheExpectedOnes() {
        fun luma(c: androidx.compose.ui.graphics.Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        for (h in listOf(Hand.LEFT, Hand.RIGHT)) {
            assertEquals(true, luma(HandColors.pressedTop(h)) < luma(HandColors.keyTop(h)))
            assertEquals(true, luma(HandColors.pressedBottom(h)) < luma(HandColors.keyBottom(h)))
            assertEquals(true, luma(HandColors.pressedBlackTop(h)) < luma(HandColors.blackTop(h)))
        }
        assertNotEquals(HandColors.pressedTop(Hand.LEFT), HandColors.pressedTop(Hand.RIGHT)) // le due mani restano distinguibili
    }
}
