package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import kotlin.math.abs

enum class Clef(val bottomDiatonic: Int) {
    TREBLE(30), // Mi4 = prima linea
    BASS(18), // Sol2 = prima linea
}

private val DOTTED_VALUES = doubleArrayOf(0.375, 0.75, 1.5, 3.0)
private val TRIPLET_VALUES = doubleArrayOf(1.0 / 6, 1.0 / 3, 2.0 / 3, 4.0 / 3)

enum class Accidental { SHARP, FLAT, NATURAL }

/**
 * Nota pronta da disegnare. [position] conta i gradi diatonici sopra la linea più bassa del rigo:
 * 0 = prima linea, 1 = primo spazio, 2 = seconda linea, ... 8 = quinta linea. Negativo/oltre 8 = fuori rigo.
 */
data class StaffNote(
    val key: Int,
    val clef: Clef,
    val startSec: Double,
    val durationSec: Double,
    val position: Int,
    val accidental: Accidental?,
    val beats: Double,
    val hand: Hand,
    /** Testa spostata sul lato opposto del gambo (accordi con note a distanza di seconda). */
    val shifted: Boolean = false,
    val lyric: String? = null,
    /** La nota continua (legata) nella parte successiva, oltre la stanghetta. */
    val tieNext: Boolean = false,
    /** Questa parte è la continuazione legata di una nota iniziata nella battuta prima. */
    val tieFromPrev: Boolean = false,
) {
    val stemUp: Boolean get() = position < 4 // dalla linea centrale in su il gambo scende
    /** Terzina: 1/6, 1/3, 2/3 o 4/3 di quarto (tre note nello spazio di due). */
    val isTriplet: Boolean
        get() = TRIPLET_VALUES.any { kotlin.math.abs(beats - it) < 2e-3 }

    /** Valore col punto di valore (3/2 del valore base): 0,375 / 0,75 / 1,5 / 3 quarti. */
    val dotted: Boolean
        get() = !isTriplet && DOTTED_VALUES.any { kotlin.math.abs(beats - it) < 1e-6 }

    /** Valore scritto senza il punto (e per le terzine il valore "normale"): serve a decidere testa, gambo e code. */
    private val baseBeats: Double get() = if (isTriplet) beats * 1.5 else if (dotted) beats / 1.5 else beats

    val hasStem: Boolean get() = baseBeats < 4.0
    val filled: Boolean get() = baseBeats < 2.0

    /** Numero di code (1 = croma, 2 = semicroma, 3 = biscroma). */
    val flags: Int
        get() = when {
            baseBeats >= 1.0 -> 0
            baseBeats >= 0.5 -> 1
            baseBeats >= 0.25 -> 2
            else -> 3
        }

    /** Nome in solfeggio come è scritta la nota (si♭ e non la♯ in una tonalità a bemolli); calcolato una volta sola. */
    val noteName: String by lazy(LazyThreadSafetyMode.NONE) { NoteNames.spelled(key, position + clef.bottomDiatonic) }

    /** Posizioni (pari) dei tagli addizionali necessari per questa nota (calcolate una volta sola). */
    val ledgerPositions: List<Int> by lazy(LazyThreadSafetyMode.NONE) {
        when {
            position < 0 -> generateSequence(-2) { it - 2 }.takeWhile { it >= position }.toList()
            position > 8 -> generateSequence(10) { it + 2 }.takeWhile { it <= position }.toList()
            else -> emptyList()
        }
    }
}

data class KeySignatureSymbol(val accidental: Accidental, val position: Int)

enum class NoteState { PLAYED, CURRENT, UPCOMING, MISSED }

/** Motore di notazione: trasforma un brano in note posizionate su due righi (chiave di violino e di basso). */
object NotationLayout {
    // Passi (Do=0 ... Si=6) in ordine di comparsa nell'armatura
    private val SHARP_ORDER = intArrayOf(3, 0, 4, 1, 5, 2, 6) // Fa Do Sol Re La Mi Si
    private val FLAT_ORDER = intArrayOf(6, 2, 5, 1, 4, 0, 3) // Si Mi La Re Sol Do Fa

    // Per classe di altezza (0..11): passo e alterazione, scrivendo con diesis o con bemolli
    private val SHARP_STEP = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
    private val SHARP_ALTER = intArrayOf(0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0)
    private val FLAT_STEP = intArrayOf(0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6)
    private val FLAT_ALTER = intArrayOf(0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, 0)

