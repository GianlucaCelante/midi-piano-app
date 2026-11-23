package com.gianlucacelante.midipiano.song

/** Trasporta un brano di qualche semitono: note, armatura (anche i cambi) e sigle degli accordi. */
object Transposer {
    private const val LOWEST = 21
    private const val HIGHEST = 108
    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val LETTER_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    fun transpose(song: Song, semitones: Int): Song {
        if (semitones == 0) return song
        val fifths = newFifths(song.keyFifths, semitones)
        val flats = fifths < 0
        return song.copy(
            notes = song.notes.mapNotNull { n ->
                val k = n.key + semitones
                if (k !in LOWEST..HIGHEST) null else n.copy(key = k, diatonic = null, alter = 0)
            },
            keyFifths = fifths,
            keyChanges = song.keyChanges.map { KeyChange(it.timeSec, newFifths(it.fifths, semitones)) },
            chordSymbols = song.chordSymbols.map { ChordSymbol(it.timeSec, symbol(it.text, semitones, flats)) },
        )
    }

    /** Alterazioni della tonalità spostata: il Do maggiore a +2 è Re maggiore (2 diesis); si preferiscono al massimo 6 alterazioni. */
    fun newFifths(fifths: Int, semitones: Int): Int {
        val tonic = Math.floorMod(fifths * 7, 12)
        val moved = Math.floorMod(tonic + semitones, 12)
        val f = Math.floorMod(moved * 7, 12) // 0..11: 0-6 diesis, 7-11 = -5..-1 bemolli
        return if (f > 6) f - 12 else f
    }

    private fun symbol(text: String, n: Int, flats: Boolean): String {
        if (text.isBlank() || text.startsWith("N")) return text
        val slash = text.indexOf('/')
        val main = if (slash >= 0) text.substring(0, slash) else text
        val bass = if (slash >= 0) text.substring(slash + 1) else null
        return moveRoot(main, n, flats) + (bass?.let { "/" + moveRoot(it, n, flats) } ?: "")
    }

    private fun moveRoot(s: String, n: Int, flats: Boolean): String {
        val letter = s.firstOrNull()?.uppercaseChar() ?: return s
        val base = LETTER_PC[letter] ?: return s
        var i = 1
        var pc = base
        while (i < s.length && s[i] in "#b♯♭") { pc += if (s[i] == '#' || s[i] == '♯') 1 else -1; i++ }
        val moved = Math.floorMod(pc + n, 12)
        return (if (flats) FLAT_NAMES[moved] else SHARP_NAMES[moved]) + s.substring(i)
    }
}
