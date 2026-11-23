package com.gianlucacelante.midipiano.song

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Brani inclusi nell'app (assets/songs) e brani importati dall'utente (cartella privata dell'app).
 * Lettura bloccante: chiamare fuori dal thread principale.
 */
class SongRepository(private val context: Context) {
    private val userDir get() = File(context.filesDir, "user_songs")

    fun entries(): List<SongEntry> {
        val bundled = SongIndex.parse(context.assets.open("$DIR/index.json").bufferedReader().use { it.readText() })
        return bundled + userEntries()
    }

    fun userEntries(): List<SongEntry> {
        val f = File(userDir, "index.json")
        if (!f.exists()) return emptyList()
        return runCatching { SongIndex.parse(f.readText()) }.getOrDefault(emptyList())
    }

    /** Come si vuole suonare un brano che ha gli accordi: gli accordi interi oppure le note singole. */
    enum class Style { CHORDS, NOTES }

    /** Il brano, nello stile scelto: per le griglie di accordi NOTES = arpeggi; per gli altri brani CHORDS = solo le sigle degli accordi. */
    fun load(entry: SongEntry, style: Style? = null): Song {
        val song = loadRaw(entry, style == Style.NOTES)
        return if (style == Style.CHORDS && !song.chordChart && song.chordSymbols.isNotEmpty()) ChordSongParser.fromSymbols(song) else song
    }

    private fun loadRaw(entry: SongEntry, arpeggio: Boolean = false): Song {
        val bytes = if (entry.file.startsWith(USER_PREFIX)) File(userDir, entry.file.removePrefix(USER_PREFIX)).readBytes()
        else context.assets.open("$DIR/${entry.file}").use { it.readBytes() }
        return parse(entry, bytes, arpeggio)
    }

    /**
     * Importa un file MIDI o MusicXML scelto dall'utente: lo legge, lo prova con i parser dell'app e solo
     * se si apre lo copia fra i brani personali. Il file resta sul dispositivo (licenza "personale").
     */
    fun importSong(uri: Uri, displayName: String, meta: ImportMeta? = null): SongEntry {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        require(ext in IMPORT_EXTENSIONS) { "Formato non supportato: usa un file .mid, .musicxml, .xml, .mxl oppure un file di accordi (.txt)" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes(MAX_IMPORT_BYTES) }
            ?: throw IllegalArgumentException("Impossibile leggere il file")
        val tidy = meta ?: ImportMetaGuess.guess(displayName, bytes)
        val base = displayName.substringBeforeLast('.').ifBlank { "brano" }
        val slug = slugify(base)
        val existing = userEntries()
        var id = "user-$slug"
        var n = 2
        while (existing.any { it.id == id }) id = "user-$slug-${n++}"
        val file = "$id.$ext"
        val entry = SongEntry(id, tidy.title, tidy.composer, "$USER_PREFIX$file",
            "File importato" + (tidy.credit?.let { " · Foto: $it" } ?: ""), "Personale", false, null)
        parse(entry, bytes) // se non si apre, lancia e non si salva nulla
        userDir.mkdirs()
        File(userDir, file).writeBytes(bytes)
        tidy.cover?.let { c -> File(context.filesDir, "user_covers").apply { mkdirs() }.let { File(it, "$id.jpg").writeBytes(c) } }
        val arr = JSONArray()
        (existing + entry).forEach { e -> arr.put(toJson(e)) }
        File(userDir, "index.json").writeText(arr.toString(2))
        return entry
    }

    /** La foto di copertina di un brano importato, se c'è. */
    fun userCover(id: String): File? = File(File(context.filesDir, "user_covers"), "$id.jpg").takeIf { it.exists() }

    /** Cancella tutti i brani importati (e le loro foto). */
    fun clearUserSongs() {
        userDir.deleteRecursively()
        File(context.filesDir, "user_covers").deleteRecursively()
    }

    private fun parse(entry: SongEntry, bytes: ByteArray, arpeggio: Boolean = false): Song = when (entry.format) {
        SongFormat.MIDI -> MidiFileParser.parse(bytes, entry.id, entry.title, entry.composer, entry.source())
        SongFormat.MUSICXML -> MusicXmlParser.parse(bytes, entry.id, entry.title, entry.composer, entry.source())
        SongFormat.CHORDS -> ChordSongParser.parse(bytes, entry.id, entry.source(), entry.title, arpeggio).let { it.copy(title = entry.title, composer = entry.composer ?: it.composer) }
    }

    private fun toJson(e: SongEntry) = JSONObject()
        .put("id", e.id).put("title", e.title).put("composer", e.composer ?: JSONObject.NULL).put("file", e.file)
        .put("origin", e.origin).put("license", e.license).put("attribution_required", e.attributionRequired)
        .put("url", e.url ?: JSONObject.NULL)

    private fun java.io.InputStream.readBytes(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val r = read(buf)
            if (r < 0) break
            total += r
            require(total <= max) { "File troppo grande (massimo ${max / 1_000_000} MB)" }
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    companion object {
        const val USER_PREFIX = "user:"
        val IMPORT_EXTENSIONS = setOf("mid", "midi", "musicxml", "xml", "mxl", "chords", "txt", "acc")
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
        private const val DIR = "songs"

        /** Identificativo sicuro ricavato dal nome del file: minuscolo, lettere e cifre, trattini. */
        fun slugify(name: String): String =
            java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "brano" }
    }
}
