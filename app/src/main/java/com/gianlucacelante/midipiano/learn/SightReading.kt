package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Lettura a prima vista: ogni volta una melodia nuova, scritta al momento, del livello raggiunto.
 * Si suona "A tempo" (con il conto alla rovescia) e senza i nomi delle note sullo spartito.
 */
object SightReading {
    const val PREFIX = "sight-"
    const val MAX_LEVEL = 5

    fun isSight(slideId: String) = slideId.startsWith(PREFIX)

    /** Il livello è nell'id: «sight-3-1700000000». */
    fun levelOf(slideId: String): Int = slideId.removePrefix(PREFIX).substringBefore('-').toIntOrNull()?.coerceIn(1, MAX_LEVEL) ?: 1

    class Spec(val title: String, val hand: Hand, val bpm: Int, val bars: Int, val keyFifths: Int, val scale: List<Int>, val maxLeap: Int, val rhythm: List<Double>, val leftScale: List<Int> = emptyList())

    private val C_MAJOR = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74)
    private val G_MAJOR = listOf(67, 69, 71, 72, 74, 76, 78, 79, 81) // Fa♯ in chiave
    private val C_LOW = listOf(48, 50, 52, 53, 55, 57, 59, 60)

    fun spec(level: Int): Spec = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> Spec("Cinque dita, mano destra", Hand.RIGHT, 60, 4, 0, C_MAJOR.take(5), 2, listOf(1.0))
        2 -> Spec("Mano destra, tutta l'ottava", Hand.RIGHT, 66, 4, 0, C_MAJOR.take(8), 2, listOf(1.0, 1.0, 1.0, 2.0))
        3 -> Spec("Mano sinistra, chiave di basso", Hand.LEFT, 60, 4, 0, C_LOW, 2, listOf(1.0, 1.0, 2.0))
        4 -> Spec("Due mani insieme", Hand.BOTH, 60, 4, 0, C_MAJOR.take(6), 2, listOf(1.0, 1.0, 1.0, 2.0), leftScale = C_LOW.take(5))
        else -> Spec("In Sol maggiore, con il Fa♯", Hand.RIGHT, 72, 4, 1, G_MAJOR.take(8), 3, listOf(1.0, 1.0, 0.5, 2.0, 1.0))
    }

    /** Una melodia casuale: parte dalla tonica, procede per gradi con qualche salto, finisce sulla tonica. */
    fun generate(level: Int, seed: Long): ExerciseSlide {
        val lv = level.coerceIn(1, MAX_LEVEL)
        val sp = spec(lv)
        val rnd = Random(seed)
        val beatsPerBar = 4.0
        val total = sp.bars * beatsPerBar
        val notes = JSONArray()

        fun melody(scale: List<Int>, hand: String, rhythm: List<Double>, leap: Int, startIdx: Int) {
            var idx = startIdx
            var beat = 0.0
            while (beat < total - 1e-9) {
                val last = total - beat <= 2.0 + 1e-9
                var dur = if (last) total - beat else rhythm[rnd.nextInt(rhythm.size)]
                dur = minOf(dur, total - beat)
                // una durata non deve scavalcare la battuta (si legge male)
                val toBarEnd = beatsPerBar - (beat % beatsPerBar)
                if (dur > toBarEnd + 1e-9) dur = toBarEnd
                if (dur == 0.5 && (beat % 1.0) != 0.0) dur = 0.5
                if (last && beat + dur >= total - 1e-9) idx = startIdx // si chiude sulla tonica
                else {
                    var step = rnd.nextInt(-leap, leap + 1)
                    if (step == 0) step = if (rnd.nextBoolean()) 1 else -1
                    idx = (idx + step).coerceIn(0, scale.lastIndex)
                }
                notes.put(JSONObject().put("beat", beat).put("dur", dur).put("key", scale[idx]).put("hand", hand))
                beat += dur
            }
        }

        if (sp.hand == Hand.BOTH) {
            melody(sp.scale, "R", sp.rhythm, sp.maxLeap, 0)
            // la sinistra suona note lunghe, una o due per battuta
            val bass = JSONArray()
            var beat = 0.0
            var idx = 0
            while (beat < total - 1e-9) {
                val dur = minOf(total - beat, if (rnd.nextBoolean()) 4.0 else 2.0)
                if (beat + dur >= total - 1e-9) idx = 0 else idx = (idx + rnd.nextInt(-1, 2)).coerceIn(0, sp.leftScale.lastIndex)
                bass.put(JSONObject().put("beat", beat).put("dur", dur).put("key", sp.leftScale[idx]).put("hand", "L"))
                beat += dur
            }
            for (i in 0 until bass.length()) notes.put(bass.get(i))
        } else {
            melody(sp.scale, if (sp.hand == Hand.LEFT) "L" else "R", sp.rhythm, sp.maxLeap, 0)
        }

        val json = JSONObject().put("bpm", sp.bpm).put("timeSignature", JSONArray(listOf(4, 4))).put("keyFifths", sp.keyFifths).put("notes", notes)
        val id = "$PREFIX$lv-$seed"
        val title = "Lettura a prima vista · livello $lv"
        val song = InlineSongParser.parse(json.put("title", title), id)
        val text = "Melodia nuova: ${sp.title.lowercase()}. Leggi lo spartito e suona a tempo, senza nomi sulle note."
        val steps = listOf(LearnStep(sp.hand, StudyMode.TEMPO, 1.0, 1, "Suona a tempo quello che leggi."))
        return ExerciseSlide(id, title, text, song, steps)
    }

    /** Con 3 stelle si sale di livello, con 0 o 1 si scende (ma mai sotto il primo); con 2 si resta. */
    fun nextLevel(level: Int, stars: Int): Int = when {
        stars >= 3 -> (level + 1).coerceAtMost(MAX_LEVEL)
        stars <= 0 -> (level - 1).coerceAtLeast(1)
        else -> level
    }
}
