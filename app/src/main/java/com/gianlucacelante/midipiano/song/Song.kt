package com.gianlucacelante.midipiano.song

enum class Hand { LEFT, RIGHT, BOTH }

/** Nota di un brano. Tempi in secondi al tempo originale. */
data class SongNote(
    val startSec: Double,
    val durationSec: Double,
    val key: Int, // numero MIDI (60 = Do centrale)
    val velocity: Int,
    val hand: Hand, // LEFT o RIGHT (BOTH non è valido per una singola nota)
    /** Durata in quarti (1.0 = semiminima). 0 se sconosciuta: la notazione la ricava da [durationSec]. */
    val beats: Double = 0.0,
    /** Ortografia scritta nello spartito: indice diatonico (ottava*7 + passo, Do=0) o null se va dedotta. */
    val diatonic: Int? = null,
    /** Alterazione scritta (-1 bemolle, 0, +1 diesis); valida solo con [diatonic]. */
    val alter: Int = 0,
    /** Sillaba cantata su questa nota (dal file, se c'è); con "-" se la parola continua. */
    val lyric: String? = null,
)

/** Origine e licenza del brano: obbligatorie, così sappiamo sempre cosa possiamo farne. */
data class SongSource(
    val origin: String, // es. "Mutopia Project", "OpenScore", "file importato"
    val license: String, // es. "CC0", "Public Domain", "personale"
    val url: String? = null,
)

/** Da [timeSec] in poi un quarto dura [secPerBeat] secondi. */
data class TempoChange(val timeSec: Double, val secPerBeat: Double)

/** Da [timeSec] in poi il metro è [numerator]/[denominator]. */
data class TimeChange(val timeSec: Double, val numerator: Int, val denominator: Int)

/** Da [timeSec] in poi l'armatura ha [fifths] diesis (>0) o bemolli (<0). */
data class KeyChange(val timeSec: Double, val fifths: Int)

/** Un movimento (battito del metronomo) a [timeSec]; [accent] se apre la battuta. */
data class Beat(val timeSec: Double, val accent: Boolean)

/** Sigla d'accordo scritta sopra lo spartito. */
data class ChordSymbol(val timeSec: Double, val text: String)

