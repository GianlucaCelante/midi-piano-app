package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Versioni di un brano per livello (i pallini verde / giallo / rosso):
 *  - [EASY]: una sola nota alla volta a destra, sulle pulsazioni o lunga; a sinistra una nota per battuta.
 *  - [MEDIUM]: la melodia intera a destra senza abbellimenti; a sinistra solo le note sulle pulsazioni, al massimo due insieme.
 *  - [ORIGINAL]: il brano com'è.
 * Le note con una sillaba non si tolgono mai, e quelle tenute riempiono lo spazio lasciato dalle note tolte,
 * così il ritmo e lo stile restano riconoscibili.
 */
object Simplifier {
    const val EASY = 1
    const val MEDIUM = 2
    const val ORIGINAL = 3

    private const val SAME_STEP_SEC = 0.012
    private const val BEAT_TOLERANCE = 0.06 // frazione di pulsazione
    private const val MIN_GAIN = 0.85 // una versione si offre solo se ha almeno il 15 % di note in meno della successiva

    /**
     * La versione di [level]. La semplificazione guarda la difficoltà tecnica di ogni battuta ([TechnicalDifficulty]):
     * una battuta già facile resta quella dell'originale; una difficile si sostituisce con la forma semplificata,
     * e mai con una più difficile. La versione facile non è mai più difficile della media, e questa mai dell'originale.
     */
    fun version(song: Song, level: Int): Song {
        if (level >= ORIGINAL || song.notes.isEmpty() || song.chordChart) return song
        val medium = simplifyAll(song, MEDIUM)
        val easy = if (level == EASY) simplifyAll(song, EASY) else null
        val wins = TechnicalDifficulty.windows(song)
        val out = ArrayList<SongNote>(song.notes.size)
        val simplified = BooleanArray(wins.size)
        for ((i, w) in wins.withIndex()) {
            val (s, e) = w
            val orig = TechnicalDifficulty.notesIn(song.notes, s, e)
            val so = TechnicalDifficulty.scoreNotes(song, orig, s, e)
            val med = TechnicalDifficulty.notesIn(medium, s, e)
            val sm = TechnicalDifficulty.scoreNotes(song, med, s, e)
            // scelta per la media: l'originale se è già abbastanza facile, altrimenti la forma semplificata (se non peggiora)
            val medPick: Pair<List<SongNote>, Double> =
                if (so <= TechnicalDifficulty.MEDIUM_MAX) orig to so
                else if (sm <= TechnicalDifficulty.MEDIUM_MAX || sm <= so) med to sm else orig to so
            val chosen: List<SongNote> = if (easy == null) medPick.first else {
                if (so <= TechnicalDifficulty.EASY_MAX) orig else {
                    val ea = TechnicalDifficulty.notesIn(easy, s, e)
                    val se = TechnicalDifficulty.scoreNotes(song, ea, s, e)
                    when {
                        se <= TechnicalDifficulty.EASY_MAX && se <= medPick.second -> ea
                        medPick.second <= TechnicalDifficulty.EASY_MAX -> medPick.first
                        se <= medPick.second -> ea
                        else -> medPick.first
                    }
                }
            }
            simplified[i] = chosen !== orig
            // le note semplificate che finiscono dentro una battuta originale non devono sovrapporsi alle sue note
            val cap = if (simplified[i]) e else Double.MAX_VALUE
            out += chosen.map { n -> if (simplified[i] && wins.getOrNull(i + 1) != null && n.startSec + n.durationSec > cap + 1e-6) n.copy(durationSec = (cap - n.startSec).coerceAtLeast(0.05), beats = 0.0) else n }
        }
        // le battute successive a una semplificata e anch'esse semplificate lasciano finire le note: solo il confine con l'originale si taglia
        val spans = wins.filterIndexed { i, _ -> simplified[i] }.map { it.first..it.second }
        return song.copy(notes = out.sortedWith(compareBy({ it.startSec }, { it.key })), simplifiedSpans = spans)
    }

    /** La semplificazione di tutto il brano, senza guardare la difficoltà delle battute (la base di [version]). */
    internal fun simplifyAll(song: Song, level: Int): List<SongNote> {
        val right = melody(song, song.notes.filter { it.hand == Hand.RIGHT }, level)
        val left = bass(song, song.notes.filter { it.hand == Hand.LEFT }, level)
        return right + left
    }

    /** Versioni davvero diverse fra loro: sempre l'originale, e quelle più facili solo se hanno parecchie note in meno. */
    fun availableVersions(song: Song): List<Int> {
        if (song.chordChart) return listOf(ORIGINAL)
        val n3 = song.notes.size
        val n2 = version(song, MEDIUM).notes.size
        val n1 = version(song, EASY).notes.size
        val out = mutableListOf(ORIGINAL)
        var upper = n3
        if (n2 in 1..(upper * MIN_GAIN).toInt()) { out.add(0, MEDIUM); upper = n2 }
        if (n1 in 1..(upper * MIN_GAIN).toInt()) out.add(0, EASY)
        return out
    }

