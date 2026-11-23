package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.json.JSONObject

/**
 * Legge gli esercizi dei corsi, scritti come JSON compatto: posizioni e durate in quarti, tempo in
 * bpm, mano "R" o "L". Restituisce un [Song] come quelli letti da MIDI o MusicXML.
 */
object InlineSongParser {
    fun parse(o: JSONObject, id: String): Song {
        val bpm = o.optDouble("bpm", 0.0)
        require(bpm > 0.0) { "bpm mancante o non valido" }
        val ts = o.optJSONArray("timeSignature")
        val num = ts?.optInt(0, 4) ?: 4
        val den = ts?.optInt(1, 4) ?: 4
        require(num > 0 && den > 0) { "tempo musicale non valido" }
        val secPerBeat = 60.0 / bpm

        val arr = o.optJSONArray("notes") ?: throw IllegalArgumentException("note mancanti")
        require(arr.length() > 0) { "l'esercizio non contiene note" }
        val notes = (0 until arr.length()).map { i ->
            val n = arr.getJSONObject(i)
            val beat = n.getDouble("beat")
            val dur = n.getDouble("dur")
            val key = n.getInt("key")
            require(beat >= 0.0) { "posizione negativa" }
            require(dur > 0.0) { "durata non valida" }
            require(key in 21..108) { "nota fuori dal pianoforte: $key" }
            val hand = when (n.getString("hand")) {
                "R" -> Hand.RIGHT
                "L" -> Hand.LEFT
                else -> throw IllegalArgumentException("mano non valida: ${n.getString("hand")}")
            }
            val vel = n.optInt("vel", 80).coerceIn(1, 127) // forza scritta (p, f...), facoltativa
            SongNote(beat * secPerBeat, dur * secPerBeat, key, vel, hand, beats = dur)
        }.sortedWith(compareBy({ it.startSec }, { it.key }))

        val barBeats = num * 4.0 / den
        val lastBeat = notes.maxOf { it.startSec / secPerBeat + it.beats }
        val barLines = generateSequence(1) { it + 1 }
            .map { it * barBeats }
            .takeWhile { it < lastBeat - 1e-9 }
            .map { it * secPerBeat }
            .toList()

        return Song(
            id = id, title = o.optString("title", id), composer = null,
            source = SongSource("Corso originale", "Originale"),
            notes = notes, barLines = barLines,
            keyFifths = o.optInt("keyFifths", 0), timeNumerator = num, timeDenominator = den, secPerBeat = secPerBeat,
        )
    }
}
