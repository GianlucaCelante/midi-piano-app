package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import kotlin.math.abs
import kotlin.math.max

/**
 * Difficoltà tecnica di un brano, battuta per battuta. Conta quello che rende davvero difficile una battuta a chi comincia:
 * velocità (attacchi al secondo per mano), salti, accordi larghi, note fuori tonalità, due mani con ritmi diversi.
 * Serve a decidere dove semplificare: una battuta già facile resta com'è nell'originale.
 */
object TechnicalDifficulty {
    /** Fino a questo punteggio una battuta si considera da principiante. */
    const val EASY_MAX = 1.5
    /** Fino a questo punteggio è da livello intermedio. */
    const val MEDIUM_MAX = 3.5

    private const val SAME_STEP_SEC = 0.012
    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /** Inizio e fine di ogni battuta, in secondi. L'ultima finisce con il brano. */
    fun windows(song: Song): List<Pair<Double, Double>> {
        val starts = listOf(0.0) + song.barLines
        val end = maxOf(song.durationSec, (starts.last() + song.barDurationSec))
        return starts.mapIndexed { i, s -> s to (starts.getOrNull(i + 1) ?: end) }
    }

    fun barScores(song: Song): List<Double> = windows(song).map { (s, e) -> scoreNotes(song, notesIn(song.notes, s, e), s, e) }

    /** Punteggio medio sulle battute (le vuote contano 0: restano confrontabili fra versioni dello stesso brano). */
    fun overall(song: Song): Double = barScores(song).let { if (it.isEmpty()) 0.0 else it.sum() / it.size }

    fun notesIn(notes: List<SongNote>, start: Double, end: Double): List<SongNote> =
        notes.filter { it.startSec >= start - 1e-6 && it.startSec < end - 1e-6 }

    /** Punteggio di un gruppo di note che cadono nella battuta [start, end). */
    fun scoreNotes(song: Song, notes: List<SongNote>, start: Double, end: Double): Double {
        if (notes.isEmpty()) return 0.0
        // velocità: con un tempo di riferimento fra 60 e 120 bpm (chi studia rallenta, quindi un brano a 190 non è "tre volte più difficile")
        val spb = song.secPerBeatAt(start).coerceIn(0.5, 1.0)
        val dur = max(song.beatsBetween(start, end) * spb, 0.5)
        var score = 0.0
        val onsetsByHand = HashMap<Hand, Set<Long>>()
        for ((hand, list) in notes.groupBy { it.hand }) {
            val steps = steps(list)
            onsetsByHand[hand] = steps.map { Math.round(it.first().startSec * 1000) }.toSet()
            score += 0.6 * max(0.0, steps.size / dur - 1.0) // velocità: oltre un attacco al secondo
            var prevRef: Int? = null
            for (st in steps) {
                val ref = if (hand == Hand.LEFT) st.minOf { it.key } else st.maxOf { it.key }
                prevRef?.let { score += 0.3 * max(0, abs(ref - it) - 5) / 6.0 } // salti oltre una quinta
                prevRef = ref
                score += 0.35 * (st.size - 1) // accordi
                val span = st.maxOf { it.key } - st.minOf { it.key }
                if (span > 12) score += 0.5 else if (span > 9) score += 0.25 // estensione che costringe ad aprire la mano
            }
        }
        // note fuori dalla scala della tonalità
        val tonic = Math.floorMod(song.keyFifths * 7, 12)
        // la scala maggiore della tonalità e la sensibile del relativo minore (Mi naturale in Fa minore): non sono note "strane"
        val scale = MAJOR.map { (tonic + it) % 12 }.toSet() + ((tonic + 8) % 12)
        score += 0.5 * notes.count { it.key % 12 !in scale }
        score += 0.1 * abs(song.keyFifths)
        // due mani insieme: base fissa, più ritmi diversi
        if (onsetsByHand.size >= 2) {
            val sets = onsetsByHand.values.sortedBy { it.size }
            val fewer = sets.first()
            val other = sets.last()
            val frac = if (fewer.isEmpty()) 0.0 else fewer.count { it !in other }.toDouble() / fewer.size
            // una sola nota per battuta di una mano (p.es. il basso all'inizio) non è indipendenza: pesa solo se la mano ha almeno tre attacchi
            val weight = ((fewer.size - 1) / 2.0).coerceIn(0.0, 1.0)
            score += 0.5 + 0.7 * frac * weight
        }
        return score
    }

    private fun steps(notes: List<SongNote>): List<List<SongNote>> {
        val sorted = notes.sortedBy { it.startSec }
        val out = ArrayList<MutableList<SongNote>>()
        for (n in sorted) {
            val last = out.lastOrNull()
            if (last != null && n.startSec - last.first().startSec <= SAME_STEP_SEC) last.add(n) else out.add(mutableListOf(n))
        }
        return out
    }
}