    // Posizioni in chiave di violino dei simboli d'armatura, nell'ordine di SHARP_ORDER / FLAT_ORDER
    private val TREBLE_SHARP_POS = intArrayOf(8, 5, 9, 6, 3, 7, 4)
    private val TREBLE_FLAT_POS = intArrayOf(4, 7, 3, 6, 2, 5, 1)

    private const val SAME_TIME_SEC = 0.03

    /** Una nota con la sua ortografia (indice diatonico e alterazione), prima di decidere l'alterazione da scrivere. */
    private class Spelled(val staff: StaffNote, val diatonic: Int, val alter: Int)

    fun layout(song: Song, hand: Hand): List<StaffNote> {
        val state = HashMap<Long, Int>() // (rigo, altezza) -> alterazione in vigore nella battuta
        var currentBar = -1
        val notes = song.notes.asSequence()
            .filter { hand == Hand.BOTH || it.hand == hand }
            .map { spell(it, song) }
            .map { sp ->
                val bar = song.barAt(sp.staff.startSec)
                if (bar != currentBar) { // una nuova battuta annulla le alterazioni
                    state.clear()
                    currentBar = bar
                }
                val k = sp.staff.clef.ordinal * 1000L + sp.diatonic
                val effective = state[k] ?: keyAlter(sp.diatonic % 7, song.keyFifthsAt(sp.staff.startSec))
                state[k] = sp.alter
                sp.staff.copy(accidental = accidentalFor(sp.alter, effective))
            }
            .toList()
        return markShifted(splitNonStandard(splitAtBars(notes, song), song))
    }

    /** Una nota che attraversa la stanghetta si scrive in due parti legate: la prima fino alla stanghetta, il resto dopo. */
    private fun splitAtBars(notes: List<StaffNote>, song: Song): List<StaffNote> {
        val bars = song.barLines
        if (bars.isEmpty()) return notes
        val out = ArrayList<StaffNote>(notes.size + 8)
        for (n in notes) {
            var cur = n
            var done = false
            var guard = 0
            while (!done && guard++ < 8) {
                val nextBar = bars.firstOrNull { it > cur.startSec + 1e-6 }
                val toBar = if (nextBar == null) Double.MAX_VALUE else song.beatsBetween(cur.startSec, nextBar) // quarti fino alla stanghetta (col tempo reale)
                if (nextBar == null || cur.beats <= toBar + 0.1) { out += cur; done = true; continue }
                val head = NoteValues.quantize(toBar)
                val rest = cur.beats - head
                if (rest < 0.12) { out += cur.copy(beats = head); done = true; continue } // eccedenza trascurabile
                out += cur.copy(beats = head, durationSec = nextBar - cur.startSec, tieNext = true)
                cur = cur.copy(
                    startSec = nextBar, durationSec = (cur.durationSec - (nextBar - cur.startSec)).coerceAtLeast(0.05),
                    beats = NoteValues.quantize(rest), accidental = null, lyric = null, tieNext = false, tieFromPrev = true,
                )
            }
            if (!done) out += cur
        }
        return out
    }

