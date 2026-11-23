package com.gianlucacelante.midipiano.notation

/** Nomi delle note in solfeggio italiano, come si usano per guidare chi studia. */
object NoteNames {
    private val NAMES = arrayOf("do", "do♯", "re", "re♯", "mi", "fa", "fa♯", "sol", "sol♯", "la", "la♯", "si")

    private val FLAT_NAMES = arrayOf("do", "reb", "re", "mib", "mi", "fa", "solb", "sol", "lab", "la", "sib", "si")

    /** Con [flats] i tasti neri prendono il nome col bemolle (tonalità a bemolli), altrimenti col diesis. */
    private val STEP_NAMES = arrayOf("do", "re", "mi", "fa", "sol", "la", "si")
    private val STEP_PITCH_CLASS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /**
     * Il nome della nota come è scritta nello spartito: [diatonic] è l'indice scritto (ottava*7 + passo, Do=0 ... Si=6).
     * Il Si♭ scritto sul Si si chiama «si♭» e non «la♯», come vuole la tonalità del brano.
     */
    fun spelled(midiKey: Int, diatonic: Int): String {
        val step = diatonic.mod(7)
        val alter = (midiKey.mod(12) - STEP_PITCH_CLASS[step] + 18).mod(12) - 6 // -1, 0, +1 (o di più per grafie rare)
        val mark = when {
            alter == 0 -> ""
            alter == 1 -> "♯"
            alter == -1 -> "♭"
            alter > 1 -> "♯".repeat(alter.coerceAtMost(2))
            else -> "♭".repeat((-alter).coerceAtMost(2))
        }
        return STEP_NAMES[step] + mark
    }

    fun italian(midiKey: Int, flats: Boolean = false): String = (if (flats) FLAT_NAMES else NAMES)[midiKey.mod(12)]
}
