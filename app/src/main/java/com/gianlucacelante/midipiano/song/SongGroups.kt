package com.gianlucacelante.midipiano.song

import java.text.Normalizer

/** Un arrangiamento di un brano: la voce vera, l'etichetta con la difficoltà letta dal titolo e il suo ordine (0 = il più facile). */
data class Variant(val entry: SongEntry, val label: String, val rank: Int)

/** Più arrangiamenti dello stesso brano, mostrati come un brano solo. [rep] è la voce che sta negli elenchi (titolo ripulito, id del primo arrangiamento). */
data class SongGroup(val rep: SongEntry, val variants: List<Variant>, val memberIds: List<String> = variants.map { it.entry.id })

/**
 * Raggruppa i brani importati che sono lo stesso brano in arrangiamenti diversi («Ordinary», «Ordinary easy piano»,
 * «Ordinary intermediate»…): la difficoltà si legge dal titolo. I brani inclusi nell'app non si toccano. Logica pura.
 */
object SongGroups {
    private val RANKS = mapOf("beginner" to 0, "easy" to 1, "simple" to 1, "intermediate" to 2, "advanced" to 4, "hard" to 4, "hardstyle" to 4, "difficult" to 4)
    private val RANK_NAMES = mapOf(0 to "Principiante", 1 to "Facile", 2 to "Intermedio", 3 to "Originale", 4 to "Avanzato")
    private val NOISE = setOf("piano", "solo", "arr", "arrangement", "by", "from", "the", "version", "sheet", "music", "and", "duet", "pdf", "for", "mid", "mxl", "midi")

    private class Parsed(val entry: SongEntry, val words: List<String>) {
        val rank: Int? = words.firstNotNullOfOrNull { RANKS[it] }
        val core: List<String> = words.filter { it !in RANKS && it !in NOISE && !it.all(Char::isDigit) }
        val coreSet = core.toSet()
        /** Parole dell'autore (senza «and», «&»…): per non fondere due brani con lo stesso titolo ma autori diversi. */
        val artist: Set<String> = entry.composer?.let { words(it).filter { w -> w != "and" && w != "x" }.toSet() }.orEmpty()
    }

    private fun words(title: String): List<String> =
        Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun same(a: Parsed, b: Parsed): Boolean {
        if (a.coreSet.isEmpty() || b.coreSet.isEmpty()) return false
        if (a.artist.isNotEmpty() && b.artist.isNotEmpty() && a.artist.intersect(b.artist).isEmpty()) return false
        if (a.coreSet == b.coreSet) return true
        val (small, big) = if (a.coreSet.size <= b.coreSet.size) a.coreSet to b.coreSet else b.coreSet to a.coreSet
        return small.size >= 2 && big.containsAll(small) // «another love» dentro «tom odell another love»
    }

    /** Toglie dal titolo scritto bene le parentesi con la difficoltà o l'arrangiamento: «Ordinary (Easy Piano)» → «Ordinary». */
    private fun cleanTitle(t: String): String = t.replace(Regex("\\s*\\([^)]*\\)"), "").trim().ifEmpty { t }

    private fun titleCase(ws: List<String>) = ws.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /**
     * [level]: difficoltà stimata di un brano (per ordinare arrangiamenti senza indicazione); [sameContent]: dice se due voci hanno
     * lo stesso contenuto (lo stesso arrangiamento in due formati: si tiene il MusicXML, che è più fedele del MIDI).
     */
    fun build(entries: List<SongEntry>, level: (String) -> Int = { 3 }, sameContent: (String, String) -> Boolean = { _, _ -> false }, size: (String) -> Int = { 0 }): List<SongGroup> {
        val parsed = entries.map { Parsed(it, words(it.title)) }
        val groupable = parsed.indices.filter { entries[it].file.startsWith("user:") }
        val parent = IntArray(parsed.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (i in groupable) for (j in groupable) if (i < j && same(parsed[i], parsed[j])) parent[find(j)] = find(i)

        val out = ArrayList<SongGroup>()
        val done = HashSet<Int>()
        for (i in parsed.indices) {
            val root = find(i)
            if (!done.add(root)) continue
            val members = parsed.indices.filter { find(it) == root }.map { parsed[it] }
            if (members.size == 1) { out += SongGroup(members[0].entry, listOf(Variant(members[0].entry, RANK_NAMES[members[0].rank ?: 3] ?: "Originale", members[0].rank ?: 3))); continue }

            // titolo del gruppo: le parole del membro con meno parole (il titolo «nudo»), nell'ordine in cui compaiono
            val core = members.minBy { it.core.size }.core
            val coreSet = core.toSet()
            val vs0 = members.map { m ->
                val rank = m.rank ?: 3
                val extra = m.words.filter { it !in coreSet && it !in RANKS && it !in NOISE && !it.all(Char::isDigit) }
                Triple(m, rank, extra)
            }.let { all ->
                // stessa difficoltà e stesso contenuto in due formati: una sola voce, di preferenza il MusicXML
                all.groupBy { (_, rank, _) -> rank }.values.flatMap { bucket ->
                    val clusters = ArrayList<MutableList<Triple<Parsed, Int, List<String>>>>()
                    for (t in bucket) {
                        val c = clusters.firstOrNull { sameContent(it[0].first.entry.id, t.first.entry.id) }
                        if (c != null) c += t else clusters += mutableListOf(t)
                    }
                    clusters.map { c -> c.firstOrNull { it.first.entry.format == SongFormat.MUSICXML } ?: c[0] }
                }
            }
            // senza indicazione nel titolo, con più arrangiamenti: si ordinano per difficoltà tecnica stimata e si distribuiscono
            // da Facile ad Avanzato (non per soglie assolute: molti brani veri sono tutti «difficili»)
            val unranked = vs0.filter { it.first.rank == null }.sortedWith(compareBy({ level(it.first.entry.id) }, { size(it.first.entry.id) }))
            val auto = if (unranked.size >= 2) unranked.mapIndexed { i, t ->
                t.first.entry.id to when { i == 0 -> 1; i == unranked.lastIndex -> 4; else -> 2 }
            }.toMap() else emptyMap()
            val vs = vs0.map { t -> auto[t.first.entry.id]?.let { Triple(t.first, it, t.third) } ?: t }
            val sorted = vs.sortedWith(compareBy({ it.second }, { level(it.first.entry.id) }, { it.first.entry.title }))
            val seen = HashMap<String, Int>()
            val variants = sorted.map { (m, rank, extra) ->
                val base = (RANK_NAMES[rank] ?: "Originale") + if (extra.isEmpty()) "" else " (${extra.joinToString(" ")})"
                val n = seen.merge(base, 1, Int::plus)!!
                Variant(m.entry, if (n == 1) base else "$base $n", rank)
            }
            val composer = members.mapNotNull { it.entry.composer }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            // un titolo tutto in minuscolo (dal nome del file) si ordina; un titolo già scritto bene (con le maiuscole) si tiene
            val shown = members.minBy { it.core.size }.entry.title.let { t -> if (t == t.lowercase()) titleCase(core) else cleanTitle(t) }
            out += SongGroup(variants[0].entry.copy(title = shown, composer = composer ?: variants[0].entry.composer), variants, members.map { it.entry.id })
        }
        return out
    }
}
