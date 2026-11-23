package com.gianlucacelante.midipiano.song

import kotlin.math.abs

/**
 * Brani "per accordi": invece delle note scritte c'è una griglia di accordi, p.es.
 *
 *     titolo: Giro di Do
 *     tempo: 90
 *     metro: 4/4
 *     tonalita: C
 *     stile: tenuti        (oppure: pulsazioni)
 *     | C | G | Am F | % |
 *
 * Ogni battuta contiene uno o più accordi (che si dividono il tempo); "%" ripete la battuta precedente.
 * L'app costruisce i tasti: la mano destra suona la triade (o la settima) con la posizione più vicina
 * all'accordo precedente, la sinistra il basso (la fondamentale, o il basso scritto dopo la barra: C/E).
 * Così si studia premendo insieme l'insieme di tasti dell'accordo, senza uno spartito nota per nota.
 */
object ChordSongParser {
    private const val MAX_BYTES = 200_000
    private const val MAX_BARS = 400

    class ChordInfo(val tones: List<Int>, val bass: Int)

    private val PITCH = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    private val QUALITIES: Map<String, List<Int>> = mapOf(
        "" to listOf(0, 4, 7), "maj" to listOf(0, 4, 7), "m" to listOf(0, 3, 7),
        "7" to listOf(0, 4, 7, 10), "maj7" to listOf(0, 4, 7, 11), "M7" to listOf(0, 4, 7, 11), "m7" to listOf(0, 3, 7, 10),
        "sus4" to listOf(0, 5, 7), "sus" to listOf(0, 5, 7), "sus2" to listOf(0, 2, 7), "2" to listOf(0, 2, 7), "4" to listOf(0, 5, 7),
        "dim" to listOf(0, 3, 6), "aug" to listOf(0, 4, 8), "+" to listOf(0, 4, 8),
        "6" to listOf(0, 4, 7, 9), "m6" to listOf(0, 3, 7, 9), "9" to listOf(0, 4, 10, 2), "add9" to listOf(0, 4, 7, 2), "5" to listOf(0, 7),
        "m7b5" to listOf(0, 3, 6, 10), "dim7" to listOf(0, 3, 6, 9),
        "mmaj7" to listOf(0, 3, 7, 11), "7sus4" to listOf(0, 5, 7, 10), "maj9" to listOf(0, 4, 11, 2), "m9" to listOf(0, 3, 10, 2),
        "11" to listOf(0, 7, 10, 5), "13" to listOf(0, 4, 10, 9), "7b9" to listOf(0, 4, 10, 1), "7#9" to listOf(0, 4, 10, 3),
        "7b5" to listOf(0, 4, 6, 10), "6/9" to listOf(0, 4, 9, 2), "69" to listOf(0, 4, 9, 2), "madd9" to listOf(0, 3, 7, 2),
        "m11" to listOf(0, 3, 10, 5), "7sus2" to listOf(0, 2, 7, 10), "maj13" to listOf(0, 4, 11, 9), "m13" to listOf(0, 3, 10, 9),
    )

    /** Altre grafie di uno stesso suffisso: ♭ ♯ Δ ° ø, "Maj", "min", "-", parentesi. */
    private fun normalizeSuffix(raw: String): String {
        var s = raw.trim().replace('♭', 'b').replace('♯', '#').replace("Δ", "maj7").replace("°", "dim").replace("ø", "m7b5")
            .replace("(", "").replace(")", "").replace(" ", "")
        s = s.replace("MAJ", "maj").replace("Maj", "maj").replace("major", "maj").replace("minor", "m").replace("min", "m")
        if (s.startsWith("-")) s = "m" + s.drop(1)
        if (s.startsWith("mi") && !s.startsWith("min")) s = "m" + s.drop(2)
        if (s == "M" ) s = "maj"
        return s
    }

    fun isNoChord(symbol: String): Boolean = symbol.trim().uppercase().replace(".", "") == "NC"

