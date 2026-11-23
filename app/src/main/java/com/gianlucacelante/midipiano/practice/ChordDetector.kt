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

    /** Le note (tasti) che compongono l'accordo con la fondamentale in [octaveBase] (60 = do centrale); con [inversion] > 0 le prime note salgono di un'ottava. */
    fun keys(root: Int, q: ChordQuality, octaveBase: Int = 60, inversion: Int = 0): List<Int> {
        val k = q.intervals.map { octaveBase + root + it }.toMutableList()
        repeat(inversion.coerceIn(0, k.size - 1)) { k.add(k.removeAt(0) + 12) }
        return k
    }
}

/** Domande per l'allenamento sugli accordi: da fare (suona…) o da riconoscere (ascolta…). */
object ChordQuiz {
    const val MAX_LEVEL = 4

    fun qualities(level: Int): List<ChordQuality> = when (level.coerceIn(1, MAX_LEVEL)) {
        1, 4 -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR)
        2 -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIM, ChordQuality.AUG)
        else -> listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOM7, ChordQuality.MAJ7, ChordQuality.MIN7, ChordQuality.SUS4)
    }

    /** Livelli 1 e 4: solo le fondamentali sui tasti bianchi; dal 2 al 3 tutte e dodici. */
    fun roots(level: Int): List<Int> = if (level == 1 || level >= 4) listOf(0, 2, 4, 5, 7, 9, 11) else (0..11).toList()

    /** Dal livello 4 si chiede anche il rivolto: [inversion] 0 = stato fondamentale, 1 e 2 = primo e secondo rivolto; -1 = qualunque. */
    data class Question(val root: Int, val quality: ChordQuality, val inversion: Int = -1) {
        val name: String get() = ChordDetector.rootName(root) + quality.suffix + when (inversion) { 0 -> ", stato fondamentale"; 1 -> ", 1° rivolto"; 2 -> ", 2° rivolto"; else -> "" }
        val size: Int get() = quality.intervals.size
        fun matches(c: Chord?) = c != null && c.root == root && c.quality == quality && (inversion < 0 || c.inversion == inversion)
        /** I tasti da far sentire, con il rivolto richiesto. */
        fun keys(): List<Int> = ChordDetector.keys(root, quality, 60, inversion.coerceAtLeast(0))
    }

    private fun variants(level: Int): List<Question> =
        roots(level).flatMap { r -> qualities(level).flatMap { q -> if (level >= 4) (0..2).map { Question(r, q, it) } else listOf(Question(r, q)) } }

    fun next(level: Int, rnd: Random, previous: Question?): Question {
        val all = variants(level)
        var q: Question
        do { q = all[rnd.nextInt(all.size)] } while (q == previous && all.size > 1)
        return q
    }

    /** Quattro nomi fra cui scegliere (compreso quello giusto), per l'ascolto. */
    fun options(answer: Question, level: Int, rnd: Random): List<Question> {
        val all = variants(level).filter { it != answer }
        return (all.shuffled(rnd).take(3) + answer).shuffled(rnd)
    }

    /** Sale con 8 risposte giuste su 10; scende con 4 o meno (con almeno 6 risposte date). */
    fun adjustLevel(level: Int, correct: Int, asked: Int): Int = when {
        asked >= 10 && correct >= 8 -> (level + 1).coerceAtMost(MAX_LEVEL)
        asked >= 6 && correct <= asked * 0.4 -> (level - 1).coerceAtLeast(1)
        else -> level
    }
}
