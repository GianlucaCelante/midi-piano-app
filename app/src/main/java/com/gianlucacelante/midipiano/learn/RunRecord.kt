package com.gianlucacelante.midipiano.learn

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Un giro suonato (finito, fallito o lasciato a metà), con tutto ciò che si riesce a misurare: serve a costruire le statistiche.
 * [outcome]: «passed» (giro riuscito), «failed» (finito ma non pulito), «abandoned» (lasciato a metà), «finished» (brano libero finito).
 */
data class RunRecord(
    val whenMillis: Long,
    val source: String = "exercise", // «exercise» o «song»
    val id: String,
    val title: String = "",
    val step: Int = -1, // passo dell'esercizio (da 0), -1 se non c'è
    val mode: String = "WAIT",
    val hand: String = "BOTH",
    val speed: Double = 1.0,
    val outcome: String = "finished",
    val durationSec: Double = 0.0, // tempo reale del giro
    val hits: Int = 0, val misses: Int = 0, val wrong: Int = 0, val accuracy: Double = 0.0, val stars: Int = 0,
    val noteHits: Int = 0, // note singole colpite (gli accordi contano per nota): base delle medie di tempismo
    val early: Int = 0, val late: Int = 0, val offBeat: Int = 0, // colpi in anticipo / in ritardo / non perfetti
    val meanDevMs: Int = 0, val meanAbsDevMs: Int = 0, // scarto medio con segno (negativo = in anticipo) e assoluto, in ms reali
    val restEarly: Int = 0, val restLate: Int = 0, val restInRest: Int = 0, // commenti sulle pause
    val holdShort: Int = 0, val holdLong: Int = 0, // note tenute troppo poco / troppo a lungo
    val barErrors: Map<Int, Int> = emptyMap(), val wrongKeys: Map<Int, Int> = emptyMap(), val missedKeys: Map<Int, Int> = emptyMap(),
    val routine: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("t", whenMillis); put("s", source); put("id", id); put("n", title); put("st", step); put("m", mode); put("h", hand); put("v", speed)
        put("o", outcome); put("d", durationSec); put("ok", hits); put("mi", misses); put("w", wrong); put("a", accuracy); put("sr", stars)
        put("nh", noteHits); put("e", early); put("l", late); put("ob", offBeat); put("md", meanDevMs); put("ma", meanAbsDevMs)
        put("re", restEarly); put("rl", restLate); put("rr", restInRest); put("hs", holdShort); put("hl", holdLong)
        put("be", JSONObject(barErrors.mapKeys { it.key.toString() } as Map<*, *>))
        put("wk", JSONObject(wrongKeys.mapKeys { it.key.toString() } as Map<*, *>))
        put("mk", JSONObject(missedKeys.mapKeys { it.key.toString() } as Map<*, *>))
        put("r", routine)
    }.toString()

    companion object {
        private fun ints(o: JSONObject?): Map<Int, Int> {
            if (o == null) return emptyMap()
            val m = HashMap<Int, Int>()
            for (k in o.keys()) runCatching { m[k.toInt()] = o.getInt(k) }
            return m
        }

        fun fromJson(line: String): RunRecord? = runCatching {
            val o = JSONObject(line)
            RunRecord(
                whenMillis = o.getLong("t"), source = o.optString("s", "exercise"), id = o.getString("id"), title = o.optString("n", ""), step = o.optInt("st", -1),
                mode = o.optString("m", "WAIT"), hand = o.optString("h", "BOTH"), speed = o.optDouble("v", 1.0), outcome = o.optString("o", "finished"),
                durationSec = o.optDouble("d", 0.0), hits = o.optInt("ok"), misses = o.optInt("mi"), wrong = o.optInt("w"), accuracy = o.optDouble("a", 0.0), stars = o.optInt("sr"),
                noteHits = o.optInt("nh"), early = o.optInt("e"), late = o.optInt("l"), offBeat = o.optInt("ob"), meanDevMs = o.optInt("md"), meanAbsDevMs = o.optInt("ma"),
                restEarly = o.optInt("re"), restLate = o.optInt("rl"), restInRest = o.optInt("rr"), holdShort = o.optInt("hs"), holdLong = o.optInt("hl"),
                barErrors = ints(o.optJSONObject("be")), wrongKeys = ints(o.optJSONObject("wk")), missedKeys = ints(o.optJSONObject("mk")), routine = o.optBoolean("r", false),
            )
        }.getOrNull()

        /** Righe di un file di registro: quelle illeggibili (o vuote) si saltano. */
        fun parseLines(lines: List<String>): List<RunRecord> = lines.mapNotNull { if (it.isBlank()) null else fromJson(it) }

        /** Tiene i [max] record più recenti. */
        fun capped(all: List<RunRecord>, max: Int): List<RunRecord> = if (all.size <= max) all else all.sortedBy { it.whenMillis }.takeLast(max)
    }
}

/** Archivio dei giri: un file di testo con un record JSON per riga, sul dispositivo (solo in aggiunta, ripulito quando diventa grande). */
class RunStore(context: Context, private val max: Int = 20_000) {
    private val file = File(context.filesDir, "runs.jsonl")

    @Synchronized fun append(r: RunRecord) {
        runCatching { file.appendText(r.toJson() + "\n") }
    }

    @Synchronized fun load(): List<RunRecord> {
        if (!file.exists()) return emptyList()
        val all = runCatching { RunRecord.parseLines(file.readLines()) }.getOrDefault(emptyList())
        if (all.size > max + max / 10) { // ogni tanto si ripulisce, tenendo i più recenti
            val kept = RunRecord.capped(all, max)
            runCatching { file.writeText(kept.joinToString("\n", postfix = "\n") { it.toJson() }) }
            return kept
        }
        return all
    }
}
