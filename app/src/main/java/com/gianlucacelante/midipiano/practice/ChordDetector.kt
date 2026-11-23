package com.gianlucacelante.midipiano.practice

import kotlin.random.Random

/** Un accordo riconosciuto: fondamentale (0 = do ... 11 = si), tipo e nota al basso (per i rivolti). */
data class Chord(val root: Int, val quality: ChordQuality, val bass: Int) {
    val inversion: Int get() {
        val i = quality.intervals.indexOf((bass - root + 12) % 12)
        return if (i < 0) 0 else i
    }
    /** «Do maggiore», «La minore», «Sol 7». */
    val name: String get() = ChordDetector.rootName(root) + quality.suffix
    /** Come [name], con il rivolto se c'è («Do maggiore, 1° rivolto»). */
    val fullName: String get() = name + when (inversion) { 0 -> ""; 1 -> ", 1° rivolto"; 2 -> ", 2° rivolto"; else -> ", 3° rivolto" }
}

enum class ChordQuality(val suffix: String, val intervals: List<Int>) {
    MAJOR(" maggiore", listOf(0, 4, 7)),
    MINOR(" minore", listOf(0, 3, 7)),
    DIM(" diminuito", listOf(0, 3, 6)),
    AUG(" aumentato", listOf(0, 4, 8)),
    SUS2(" sus2", listOf(0, 2, 7)),
    SUS4(" sus4", listOf(0, 5, 7)),
    DOM7(" 7", listOf(0, 4, 7, 10)),
    MAJ7(" maggiore 7", listOf(0, 4, 7, 11)),
    MIN7(" minore 7", listOf(0, 3, 7, 10)),
    HALFDIM(" semidiminuito", listOf(0, 3, 6, 10)),
}

object ChordDetector {
    private val NAMES = arrayOf("Do", "Re♭", "Re", "Mi♭", "Mi", "Fa", "Fa♯", "Sol", "La♭", "La", "Si♭", "Si")
    fun rootName(pc: Int): String = NAMES[pc.mod(12)]

    /** I tasti premuti (qualunque ottava, qualunque rivolto) → l'accordo, o null se non ne formano uno noto. */
    fun identify(keys: Collection<Int>): Chord? {
        if (keys.size < 3) return null
        val bass = keys.min().mod(12)
        val pcs = keys.map { it.mod(12) }.toSet()
        if (pcs.size < 3) return null
        val found = ArrayList<Chord>()
        for (root in pcs) {
            val rel = pcs.map { (it - root + 12) % 12 }.toSet()
            for (q in ChordQuality.entries) if (rel == q.intervals.toSet()) found += Chord(root, q, bass)
        }
        // più letture possibili (Do6 = La minore 7, Do aumentato...): si preferisce la fondamentale al basso, poi i tipi più comuni
        return found.sortedWith(compareBy({ it.root != bass }, { it.quality.ordinal })).firstOrNull()
    }

    /** Le note (tasti) che compongono l'accordo con la fondamentale in [octaveBase] (60 = do centrale). */
    fun keys(root: Int, q: ChordQuality, octaveBase: Int = 60): List<Int> = q.intervals.map { octaveBase + root + it }
}

/** Domande per l'allenamento sugli accordi: da fare (suona…) o da riconoscere (ascolta…). */
object ChordQuiz {
    const val MAX_LEVEL = 3

    fun qualities(level: Int): List<ChordQuality> = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR)
        2 -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIM, ChordQuality.AUG)
        else -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOM7, ChordQuality.MAJ7, ChordQuality.MIN7, ChordQuality.SUS4)
    }

    /** Livello 1: solo le fondamentali sui tasti bianchi; dal 2 tutte e dodici. */
    fun roots(level: Int): List<Int> = if (level <= 1) listOf(0, 2, 4, 5, 7, 9, 11) else (0..11).toList()

    data class Question(val root: Int, val quality: ChordQuality) {
        val name: String get() = ChordDetector.rootName(root) + quality.suffix
        val size: Int get() = quality.intervals.size
        fun matches(c: Chord?) = c != null && c.root == root && c.quality == quality
    }

    fun next(level: Int, rnd: Random, previous: Question?): Question {
        val rs = roots(level)
        val qs = qualities(level)
        var q: Question
        do { q = Question(rs[rnd.nextInt(rs.size)], qs[rnd.nextInt(qs.size)]) } while (q == previous && rs.size * qs.size > 1)
        return q
    }

    /** Quattro nomi fra cui scegliere (compreso quello giusto), per l'ascolto. */
    fun options(answer: Question, level: Int, rnd: Random): List<Question> {
        val all = roots(level).flatMap { r -> qualities(level).map { Question(r, it) } }.filter { it != answer }
        return (all.shuffled(rnd).take(3) + answer).shuffled(rnd)
    }

    /** Sale con 8 risposte giuste su 10; scende con 4 o meno (con almeno 6 risposte date). */
    fun adjustLevel(level: Int, correct: Int, asked: Int): Int = when {
        asked >= 10 && correct >= 8 -> (level + 1).coerceAtMost(MAX_LEVEL)
        asked >= 6 && correct <= asked * 0.4 -> (level - 1).coerceAtLeast(1)
        else -> level
    }
}
