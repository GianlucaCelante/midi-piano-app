package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand

/** Testi del pulsante e della finestra delle modalità del player. */
object ModeLabels {
    private fun handName(h: Hand) = when (h) { Hand.RIGHT -> "destra"; Hand.LEFT -> "sinistra"; Hand.BOTH -> "" }

    /** Etichetta breve sul pulsante, per esempio «A tempo · destra». */
    fun pill(mode: StudyMode, hand: Hand): String = when (mode) {
        StudyMode.LISTEN -> "Ascolta"
        StudyMode.WAIT -> if (hand == Hand.BOTH) "Impara" else "Impara · ${handName(hand)}"
        StudyMode.TEMPO -> if (hand == Hand.BOTH) "A tempo" else "A tempo · ${handName(hand)}"
    }

    /** Cosa succede con la combinazione scelta. */
    fun describe(mode: StudyMode, hand: Hand, backing: Boolean): String = when (mode) {
        StudyMode.LISTEN -> "Suona il brano da solo: guarda lo spartito e ascolta."
        StudyMode.WAIT -> if (hand == Hand.BOTH) "Il brano aspetta: va avanti solo quando suoni le note giuste."
            else "Il brano aspetta le note della mano ${handName(hand)}; l'altra mano non serve."
        StudyMode.TEMPO -> when {
            hand == Hand.BOTH -> "Il brano scorre da solo e conta quanto sei preciso, con tutte e due le mani."
            backing -> "Suoni la mano ${handName(hand)} a tempo; l'app suona la ${handName(if (hand == Hand.RIGHT) Hand.LEFT else Hand.RIGHT)} per accompagnarti."
            else -> "Suoni la mano ${handName(hand)} a tempo; l'altra mano resta in silenzio."
        }
    }

    fun backingApplies(mode: StudyMode, hand: Hand): Boolean = mode == StudyMode.TEMPO && hand != Hand.BOTH
}
