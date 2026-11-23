package com.gianlucacelante.midipiano.song

import org.json.JSONArray
import org.json.JSONObject

enum class SongFormat { MIDI, MUSICXML, CHORDS }

/** Voce dell'indice dei brani inclusi (assets/songs/index.json). Licenza e origine sono obbligatorie. */
data class SongEntry(
    val id: String,
    val title: String,
    val composer: String?,
    val file: String,
    val origin: String,
    val license: String,
    val attributionRequired: Boolean,
    val url: String?,
) {
    val format: SongFormat
        get() = when (file.substringAfterLast('.').lowercase()) {
            "mid", "midi" -> SongFormat.MIDI
            "mxl", "xml", "musicxml" -> SongFormat.MUSICXML
            "chords", "txt", "acc" -> SongFormat.CHORDS
            else -> throw IllegalArgumentException("Formato non supportato: $file")
        }

    fun source() = SongSource(origin, license, url)
}

object SongIndex {
    fun parse(json: String): List<SongEntry> {
        val arr = JSONArray(json)
        val entries = (0 until arr.length()).map { toEntry(arr.getJSONObject(it)) }
        val duplicate = entries.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        require(duplicate == null) { "Id duplicato nell'indice brani: $duplicate" }
        return entries
    }

    private fun toEntry(o: JSONObject): SongEntry {
        fun required(name: String): String =
            o.optString(name, "").takeIf { it.isNotBlank() && !o.isNull(name) }
                ?: throw IllegalArgumentException("Campo obbligatorio mancante nell'indice brani: $name")

        fun optional(name: String): String? = if (o.isNull(name)) null else o.optString(name).takeIf { it.isNotBlank() }

        val entry = SongEntry(
            id = required("id"),
            title = required("title"),
            composer = optional("composer"),
            file = required("file"),
            origin = required("origin"),
            license = required("license"),
            attributionRequired = o.optBoolean("attribution_required", false),
            url = optional("url"),
        )
        entry.format // valida l'estensione subito
        return entry
    }
}