data class Song(
    val id: String,
    val title: String,
    val composer: String? = null,
    val source: SongSource,
    val notes: List<SongNote>,
    /** Istanti (secondi) delle stanghette di battuta; vuoto se sconosciuti. */
    val barLines: List<Double> = emptyList(),
    /** Armatura di chiave: numero di diesis (>0) o bemolli (<0). */
    val keyFifths: Int = 0,
    val timeNumerator: Int = 4,
    val timeDenominator: Int = 4,
    /** Durata di un quarto al tempo iniziale del brano (0,5 s = 120 bpm). */
    val secPerBeat: Double = 0.5,
    /** Sigle degli accordi (C, Am, B♭/F...) con l'istante in cui compaiono. */
    val chordSymbols: List<ChordSymbol> = emptyList(),
    /** Brano costruito da una griglia di accordi: gli accordi sono il contenuto, non si semplificano. */
    val chordChart: Boolean = false,
    /** Cambi di tempo (ordinati). Vuoto = tempo costante [secPerBeat]. */
    val tempoChanges: List<TempoChange> = emptyList(),
    /** Cambi di metro dopo l'inizio (ordinati); il metro iniziale è [timeNumerator]/[timeDenominator]. */
    val timeChanges: List<TimeChange> = emptyList(),
    /** Cambi di armatura dopo l'inizio (ordinati); l'armatura iniziale è [keyFifths]. */
    val keyChanges: List<KeyChange> = emptyList(),
    /** Battute (inizio, fine in secondi) che una versione semplificata ha cambiato rispetto all'originale. */
    val simplifiedSpans: List<ClosedFloatingPointRange<Double>> = emptyList(),
) {
    /** Metro (numeratore, denominatore) in vigore all'istante [sec]. */
    fun timeSignatureAt(sec: Double): Pair<Int, Int> {
        var r = timeNumerator to timeDenominator
        for (c in timeChanges) { if (c.timeSec <= sec + 1e-6) r = c.numerator to c.denominator else break }
        return r
    }

    /** Armatura (diesis > 0, bemolli < 0) in vigore all'istante [sec]. */
    fun keyFifthsAt(sec: Double): Int {
        var r = keyFifths
        for (c in keyChanges) { if (c.timeSec <= sec + 1e-6) r = c.fifths else break }
        return r
    }

    /**
     * Tutti i movimenti del brano (battiti del metronomo), seguendo cambi di metro e di tempo.
     * Con un levare la battuta prima della prima stanghetta comincia prima di zero: i suoi movimenti precedenti non ci sono.
     */
    val beatGrid: List<Beat> by lazy {
        val out = ArrayList<Beat>()
        val firstBarQuarters = timeNumerator * (4.0 / timeDenominator)
        val pickup = (barLines.firstOrNull() ?: Double.MAX_VALUE) < barDurationSec * 0.9
        val starts = ArrayList<Double>()
        starts.add(if (pickup) barLines[0] - firstBarQuarters * secPerBeat else 0.0)
        starts.addAll(barLines)
        val end = durationSec
        var i = 0
        var s = starts[0]
        while (s < end + 1e-6 && i < 20_000) {
            val (num, den) = timeSignatureAt(maxOf(s, 0.0))
            val quarters = num * (4.0 / den)
            val moves = if (den == 8 && num % 3 == 0) num / 3 else num
            val nextStart = starts.getOrNull(i + 1)
            for (k in 0 until moves) {
                val t = secAfter(s, k * quarters / moves)
                if (nextStart != null && t >= nextStart - 1e-6) break
                if (t >= -1e-9) out.add(Beat(t.coerceAtLeast(0.0), k == 0))
            }
            s = nextStart ?: secAfter(s, quarters)
            i++
        }
        out
    }

    /** Posizione del movimento a [sec]: (numero nella battuta da 1, movimenti nella battuta), o null prima del primo. */
    fun beatPositionAt(sec: Double): Pair<Int, Int>? {
        val i = beatIndexAt(sec)
        if (i < 0) return null
        var j = i
        while (j > 0 && !beatGrid[j].accent) j--
        var k = i + 1
        while (k < beatGrid.size && !beatGrid[k].accent) k++
        return (i - j + 1) to (k - j)
    }

    /** Durata di una battuta in secondi al metro e al tempo in vigore a [sec]. */
    fun barDurationAt(sec: Double): Double { val (n, d) = timeSignatureAt(sec); return n * (4.0 / d) * secPerBeatAt(sec) }

    /** Movimenti per battuta al metro in vigore a [sec]. */
    fun beatsPerBarAt(sec: Double): Int { val (n, d) = timeSignatureAt(sec); return if (d == 8 && n % 3 == 0) n / 3 else n }

    /** Durata di un movimento a [sec]. */
    fun beatSecAt(sec: Double): Double = barDurationAt(sec) / beatsPerBarAt(sec)

    /** Indice dell'ultimo movimento a [sec] o prima (−1 se è prima del primo). */
    fun beatIndexAt(sec: Double): Int {
        val g = beatGrid
        var lo = 0
        var hi = g.size - 1
        var r = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (g[mid].timeSec <= sec + 1e-9) { r = mid; lo = mid + 1 } else hi = mid - 1
        }
        return r
    }

    /** Durata di un quarto all'istante [sec], seguendo i cambi di tempo. */
    fun secPerBeatAt(sec: Double): Double {
        var v = secPerBeat
        for (c in tempoChanges) { if (c.timeSec <= sec + 1e-9) v = c.secPerBeat else break }
        return v
    }

    /** Quarti fra due istanti, sommando i tratti a tempo diverso. */
    fun beatsBetween(from: Double, to: Double): Double {
        if (to <= from) return 0.0
        if (tempoChanges.isEmpty()) return (to - from) / secPerBeat
        var t = from
        var total = 0.0
        while (t < to - 1e-12) {
            val next = tempoChanges.firstOrNull { it.timeSec > t + 1e-9 }?.timeSec ?: Double.MAX_VALUE
            val end = minOf(to, next)
            total += (end - t) / secPerBeatAt(t)
            t = end
        }
        return total
    }

    /** L'istante in cui, partendo da [start], sono passati [beats] quarti. */
    fun secAfter(start: Double, beats: Double): Double {
        if (tempoChanges.isEmpty()) return start + beats * secPerBeat
        var t = start
        var left = beats
        while (left > 1e-12) {
            val spb = secPerBeatAt(t)
            val next = tempoChanges.firstOrNull { it.timeSec > t + 1e-9 }?.timeSec ?: Double.MAX_VALUE
            val room = (next - t) / spb
            if (left <= room) return t + left * spb
            left -= room
            t = next
        }
        return t
    }

    /** Durata di una battuta in secondi, al tempo iniziale. */
    val barDurationSec: Double get() = timeNumerator * (4.0 / timeDenominator) * secPerBeat

    /** Movimenti per battuta: i tempi composti (6/8, 9/8, 12/8) si contano a quarti col punto. */
    val beatsPerBar: Int get() = if (timeDenominator == 8 && timeNumerator % 3 == 0) timeNumerator / 3 else timeNumerator

    /** Durata di un movimento (battito del metronomo e del conto alla rovescia). */
    val beatSec: Double get() = barDurationSec / beatsPerBar

    /** Numero di battute (dalla 1), contando quella in corso alla fine. */
    val barCount: Int get() = barLines.size + 1

    /** Istante di inizio della battuta [bar] (da 1). */
    fun barStartSec(bar: Int): Double = if (bar <= 1) 0.0 else barLines.getOrElse(bar - 2) { durationSec }

    /** Istante di fine della battuta [bar] (da 1). */
    fun barEndSec(bar: Int): Double = barLines.getOrElse(bar - 1) { durationSec }

    /** Battuta (da 1) che contiene l'istante [timeSec]. */
    fun barAt(timeSec: Double): Int = (barLines.count { it <= timeSec } + 1).coerceIn(1, barCount)

    val durationSec: Double get() = notes.maxOfOrNull { it.startSec + it.durationSec } ?: 0.0

    /**
     * Raggruppa le note che iniziano insieme (tolleranza [toleranceSec]) in "passi" per la
     * modalità attesa. Se [hand] non è BOTH, tiene solo le note di quella mano.
     */
    fun steps(hand: Hand = Hand.BOTH, toleranceSec: Double = 0.03): List<Step> {
        val selected = notes.filter { hand == Hand.BOTH || it.hand == hand }.sortedBy { it.startSec }
        val result = ArrayList<Step>()
        var current = ArrayList<SongNote>()
        var start = 0.0
        for (n in selected) {
            if (current.isNotEmpty() && n.startSec - start > toleranceSec) {
                result.add(Step(start, current))
                current = ArrayList()
            }
            if (current.isEmpty()) start = n.startSec
            current.add(n)
        }
        if (current.isNotEmpty()) result.add(Step(start, current))
        return result
    }
}

/** Gruppo di note da suonare "insieme" (un accordo, o una singola nota). */
data class Step(val startSec: Double, val notes: List<SongNote>) {
    val keys: Set<Int> get() = notes.map { it.key }.toSet()
}
