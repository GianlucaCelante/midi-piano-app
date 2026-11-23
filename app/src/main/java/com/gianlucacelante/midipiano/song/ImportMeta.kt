package com.gianlucacelante.midipiano.song

import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

/** Titolo, autore e (facoltativa) foto di un brano importato. */
class ImportMeta(val title: String, val composer: String?, val credit: String? = null, val cover: ByteArray? = null)

/**
 * Ricava un titolo e un autore ordinati da un file importato: prima i metadati del file (se non sono segnaposto
 * come «Untitled score»), altrimenti il nome del file ripulito. Logica pura.
 */
object ImportMetaGuess {
    private val PLACEHOLDERS = setOf("", "untitled", "untitled score", "titel", "title", "titolo", "free", "composer", "composer / arranger", "componist", "compositore", "arranger", "unknown")
    private val FILE_NOISE = setOf("free", "piano", "solo", "arr", "by", "mid", "mxl", "midi", "sheet", "music", "pdf")

    fun guess(displayName: String, bytes: ByteArray): ImportMeta {
        val base = displayName.substringBeforeLast('.')
        val (xmlTitle, xmlComposer) = xmlMetadata(displayName, bytes)
        val fileTitle = titleFromFile(base)
        val title = (xmlTitle?.takeIf { valid(it) }?.let(::tidyText) ?: fileTitle)
        val composer = xmlComposer?.takeIf { valid(it) && !it.contains("arr", true) }?.let(::tidyText)
        return ImportMeta(title, composer)
    }

    fun valid(s: String): Boolean = s.trim().lowercase() !in PLACEHOLDERS && s.trim().length >= 2

    /** Nome del file → titolo: parole in maiuscolo iniziale, senza «(1)» e simili. */
    fun titleFromFile(base: String): String {
        val words = Normalizer.normalize(base, Normalizer.Form.NFC).replace('_', ' ').replace('-', ' ').split(Regex("\\s+"))
            .filter { it.isNotBlank() && !it.matches(Regex("\\(?\\d+\\)?")) && it.lowercase() !in FILE_NOISE }
        return words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { "Brano importato" }
    }

    private fun tidyText(s: String): String = s.replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    /** (titolo, autore) letti dal MusicXML (anche compresso .mxl); null per MIDI o se mancano. */
    fun xmlMetadata(displayName: String, bytes: ByteArray): Pair<String?, String?> {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val xml = when (ext) {
            "mxl" -> runCatching {
                ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                    var e = z.nextEntry
                    var out: String? = null
                    while (e != null) {
                        if (!e.isDirectory && e.name.endsWith(".xml", true) && !e.name.startsWith("META-INF", true)) { out = z.readBytes().toString(Charsets.UTF_8); break }
                        e = z.nextEntry
                    }
                    out
                }
            }.getOrNull()
            "xml", "musicxml" -> runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            else -> null
        } ?: return null to null
        val title = Regex("<work-title[^>]*>([^<]*)</work-title>").find(xml)?.groupValues?.get(1)
        val composer = Regex("<creator[^>]*type=\"composer\"[^>]*>([^<]*)</creator>").find(xml)?.groupValues?.get(1)
        return title to composer
    }
}