    private fun pc(text: String): Int? {
        if (text.isEmpty()) return null
        var v = PITCH[text[0].uppercaseChar()] ?: return null
        for (c in text.drop(1)) v += when (c) { '#', '♯' -> 1; 'b', '♭' -> -1; else -> return null }
        return Math.floorMod(v, 12)
    }

    /**
     * Note dell'accordo (classi di altezza, fondamentale per prima) e basso; null se non si capisce nemmeno la fondamentale.
     * Un suffisso sconosciuto non fa fallire: si ripiega sulla triade (minore se comincia con "m").
     */
    fun chordTones(symbol: String): ChordInfo? {
        val text = symbol.trim()
        // il "/" è un basso solo se dopo c'è una nota (C/E); in "C6/9" fa parte del suffisso
        val slash = text.lastIndexOf('/')
        val bassPc = if (slash > 0) pc(text.substring(slash + 1)) else null
        val main = if (bassPc != null) text.substring(0, slash) else text
        if (main.isEmpty() || main[0].uppercaseChar() !in PITCH) return null
        var i = 1
        while (i < main.length && main[i] in "#b♯♭") i++
        // "Bb" è si bemolle, ma "Bm" ha la m di minore: la b dopo la lettera è sempre alterazione
        val root = pc(main.substring(0, i)) ?: return null
        val suffix = normalizeSuffix(main.substring(i))
        val quality = QUALITIES[suffix] ?: if (suffix.startsWith("m") && !suffix.startsWith("maj")) QUALITIES.getValue("m") else QUALITIES.getValue("")
        return ChordInfo(quality.map { Math.floorMod(root + it, 12) }, bassPc ?: root)
    }

    private val MAJOR_FIFTHS = mapOf("C" to 0, "G" to 1, "D" to 2, "A" to 3, "E" to 4, "B" to 5, "F#" to 6, "C#" to 7,
        "F" to -1, "Bb" to -2, "Eb" to -3, "Ab" to -4, "Db" to -5, "Gb" to -6)
    private val MINOR_FIFTHS = mapOf("A" to 0, "E" to 1, "B" to 2, "F#" to 3, "C#" to 4, "G#" to 5,
        "D" to -1, "G" to -2, "C" to -3, "F" to -4, "Bb" to -5, "Eb" to -6)

    private fun keyFifths(text: String): Int? {
        val t = text.trim()
        return if (t.endsWith("m") && t.length > 1) MINOR_FIFTHS[t.dropLast(1)] else MAJOR_FIFTHS[t]
    }

    fun parse(bytes: ByteArray, id: String, source: SongSource, fallbackTitle: String = "Brano per accordi", arpeggio: Boolean = false): Song {
        require(bytes.size <= MAX_BYTES) { "File troppo grande per un brano per accordi" }
        var title = fallbackTitle
        var composer: String? = null
        var tempo = 90.0
        var num = 4
        var den = 4
        var fifths = 0
        var beatsStyle = false
        val bars = ArrayList<List<String>>()
        for (raw in String(bytes, Charsets.UTF_8).lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("|")) {
                for (cell in line.split('|').map { it.trim() }.filter { it.isNotEmpty() }) {
                    if (cell == "%") { require(bars.isNotEmpty()) { "«%» ripete la battuta prima, ma non ce n'è una" }; bars.add(bars.last()); require(bars.size <= MAX_BARS) { "Troppe battute (massimo $MAX_BARS)" }; continue }
                    val chords = cell.split(Regex("\\s+"))
                    for (c in chords) require(isNoChord(c) || chordTones(c) != null) { "Accordo non riconosciuto: «$c»" }
                    bars.add(chords)
                    require(bars.size <= MAX_BARS) { "Troppe battute (massimo $MAX_BARS)" }
                }
                continue
            }
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val value = line.substring(colon + 1).trim()
            when (line.substring(0, colon).trim().lowercase()) {
                "titolo", "title" -> title = value.take(80).ifBlank { title }
                "autore", "compositore", "artist" -> composer = value.take(80).ifBlank { null }
                "tempo", "bpm" -> {
                    tempo = value.toDoubleOrNull() ?: throw IllegalArgumentException("Il tempo «$value» non è un numero")
                    require(tempo in 30.0..240.0) { "Il tempo deve stare fra 30 e 240 (trovato $tempo)" }
                }
                "metro", "tempo musicale", "misura" -> {
                    val p = value.split('/')
                    num = p.getOrNull(0)?.trim()?.toIntOrNull() ?: throw IllegalArgumentException("Il metro «$value» non si capisce (esempio: 4/4)")
                    den = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 4
                    require(num in 1..12 && den in listOf(2, 4, 8)) { "Metro non supportato: $value" }
                }
                "tonalita", "tonalità", "key" -> fifths = keyFifths(value) ?: throw IllegalArgumentException("Tonalità non riconosciuta: «$value»")
                "stile", "style" -> beatsStyle = value.lowercase().startsWith("puls")
            }
        }
        require(bars.isNotEmpty()) { "Non ho trovato nessuna riga di accordi: scrivila fra barre, p.es. | C | G | Am F |" }

