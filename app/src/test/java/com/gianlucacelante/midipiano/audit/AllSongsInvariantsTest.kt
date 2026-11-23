package com.gianlucacelante.midipiano.audit

import com.gianlucacelante.midipiano.learn.Simplifier
import com.gianlucacelante.midipiano.notation.Chords
import com.gianlucacelante.midipiano.notation.NotationLayout
import com.gianlucacelante.midipiano.notation.Rests
import com.gianlucacelante.midipiano.song.ChordSongParser
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.MidiFileParser
import com.gianlucacelante.midipiano.song.MusicXmlParser
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Controlli di base su TUTTI i brani inclusi (MIDI, MusicXML, accordi): note, durate, battute, pause, posizioni. */
class AllSongsInvariantsTest {
    private val src = SongSource("t", "t")
    private fun songs(): List<Pair<String, Song>> = File("src/main/assets/songs").listFiles().orEmpty().filter { it.isFile }.mapNotNull { f ->
        val id = f.nameWithoutExtension
        when (f.extension) {
            "mid" -> f.name to MidiFileParser.parse(f.readBytes(), id, id, null, src)
            "mxl", "musicxml", "xml" -> f.name to MusicXmlParser.parse(f.readBytes(), id, id, null, src)
            "chords" -> f.name to ChordSongParser.parse(f.readBytes(), id, src)
            else -> null
        }
    }

    private val problems = ArrayList<String>()
    private fun bad(name: String, msg: String) { if (problems.count { it.startsWith(name) } < 6) problems.add("$name: $msg") }

    @Test
    fun everySongPassesTheBasicInvariants() {
        val all = songs()
        assertTrue(all.size >= 30)
        for ((name, s) in all) {
            val notes = s.notes
            if (notes.isEmpty()) { bad(name, "senza note"); continue }
            for (n in notes) {
                if (n.key !in 21..108) bad(name, "tasto fuori tastiera ${n.key}")
                if (n.durationSec <= 0.0) bad(name, "durata non positiva a ${n.startSec}")
                if (n.startSec < -1e-9) bad(name, "inizio negativo")
                if (n.hand == Hand.BOTH) bad(name, "nota con mano BOTH")
            }
            if (notes.zipWithNext().any { (a, b) -> b.startSec < a.startSec - 1e-9 }) bad(name, "note non ordinate")
            // battute: crescenti, dentro il brano, con una lunghezza ragionevole
            val bl = s.barLines
            if (bl.zipWithNext().any { (a, b) -> b <= a + 1e-9 }) bad(name, "stanghette non crescenti")
            if (bl.any { it <= 0.0 || it > s.durationSec + 1.0 }) bad(name, "stanghetta fuori dal brano")
            if (s.secPerBeat !in 0.1..3.0) bad(name, "tempo strano secPerBeat=${s.secPerBeat}")
            if (s.timeNumerator < 1 || s.timeDenominator !in listOf(1, 2, 4, 8, 16)) bad(name, "metro ${s.timeNumerator}/${s.timeDenominator}")
            if (kotlin.math.abs(s.keyFifths) > 7) bad(name, "armatura ${s.keyFifths}")
            val firstBar = bl.firstOrNull() ?: s.durationSec
            if (bl.size >= 2) {
                // la lunghezza attesa segue il metro in vigore: con cambi di metro le battute non sono tutte uguali
                val pairs = bl.zipWithNext { a, b -> (b - a) to (s.timeSignatureAt(a).let { (n, d) -> n * (4.0 / d) } / (s.timeNumerator * (4.0 / s.timeDenominator))) }
                val widths = pairs.map { it.first / it.second }
                val typical = widths.sorted()[widths.size / 2]
                val odd = widths.count { kotlin.math.abs(it - typical) > typical * 0.34 }
                if (odd > widths.size * 0.10 + 1) bad(name, "battute di lunghezza irregolare: $odd su ${widths.size} (tipica ${"%.2f".format(typical)} s)")
                if (firstBar > typical * 1.34) bad(name, "prima battuta più lunga delle altre")
            }
            // nessuna nota uguale sovrapposta nella stessa mano
            for ((hand, list) in notes.groupBy { it.hand }) for ((key, same) in list.groupBy { it.key }) {
                val sorted = same.sortedBy { it.startSec }
                for (i in 0 until sorted.size - 1) if (sorted[i].startSec + sorted[i].durationSec > sorted[i + 1].startSec + 0.02 && sorted[i + 1].startSec - sorted[i].startSec > 0.001)
                    bad(name, "stesso tasto $key sovrapposto ($hand) a ${"%.2f".format(sorted[i].startSec)}")
            }
            // notazione: ogni nota ha una posizione e una durata scritta sensata, e le pause non coprono le note
            val staff = NotationLayout.layout(s, Hand.BOTH)
            for (n in staff) {
                if (n.beats <= 0.0 || n.beats > 4.0 + 1e-9) bad(name, "durata scritta ${n.beats} a ${"%.2f".format(n.startSec)}")
                if (n.position < -12 || n.position > 22) bad(name, "posizione sul rigo ${n.position} per il tasto ${n.key}")
            }
            val chords = Chords.group(staff)
            for ((clef, cs) in chords.groupBy { it.clef }) {
                val rests = Rests.compute(cs.sortedBy { it.startSec }, s)
                for (r in rests) {
                    val end = r.startSec + r.beats * s.secPerBeat
                    if (cs.any { c -> c.startSec > r.startSec + 0.05 && c.startSec < end - 0.05 }) bad(name, "pausa $clef a ${"%.2f".format(r.startSec)} copre una nota")
                    if (r.beats <= 0) bad(name, "pausa di durata nulla")
                }
            }
            // versioni semplificate: mai vuote, tasti nel brano originale, mai note inventate
            if (!s.chordChart) for (lvl in listOf(Simplifier.EASY, Simplifier.MEDIUM)) {
                val v = Simplifier.version(s, lvl)
                if (v.notes.isEmpty()) bad(name, "versione $lvl vuota")
                val keys = s.notes.map { it.key }.toSet()
                if (v.notes.any { it.key !in keys }) bad(name, "versione $lvl con note inventate")
            }
        }
        if (problems.isNotEmpty()) println("PROBLEMI:\n" + problems.joinToString("\n"))
        assertTrue("Problemi trovati:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
