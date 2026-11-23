package com.gianlucacelante.midipiano.learn

import kotlin.math.abs
import kotlin.math.floor

/** Copertine generate: due colori stabili per ogni brano, calcolati dall'identificativo (nessuna immagine esterna). */
object Covers {
    fun colors(id: String): Pair<Long, Long> {
        val h = id.hashCode()
        val hue1 = abs(h % 360).toFloat()
        val hue2 = (hue1 + 40f + abs((h / 360) % 80)) % 360f
        return hsv(hue1, 0.55f, 0.85f) to hsv(hue2, 0.65f, 0.55f)
    }

    /** Quale foto (fra [count]) usare per un brano/lezione: stabile, sempre nell'intervallo; -1 se non ce ne sono. */
    fun photoIndex(id: String, count: Int): Int = if (count <= 0) -1 else Math.floorMod(id.hashCode() * 31 + 7, count)

    /**
     * Foto per un brano/corso: prima la parola chiave più lunga della [map] che compare in id+[hint] (compositore,
     * titolo...), così Beethoven ha il suo ritratto; altrimenti una foto generica scelta in modo stabile fra quelle
     * non legate a una parola chiave. Null se non ci sono foto.
     */
    private val COMPOSER_KEYS = setOf("beethoven", "bach", "bwv", "chopin", "debussy", "satie", "liszt", "schubert", "brahms", "schumann")

    fun pickPhoto(id: String, hint: String, files: List<String>, map: Map<String, String>, allowGeneric: Boolean = true): String? {
        if (files.isEmpty()) return null
        val text = "$id $hint".lowercase()
        // i brani usano solo i ritratti dei compositori; i corsi anche le foto di argomento (accordi, ritmo...)
        val usable = if (allowGeneric) map.entries else map.entries.filter { it.key in COMPOSER_KEYS }
        usable.sortedByDescending { it.key.length }.firstOrNull { text.contains(it.key) && it.value in files }?.let { return it.value }
        if (!allowGeneric) return null
        val mapped = map.values.toSet()
        val generic = files.filter { it !in mapped }.sorted().ifEmpty { files.sorted() }
        return generic[photoIndex(id, generic.size)]
    }

    fun initials(title: String): String =
        title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }

    /** HSV -> ARGB opaco, senza dipendere da android.graphics (si prova nei test). */
    private fun hsv(h: Float, s: Float, v: Float): Long {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when (floor(h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
