package com.gianlucacelante.midipiano.song

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class MusicXmlException(message: String) : Exception(message)

/**
 * Converte MusicXML "partwise" (.xml/.musicxml o .mxl compresso) in un [Song].
 *
 * Supportato: durate, accordi, alterazioni, legature, pause, backup/forward, più righi (staff 1 =
 * mano destra, staff 2 = sinistra), cambi di tempo via `<sound tempo>`. Se esiste una parte
 * pianistica (2 righi) le altre parti (es. voce) sono ignorate. Non supportato: ritornelli (il brano
 * si suona una volta sola), abbellimenti, dinamiche.
 */
object MusicXmlParser {
    private const val DEFAULT_BPM = 120.0
    private const val DEFAULT_VELOCITY = 80
    private const val LOWEST_KEY = 21 // La0
    private const val HIGHEST_KEY = 108 // Do8

    /** Tetto di byte letti per ogni entry dell'archivio .mxl. */
    const val MAX_ENTRY_BYTES = 20 * 1024 * 1024

    private val SIMPLE_DOCTYPE = Regex("<!DOCTYPE[^>\\[]*>", RegexOption.IGNORE_CASE)
    private val ENCODING_DECL = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']")

    /** Decodifica rispettando BOM e dichiarazione `encoding` (UTF-8 se assente o sconosciuta). */
    private fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        val head = String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1)
        val charset = ENCODING_DECL.find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(b, charset)
    }

    private class RawNote(val startQ: Double, var durQ: Double, val key: Int, val staff: Int, val diatonic: Int, val alter: Int, val lyric: String? = null)
    private class PartResult(
        val notes: List<RawNote>,
        val staves: Int,
        val tempos: List<Pair<Double, Double>>,
        val measureStartsQ: List<Double>, // inizio di ogni battuta, in quarti
        val fifths: Int,
        val timeNumerator: Int,
        val timeDenominator: Int,
        val harmonies: List<Pair<Double, String>>, // (posizione in quarti, sigla)
        val timeChangesQ: List<Triple<Double, Int, Int>> = emptyList(), // (posizione in quarti, numeratore, denominatore) dopo il primo
        val keyChangesQ: List<Pair<Double, Int>> = emptyList(), // (posizione in quarti, alterazioni) dopo la prima
    )

    fun parse(bytes: ByteArray, id: String, title: String?, composer: String?, source: SongSource): Song {
        val xml = if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) unzip(bytes) else bytes
        val text = decode(xml).replace(SIMPLE_DOCTYPE, "")
        // Un DOCTYPE con sottoinsieme interno (entità) non serve a MusicXML ed è la via classica per XXE
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) {
            throw MusicXmlException("DOCTYPE/ENTITY non ammessi nel MusicXML")
        }
        val doc = try {
            // Si passa il testo già decodificato: il parser non carica DTD esterni (DOCTYPE rimosso sopra)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(text)))
        } catch (e: Exception) {
            throw MusicXmlException("MusicXML non leggibile: ${e.message}")
        }
        val root = doc.documentElement
        if (root.tagName != "score-partwise") throw MusicXmlException("Formato non supportato (serve score-partwise)")

        val results = root.children("part").map(::readPart)
        val piano = results.filter { it.staves >= 2 }
        val used = piano.ifEmpty { results }
        if (used.sumOf { it.notes.size } == 0) throw MusicXmlException("Lo spartito non contiene note")

        val tempoMap = TempoMap(used.flatMap { it.tempos }.sortedBy { it.first })
        val songNotes = ArrayList<SongNote>()
        for (p in used) {
            for (n in p.notes) {
                val hand = when {
                    p.staves >= 2 -> if (n.staff == 1) Hand.RIGHT else Hand.LEFT
                    n.key >= 60 -> Hand.RIGHT // parte a un solo rigo: si divide al Do centrale
                    else -> Hand.LEFT
                }
                val start = tempoMap.toSeconds(n.startQ)
                songNotes.add(
                    SongNote(
                        startSec = start,
                        durationSec = tempoMap.toSeconds(n.startQ + n.durQ) - start,
                        key = n.key, velocity = DEFAULT_VELOCITY, hand = hand,
                        beats = n.durQ, diatonic = n.diatonic, alter = n.alter, lyric = n.lyric,
                    ),
                )
            }
        }
        songNotes.sortWith(compareBy({ it.startSec }, { it.key }))

        // Informazioni globali e battute dalla prima parte usata (le parti pianistiche le condividono)
        val first = used.first()
        val barLines = first.measureStartsQ.drop(1).map { tempoMap.toSeconds(it) }

        val workTitle = root.firstChild("work")?.firstChild("work-title")?.text()
        val creator = root.firstChild("identification")?.children("creator")?.firstOrNull { it.getAttribute("type") == "composer" }?.text()
        return Song(
            id, title ?: workTitle ?: id, composer ?: creator, source, SongCleanup.tidy(songNotes),
            barLines, first.fifths, first.timeNumerator, first.timeDenominator,
            secPerBeat = tempoMap.toSeconds(1.0), // durata del primo quarto
            tempoChanges = tempoMap.changes().let { if (it.size > 1) it else emptyList() },
            chordSymbols = first.harmonies.map { (q, text) -> ChordSymbol(tempoMap.toSeconds(q), text) },
            timeChanges = first.timeChangesQ.map { (q, n, d) -> TimeChange(tempoMap.toSeconds(q), n, d) },
            keyChanges = first.keyChangesQ.map { (q, f) -> KeyChange(tempoMap.toSeconds(q), f) },
        )
    }

    private fun readPart(part: Element): PartResult {
        val notes = ArrayList<RawNote>()
        val tempos = ArrayList<Pair<Double, Double>>()
        val openTies = HashMap<Int, RawNote>() // chiave*10+staff -> nota con legatura aperta
        var divisions = 1.0
        var staves = 1
        var measureStart = 0.0
        val measureStarts = ArrayList<Double>()
        val harmonies = ArrayList<Pair<Double, String>>()
        var fifths = 0
        var fifthsSet = false
        var timeNum = 4
        var timeDen = 4
        var timeSet = false
        val timeChanges = ArrayList<Triple<Double, Int, Int>>()
        val keyChanges = ArrayList<Pair<Double, Int>>()

        for (measure in part.children("measure")) {
            measureStarts.add(measureStart)
            var cursor = 0.0
            var maxCursor = 0.0
            var lastStart = 0.0
            for (el in measure.elements()) {
                when (el.tagName) {
                    "attributes" -> {
                        el.firstChild("divisions")?.text()?.toDoubleOrNull()?.takeIf { it > 0 }?.let { divisions = it }
                        el.firstChild("staves")?.text()?.toIntOrNull()?.let { staves = maxOf(staves, it) }
                        el.firstChild("key")?.firstChild("fifths")?.text()?.toIntOrNull()?.takeIf { it in -7..7 }?.let {
                            if (!fifthsSet) { fifths = it; fifthsSet = true }
                            else if (it != (keyChanges.lastOrNull()?.second ?: fifths)) keyChanges.add((measureStart + cursor) to it)
                        }
                        run {
                            val t = el.firstChild("time")
                            val b = t?.firstChild("beats")?.text()?.toIntOrNull()
                            val bt = t?.firstChild("beat-type")?.text()?.toIntOrNull()
                            if (b != null && bt != null && b > 0 && bt > 0) {
                                if (!timeSet) { timeNum = b; timeDen = bt; timeSet = true }
                                else {
                                    val prev = timeChanges.lastOrNull()
                                    if ((prev?.second ?: timeNum) != b || (prev?.third ?: timeDen) != bt) timeChanges.add(Triple(measureStart + cursor, b, bt))
                                }
                            }
                        }
                    }
                    "direction", "sound" -> {
                        val sound = if (el.tagName == "sound") el else el.firstChild("sound")
                        sound?.getAttribute("tempo")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { tempos.add((measureStart + cursor) to it) }
                    }
                    "harmony" -> harmonyText(el)?.let { harmonies.add((measureStart + cursor) to it) }
                    "backup" -> cursor -= (el.firstChild("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions
                    "forward" -> cursor += (el.firstChild("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions
                    "note" -> {
                        if (el.firstChild("grace") != null) continue
                        val dur = (el.firstChild("duration")?.text()?.toDoubleOrNull() ?: 0.0) / divisions
                        val isChord = el.firstChild("chord") != null
                        val start = if (isChord) lastStart else cursor
                        val pitch = el.firstChild("pitch")
                        if (pitch != null && el.firstChild("rest") == null && el.firstChild("cue") == null) { // le note di richiamo (cue) non si suonano
                            val key = midiKey(pitch)
                            if (key in LOWEST_KEY..HIGHEST_KEY) { // fuori dalla tastiera di un pianoforte: scartata
                                val staff = el.firstChild("staff")?.text()?.toIntOrNull() ?: 1
                                val tieKey = key * 10 + staff
                                val ties = el.children("tie").map { it.getAttribute("type") }
                                val continued = if ("stop" in ties) openTies[tieKey] else null
                                if (continued != null) {
                                    continued.durQ += dur
                                    if ("start" !in ties) openTies.remove(tieKey)
                                } else {
                                    val n = RawNote(measureStart + start, dur, key, staff, diatonic(pitch), alterOf(pitch), lyricOf(el))
                                    notes.add(n)
                                    if ("start" in ties) openTies[tieKey] = n
                                }
                            }
                        }
                        lastStart = start
                        if (!isChord) cursor += dur
                    }
                }
                maxCursor = maxOf(maxCursor, cursor)
            }
            measureStart += maxCursor
        }
        return PartResult(notes, staves, tempos, measureStarts, fifths, timeNum, timeDen, harmonies, timeChanges, keyChanges)
    }

    /** Sillaba della prima strofa, con "-" se la parola continua nella nota seguente. */
    private fun lyricOf(note: Element): String? {
        val lyric = note.children("lyric").firstOrNull() ?: return null
        val text = lyric.firstChild("text")?.text()?.takeIf { it.isNotEmpty() } ?: return null
        val syllabic = lyric.firstChild("syllabic")?.text()
        return if (syllabic == "begin" || syllabic == "middle") "$text-" else text
    }

    private val KIND_SUFFIX = mapOf(
        "major" to "", "minor" to "m", "dominant" to "7", "major-seventh" to "maj7", "minor-seventh" to "m7",
        "diminished" to "dim", "augmented" to "aug", "diminished-seventh" to "dim7", "half-diminished" to "m7b5",
        "major-sixth" to "6", "minor-sixth" to "m6", "suspended-fourth" to "sus4", "suspended-second" to "sus2",
        "dominant-ninth" to "9", "major-minor" to "mmaj7", "power" to "5",
        "major-ninth" to "maj9", "minor-ninth" to "m9", "dominant-11th" to "11", "dominant-13th" to "13", "minor-11th" to "m11",
        "major-11th" to "maj9", "major-13th" to "maj13", "minor-13th" to "m13", "suspended" to "sus4", "none" to "N.C.",
    )

    private fun accidentalText(alter: Int) = when {
        alter > 0 -> "♯".repeat(alter)
        alter < 0 -> "♭".repeat(-alter)
        else -> ""
    }

    /** Sigla d'accordo da un elemento `<harmony>` (es. "B♭/F"); null se non è leggibile. */
    private fun harmonyText(h: Element): String? {
        val root = h.firstChild("root") ?: return null
        val step = root.firstChild("root-step")?.text()?.takeIf { it.isNotEmpty() } ?: return null
        val alter = root.firstChild("root-alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
        val kindEl = h.firstChild("kind")
        val kindText = kindEl?.getAttribute("text")?.takeIf { it.isNotEmpty() }
        val kindName = kindEl?.text()
        // kind sconosciuto e senza testo: meglio nessuna sigla che un accordo sbagliato (prima diventava una triade maggiore)
        val suffix = kindText ?: if (kindEl == null) "" else KIND_SUFFIX[kindName] ?: return null
        if (suffix == "N.C.") return "N.C."
        val bass = h.firstChild("bass")?.let { b ->
            b.firstChild("bass-step")?.text()?.takeIf { it.isNotEmpty() }?.let { bs ->
                "/" + bs + accidentalText(b.firstChild("bass-alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0)
            }
        } ?: ""
        return step + accidentalText(alter) + suffix + bass
    }

    private fun alterOf(pitch: Element): Int = pitch.firstChild("alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0

    /** Indice diatonico scritto: ottava*7 + passo (Do=0 ... Si=6). */
    private fun diatonic(pitch: Element): Int {
        val step = pitch.firstChild("step")?.text() ?: throw MusicXmlException("Nota senza altezza")
        val octave = pitch.firstChild("octave")?.text()?.toIntOrNull() ?: throw MusicXmlException("Nota senza ottava")
        val index = "CDEFGAB".indexOf(step).takeIf { it >= 0 } ?: throw MusicXmlException("Altezza non valida: $step")
        return octave * 7 + index
    }

    private fun midiKey(pitch: Element): Int {
        val step = pitch.firstChild("step")?.text() ?: throw MusicXmlException("Nota senza altezza")
        val octave = pitch.firstChild("octave")?.text()?.toIntOrNull() ?: throw MusicXmlException("Nota senza ottava")
        val alter = pitch.firstChild("alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
        val semitone = when (step) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5; "G" -> 7; "A" -> 9; "B" -> 11
            else -> throw MusicXmlException("Altezza non valida: $step")
        }
        return 12 * (octave + 1) + semitone + alter
    }

    /** Legge al massimo [MAX_ENTRY_BYTES] da un'entry: protegge dalle "zip bomb" (pochi KB che espandono in GB). */
    private fun readCapped(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw MusicXmlException("Archivio .mxl troppo grande")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): ByteArray {
        val entries = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var total = 0
                generateSequence { zip.nextEntry }.forEach { e ->
                    val wanted = !e.isDirectory &&
                        (e.name == "META-INF/container.xml" || (!e.name.startsWith("META-INF") && (e.name.endsWith(".xml") || e.name.endsWith(".musicxml"))))
                    if (wanted) { // le altre entry (immagini, ecc.) non si leggono nemmeno
                        val data = readCapped(zip)
                        total += data.size
                        if (total > MAX_ENTRY_BYTES * 2) throw MusicXmlException("Archivio .mxl troppo grande")
                        entries[e.name] = data
                    }
                }
            }
        } catch (e: MusicXmlException) {
            throw e
        } catch (e: Exception) { // ZipException, EOF, ecc.
            throw MusicXmlException("Archivio .mxl non valido: ${e.message}")
        }
        val container = entries["META-INF/container.xml"]?.let { String(it, Charsets.UTF_8) }
        val rootPath = container?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        val chosen = rootPath?.let { entries[it] }
            ?: entries.entries.firstOrNull { !it.key.startsWith("META-INF") && (it.key.endsWith(".xml") || it.key.endsWith(".musicxml")) }?.value
        return chosen ?: throw MusicXmlException("Archivio .mxl senza spartito")
    }

    /** Tempo in quarti al minuto, con cambi in posizione (in quarti); restituisce secondi. */
    private class TempoMap(changes: List<Pair<Double, Double>>) {
        private val pos = ArrayList<Double>()
        private val secs = ArrayList<Double>()
        private val bpm = ArrayList<Double>()

        init {
            val list = if (changes.isEmpty() || changes.first().first > 0.0) listOf(0.0 to DEFAULT_BPM) + changes else changes
            var t = 0.0
            var prevQ = 0.0
            var prevBpm = list.first().second
            for ((q, b) in list) {
                t += (q - prevQ) * 60.0 / prevBpm
                pos.add(q); secs.add(t); bpm.add(b)
                prevQ = q; prevBpm = b
            }
        }

        fun changes(): List<TempoChange> = pos.indices.map { TempoChange(secs[it], 60.0 / bpm[it]) }

        fun toSeconds(q: Double): Double {
            val i = pos.indexOfLast { it <= q }.coerceAtLeast(0)
            return secs[i] + (q - pos[i]) * 60.0 / bpm[i]
        }
    }

    private fun Element.elements(): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element) out.add(n)
            n = n.nextSibling
        }
        return out
    }

    private fun Element.children(name: String) = elements().filter { it.tagName == name }
    private fun Element.firstChild(name: String) = elements().firstOrNull { it.tagName == name }
    private fun Element.text() = textContent.trim()
}