    private fun steps(notes: List<SongNote>): List<List<SongNote>> {
        val sorted = notes.sortedWith(compareBy({ it.startSec }, { it.key }))
        val out = ArrayList<List<SongNote>>()
        for (n in sorted) {
            val last = out.lastOrNull()
            if (last != null && n.startSec - last.first().startSec <= SAME_STEP_SEC) out[out.lastIndex] = last + n else out.add(listOf(n))
        }
        return out
    }

    /** Una pulsazione cade dove i quarti dall'inizio della battuta sono un multiplo del movimento (quarto, o quarto col punto nei tempi composti). */
    private fun onBeat(song: Song, t: Double): Boolean {
        val bar = song.barAt(t.coerceAtLeast(0.0))
        val pickup = (song.barLines.firstOrNull() ?: Double.MAX_VALUE) < song.barDurationSec * 0.9
        // con un levare la battuta 1 è incompleta: la sua battuta "virtuale" comincia prima di zero
        val start = if (bar == 1 && pickup) song.barLines[0] - song.barDurationSec else song.barStartSec(bar)
        val (tn, td) = song.timeSignatureAt(t.coerceAtLeast(0.0))
        val pulse = if (td == 8 && tn % 3 == 0) 1.5 else 4.0 / td
        val x = song.beatsBetween(start, t) / pulse
        return abs(x - x.roundToLong()) < BEAT_TOLERANCE
    }

    private fun barStarts(song: Song): List<Double> {
        if (song.barLines.isNotEmpty()) return listOf(0.0) + song.barLines
        val len = song.barDurationSec
        return generateSequence(0.0) { it + len }.takeWhile { it < song.durationSec }.toList()
    }

    /** Mano destra: una linea sola (la nota più alta); si tolgono le note brevi fuori pulsazione. */
    private fun melody(song: Song, notes: List<SongNote>, level: Int): List<SongNote> {
        val top = steps(notes).map { st -> st.maxByOrNull { it.key }!!.let { keep -> keep.copy(lyric = st.firstNotNullOfOrNull { it.lyric } ?: keep.lyric) } }
        val fromBeat = { n: SongNote -> song.secPerBeatAt(n.startSec) } // il tempo del brano può cambiare
        val kept = ArrayList<SongNote>()
        for (n in top) {
            val beats = n.durationSec / fromBeat(n)
            // una voce interna sotto una nota di melodia ancora in suono non è melodia: si salta (salvo sillaba)
            val prevHeld = kept.lastOrNull()
            if (prevHeld != null && n.lyric == null && prevHeld.startSec + prevHeld.durationSec > n.startSec + SAME_STEP_SEC && prevHeld.key > n.key) continue
            val important = n.lyric != null || kept.isEmpty() ||
                if (level == EASY) onBeat(song, n.startSec) || beats >= 0.9
                else onBeat(song, n.startSec) || beats >= 0.45
            if (!important) continue
            // nel livello facile due note vicine (meno di mezza pulsazione) restano una sola, salvo sillabe
            val prev = kept.lastOrNull()
            if (level == EASY && prev != null && n.lyric == null && n.startSec - prev.startSec < 0.5 * fromBeat(n)) continue
            kept.add(n)
        }
        return fill(song, kept)
    }

    /** Mano sinistra: EASY una nota (il basso) per battuta; MEDIUM note sulle pulsazioni, al massimo due insieme. */
    private fun bass(song: Song, notes: List<SongNote>, level: Int): List<SongNote> {
        if (notes.isEmpty()) return emptyList()
        val st = steps(notes)
        if (level == EASY) {
            val bars = barStarts(song)
            val out = ArrayList<SongNote>()
            for (b in bars.indices) {
                val from = bars[b] - 1e-6
                val to = bars.getOrElse(b + 1) { Double.MAX_VALUE } - 1e-6
                val first = st.firstOrNull { it.first().startSec in from..to } ?: continue
                val low = first.minByOrNull { it.key }!!
                val end = minOf(bars.getOrElse(b + 1) { low.startSec + low.durationSec }, low.startSec + 2 * song.barDurationSec)
                out.add(low.copy(durationSec = (end - low.startSec) * 0.97, beats = 0.0, lyric = null))
            }
            return out
        }
        val kept = st.filter { onBeat(song, it.first().startSec) }.map { g ->
            val sorted = g.sortedBy { it.key }
            if (sorted.size <= 2) sorted else listOf(sorted.first(), sorted.last())
        }.flatten()
        return fill(song, kept)
    }

    /** Le note tenute arrivano fino alla successiva dello stesso rigo (al massimo due battute): il ritmo resta leggibile. */
    private fun fill(song: Song, kept: List<SongNote>): List<SongNote> {
        val sorted = kept.sortedWith(compareBy({ it.startSec }, { it.key }))
        val starts = sorted.map { it.startSec }.distinct()
        return sorted.map { n ->
            val next = starts.firstOrNull { it > n.startSec + SAME_STEP_SEC }
            if (next == null) n else {
                val gap = next - n.startSec
                if (gap > n.durationSec && gap <= 2 * song.barDurationSec) n.copy(durationSec = gap * 0.97, beats = 0.0) else n
            }
        }
    }
}
