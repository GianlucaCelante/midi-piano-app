package com.gianlucacelante.midipiano.notation

/** Note simultanee dello stesso rigo: si disegnano insieme, con un solo gambo. */
data class Chord(val clef: Clef, val startSec: Double, val notes: List<StaffNote>) {
    private val sorted = notes.sortedBy { it.position }

    val lowest: StaffNote get() = sorted.first()
    val highest: StaffNote get() = sorted.last()

    /** Valore della nota più breve dell'accordo (decide gambo e code). */
    val beats: Double = notes.minOf { it.beats }
    /** Valore della nota più lunga: fin dove l'accordo suona (per decidere dove iniziano le pause). */
    val longestBeats: Double = notes.maxOf { it.beats }
    val hasStem: Boolean get() = beats < 3.5

    /** Numero di code/travi (1 = croma, 2 = semicroma, 3 = biscroma). */
    val flags: Int = notes.maxOf { it.flags }

    /** Direzione del gambo: decide la nota più lontana dalla linea centrale (posizione 4). */
    val stemUp: Boolean get() = (4 - lowest.position) >= (highest.position - 4)
}

object Chords {
    private const val SAME_TIME_SEC = 0.03

    /** Raggruppa le note in accordi, ordinati per istante (a parità, violino prima del basso). */
    fun group(notes: List<StaffNote>): List<Chord> {
        val out = ArrayList<Chord>()
        for ((clef, list) in notes.groupBy { it.clef }) {
            // gruppi contigui per istante (non a "secchi" fissi: due note a 0,044 e 0,046 s stanno insieme)
            var cur = ArrayList<StaffNote>()
            for (n in list.sortedBy { it.startSec }) {
                if (cur.isNotEmpty() && n.startSec - cur.first().startSec > SAME_TIME_SEC) { out += Chord(clef, cur.first().startSec, cur); cur = ArrayList() }
                cur.add(n)
            }
            if (cur.isNotEmpty()) out += Chord(clef, cur.first().startSec, cur)
        }
        return out.sortedWith(compareBy({ it.startSec }, { it.clef.ordinal }))
    }
}
