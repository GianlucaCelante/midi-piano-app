package com.gianlucacelante.midipiano.song

/** Pulizia delle note lette da un file: lo stesso tasto non può suonare due volte insieme a se stesso. */
object SongCleanup {
    private const val SAME_ONSET_SEC = 0.001
    private const val MIN_DURATION_SEC = 0.03

    /**
     * - due note identiche (stesso tasto, stesso attacco, es. due voci all'unisono) diventano una sola, la più lunga;
     * - se un tasto viene ribattuto mentre suona ancora, la nota precedente finisce all'attacco della successiva.
     */
    fun tidy(notes: List<SongNote>): List<SongNote> {
        val out = ArrayList<SongNote>(notes.size)
        for ((_, group) in notes.groupBy { it.key }) {
            val sorted = group.sortedWith(compareBy({ it.startSec }, { -it.durationSec }))
            val kept = ArrayList<SongNote>()
            for (n in sorted) {
                val last = kept.lastOrNull()
                if (last != null && n.startSec - last.startSec < SAME_ONSET_SEC) {
                    if (n.durationSec > last.durationSec) kept[kept.lastIndex] = n // stesso attacco: si tiene la più lunga
                    continue
                }
                if (last != null && last.startSec + last.durationSec > n.startSec) {
                    kept[kept.lastIndex] = last.copy(durationSec = maxOf(n.startSec - last.startSec, MIN_DURATION_SEC))
                }
                kept.add(n)
            }
            out.addAll(kept)
        }
        return out.sortedWith(compareBy({ it.startSec }, { it.key }))
    }
}
