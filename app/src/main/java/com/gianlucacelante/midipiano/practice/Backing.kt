package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand

/** Accompagnamento: mentre si studia una mano in "A tempo", l'app suona l'altra. */
object Backing {
    fun handToPlay(userHand: Hand): Hand? = when (userHand) {
        Hand.RIGHT -> Hand.LEFT
        Hand.LEFT -> Hand.RIGHT
        Hand.BOTH -> null
    }

    fun active(enabled: Boolean, mode: StudyMode, hand: Hand, sound: Boolean, previewing: Boolean, countingIn: Boolean): Boolean =
        enabled && sound && mode == StudyMode.TEMPO && hand != Hand.BOTH && !previewing && !countingIn
}