        val secPerQuarter = 60.0 / tempo
        val barQ = num * 4.0 / den
        val beatQ = 4.0 / den
        val notes = ArrayList<SongNote>()
        val symbols = ArrayList<ChordSymbol>()
        val barLines = ArrayList<Double>()
        var prevVoicing = listOf(60, 64, 67)
        var tQ = 0.0
        for ((bi, chords) in bars.withIndex()) {
            if (bi > 0) barLines.add(tQ * secPerQuarter)
            val chordQ = barQ / chords.size
            for (symbol in chords) {
                val start = tQ * secPerQuarter
                symbols.add(ChordSymbol(start, if (isNoChord(symbol)) "N.C." else symbol))
                if (isNoChord(symbol)) { tQ += chordQ; continue } // senza accordo: battuta di silenzio
                val info = chordTones(symbol)!!
                val voicing = voice(info.tones, prevVoicing)
                prevVoicing = voicing
                emit(notes, info, voicing, tQ, chordQ, secPerQuarter, beatQ, beatsStyle, arpeggio)
                tQ += chordQ
            }
        }
        return Song(
            id = id, title = title, composer = composer, source = source,
            notes = notes.sortedWith(compareBy({ it.startSec }, { it.key })),
            barLines = barLines, keyFifths = fifths, timeNumerator = num, timeDenominator = den,
            secPerBeat = secPerQuarter, chordSymbols = symbols, chordChart = true,
        )
    }

    /** Le note di un accordo: basso a sinistra e triade a destra (insieme, ribattuta a ogni pulsazione, o spezzata in arpeggio). */
    private fun emit(
        notes: MutableList<SongNote>, info: ChordInfo, voicing: List<Int>, startQ: Double, chordQ: Double,
        secPerQuarter: Double, beatQ: Double, beatsStyle: Boolean, arpeggio: Boolean,
    ) {
        val start = startQ * secPerQuarter
        notes.add(SongNote(start, chordQ * secPerQuarter * 0.97, 36 + info.bass, 85, Hand.LEFT, chordQ))
        if (arpeggio) { // una nota alla volta, salendo e ripartendo: i tasti dell'accordo si suonano separati
            var q = 0.0
            var i = 0
            while (q < chordQ - 1e-9) {
                val len = minOf(beatQ, chordQ - q)
                notes.add(SongNote((startQ + q) * secPerQuarter, len * secPerQuarter * 0.95, voicing[i % voicing.size], 80, Hand.RIGHT, len))
                q += beatQ; i++
            }
        } else if (beatsStyle) {
            var q = 0.0
            while (q < chordQ - 1e-9) {
                val len = minOf(beatQ, chordQ - q)
                for (k in voicing) notes.add(SongNote((startQ + q) * secPerQuarter, len * secPerQuarter * 0.9, k, 80, Hand.RIGHT, len))
                q += beatQ
            }
        } else {
            for (k in voicing) notes.add(SongNote(start, chordQ * secPerQuarter * 0.97, k, 80, Hand.RIGHT, chordQ))
        }
    }

    /**
     * Da un brano con le sigle degli accordi scritte sopra lo spartito (p.es. un MusicXML) ricava la versione "solo accordi":
     * ogni sigla dura fino alla successiva; melodia e note scritte non si usano.
     */
    fun fromSymbols(song: Song, arpeggio: Boolean = false): Song {
        val symbols = song.chordSymbols.sortedBy { it.timeSec }
        val spq = song.secPerBeat
        val beatQ = 4.0 / song.timeDenominator
        val notes = ArrayList<SongNote>()
        var prevVoicing = listOf(60, 64, 67)
        val used = ArrayList<ChordSymbol>()
        for ((i, sym) in symbols.withIndex()) {
            if (isNoChord(sym.text)) { used.add(ChordSymbol(sym.timeSec, "N.C.")); continue }
            val info = chordTones(sym.text) ?: continue
            val end = symbols.getOrNull(i + 1)?.timeSec ?: song.durationSec
            val chordQ = song.beatsBetween(sym.timeSec, end).coerceAtLeast(beatQ) // quarti veri, anche con cambi di tempo
            val voicing = voice(info.tones, prevVoicing)
            prevVoicing = voicing
            emit(notes, info, voicing, song.beatsBetween(0.0, sym.timeSec), chordQ, spq, beatQ, false, arpeggio)
            used.add(sym)
        }
        // le note sono costruite a tempo costante [spq]: con cambi di tempo si portano sui secondi veri
        val real = if (song.tempoChanges.isEmpty()) notes else notes.map { n ->
            val s = song.secAfter(0.0, n.startSec / spq)
            n.copy(startSec = s, durationSec = (song.secAfter(0.0, (n.startSec + n.durationSec) / spq) - s).coerceAtLeast(0.02))
        }
        return song.copy(notes = real.sortedWith(compareBy({ it.startSec }, { it.key })), chordSymbols = used, chordChart = true)
    }

    /**
     * La posizione dell'accordo (note in ordine crescente fra Sol3 e Sol5) che si muove meno rispetto a [prev]:
     * somma degli spostamenti nota per nota, con un piccolo vantaggio per le note in comune. Una nona può arrivare a 14 semitoni.
     */
    private fun voice(tones: List<Int>, prev: List<Int>): List<Int> {
        var best: List<Int> = emptyList()
        var bestCost = Double.MAX_VALUE
        val maxSpan = if (tones.size >= 4) 14 else 12
        for (rot in tones.indices) {
            val order = tones.drop(rot) + tones.take(rot)
            for (base in 53..70) {
                if (base % 12 != order[0]) continue
                val stack = ArrayList<Int>()
                var last = base - 1
                for (p in order) { var k = last + 1; while (k % 12 != p) k++; stack.add(k); last = k }
                if (stack.last() > 79 || stack.last() - stack.first() > maxSpan) continue
                var cost = 0.0
                for (i in 0 until minOf(stack.size, prev.size)) cost += abs(stack[i] - prev[i])
                cost += abs(stack.size - prev.size) * 2.0
                cost -= 1.5 * stack.count { it in prev } // note in comune: la mano resta ferma
                cost += 6.0 * stack.zipWithNext().count { (a, b) -> b - a == 1 } // semitoni adiacenti: grappolo poco suonabile
                if (cost < bestCost) { bestCost = cost; best = stack }
            }
        }
        return best.ifEmpty { tones.map { 60 + it }.sorted() }
    }
}