    private val STANDARD = doubleArrayOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.375, 0.25, 0.125)

    /** Le durate esatte che non sono un valore scritto (2,5 = minima + croma, 3,5 = minima col punto + croma, 0,875...) si legano. */
    private fun splitNonStandard(notes: List<StaffNote>, song: Song): List<StaffNote> {
        val out = ArrayList<StaffNote>(notes.size + 4)
        for (n in notes) {
            if (STANDARD.any { abs(it - n.beats) < 1e-6 } || n.isTriplet || n.beats < 0.1) { out += n; continue }
            var remaining = n.beats
            var cur = n
            var first = true
            var guard = 0
            while (remaining > 0.1 && guard++ < 6) {
                val v = STANDARD.firstOrNull { it <= remaining + 1e-6 } ?: break
                val last = remaining - v < 0.1
                val partDur = song.secAfter(cur.startSec, v) - cur.startSec
                out += cur.copy(beats = v, durationSec = partDur, tieNext = !last || n.tieNext, tieFromPrev = !first || n.tieFromPrev,
                    accidental = if (first) cur.accidental else null, lyric = if (first) cur.lyric else null)
                remaining -= v
                if (!last) cur = cur.copy(startSec = song.secAfter(cur.startSec, v))
                first = false
            }
        }
        return out
    }

    private fun accidentalFor(alter: Int, effective: Int): Accidental? = when {
        alter == effective -> null
        alter > 0 -> Accidental.SHARP
        alter < 0 -> Accidental.FLAT
        else -> Accidental.NATURAL
    }

    /** Stato di una nota rispetto al passo corrente della modalità attesa. */
    fun stateOf(note: StaffNote, currentTimeSec: Double, expectedKeys: Set<Int>): NoteState = when {
        note.startSec < currentTimeSec - SAME_TIME_SEC -> NoteState.PLAYED
        note.startSec <= currentTimeSec + SAME_TIME_SEC ->
            if (note.key in expectedKeys) NoteState.CURRENT else NoteState.PLAYED
        else -> NoteState.UPCOMING
    }

    /** Nelle note simultanee dello stesso rigo, a distanza di seconda, alterna il lato della testa. */
    private fun markShifted(notes: List<StaffNote>): List<StaffNote> {
        val shiftedKeys = HashSet<Triple<Clef, Long, Int>>()
        notes.groupBy { it.clef to Math.round(it.startSec / SAME_TIME_SEC) }.forEach { (group, chord) ->
            var previous: StaffNote? = null
            var previousShifted = false
            for (n in chord.sortedBy { it.position }) {
                val p = previous
                val shift = p != null && n.position - p.position == 1 && !previousShifted
                if (shift) shiftedKeys.add(Triple(group.first, group.second, n.key))
                previous = n
                previousShifted = shift
            }
        }
        return notes.map {
            if (Triple(it.clef, Math.round(it.startSec / SAME_TIME_SEC), it.key) in shiftedKeys) it.copy(shifted = true) else it
        }
    }

    private const val LEFT_TO_TREBLE_FROM = 69 // La4
    private const val RIGHT_TO_BASS_UP_TO = 50 // Re3

    private fun spell(n: SongNote, song: Song): Spelled {
        // La mano decide il rigo, tranne per le note estreme: alla sinistra molto acute si scrivono in chiave di violino,
        // alla destra molto gravi in chiave di basso (altrimenti servirebbero una decina di tagli addizionali)
        val clef = when {
            n.hand == Hand.LEFT && n.key >= LEFT_TO_TREBLE_FROM -> Clef.TREBLE
            n.hand != Hand.LEFT && n.key <= RIGHT_TO_BASS_UP_TO -> Clef.BASS
            n.hand == Hand.LEFT -> Clef.BASS
            else -> Clef.TREBLE
        }
        val diatonic: Int
        val alter: Int
        if (n.diatonic != null) {
            diatonic = n.diatonic
            alter = n.alter
        } else {
            val pc = n.key % 12
            val octave = n.key / 12 - 1
            val sharps = song.keyFifthsAt(n.startSec) >= 0
            diatonic = octave * 7 + if (sharps) SHARP_STEP[pc] else FLAT_STEP[pc]
            alter = if (sharps) SHARP_ALTER[pc] else FLAT_ALTER[pc]
        }
        val beats = if (n.beats > 0.0) n.beats else beatsFromSeconds(n.startSec, n.durationSec, song)
        val staff = StaffNote(n.key, clef, n.startSec, n.durationSec, diatonic - clef.bottomDiatonic, null, beats, n.hand, lyric = n.lyric)
        return Spelled(staff, diatonic, alter)
    }

    /** Stima dei quarti quando la nota non li indica (versioni semplificate, brani senza durate scritte): dal tempo reale del brano. */
    private fun beatsFromSeconds(startSec: Double, durationSec: Double, song: Song): Double =
        NoteValues.quantize(song.beatsBetween(startSec, startSec + durationSec))

    /** Alterazione imposta dall'armatura al passo [step] (Do=0 ... Si=6). */
    private fun keyAlter(step: Int, fifths: Int): Int = when {
        fifths > 0 -> if (SHARP_ORDER.take(minOf(fifths, 7)).contains(step)) 1 else 0
        fifths < 0 -> if (FLAT_ORDER.take(minOf(-fifths, 7)).contains(step)) -1 else 0
        else -> 0
    }

    fun keySignature(fifths: Int, clef: Clef): List<KeySignatureSymbol> {
        val offset = if (clef == Clef.BASS) -2 else 0
        val n = minOf(abs(fifths), 7)
        return when {
            fifths > 0 -> (0 until n).map { KeySignatureSymbol(Accidental.SHARP, TREBLE_SHARP_POS[it] + offset) }
            fifths < 0 -> (0 until n).map { KeySignatureSymbol(Accidental.FLAT, TREBLE_FLAT_POS[it] + offset) }
            else -> emptyList()
        }
    }
}
