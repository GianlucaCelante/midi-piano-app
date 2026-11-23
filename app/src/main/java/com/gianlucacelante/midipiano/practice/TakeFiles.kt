package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongNote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un'esecuzione registrata: titolo del brano, quando, precisione (se a tempo) e le note suonate. */
class TakeData(val title: String, val whenMillis: Long, val accuracy: Double?, val notes: List<SongNote>)

/** Riga dell'elenco delle registrazioni (senza caricare tutte le note). */
class TakeMeta(val name: String, val title: String, val whenMillis: Long, val accuracy: Double?, val noteCount: Int, val durationSec: Double)

/** Registrazioni su file JSON, una per esecuzione, nella cartella [dir]; se ne tengono al massimo [max]. */
class TakeFiles(private val dir: File, private val max: Int = 20) {
    init { dir.mkdirs() }

    fun save(t: TakeData): String {
        var name = "${t.whenMillis}.json"
        var n = 1
        while (File(dir, name).exists()) name = "${t.whenMillis}-${n++}.json"
        val o = JSONObject().put("title", t.title).put("when", t.whenMillis)
        t.accuracy?.let { o.put("acc", it) }
        o.put("notes", JSONArray().also { a ->
            for (n2 in t.notes) a.put(JSONArray().put(n2.startSec).put(n2.durationSec).put(n2.key).put(n2.velocity).put(if (n2.hand == Hand.LEFT) 0 else 1))
        })
        File(dir, name).writeText(o.toString())
        prune()
        return name
    }

    fun load(name: String): TakeData? = runCatching {
        val o = JSONObject(File(dir, name).readText())
        val a = o.getJSONArray("notes")
        val notes = (0 until a.length()).map { i ->
            val r = a.getJSONArray(i)
            SongNote(r.getDouble(0), r.getDouble(1), r.getInt(2), r.getInt(3), if (r.getInt(4) == 0) Hand.LEFT else Hand.RIGHT)
        }
        TakeData(o.getString("title"), o.getLong("when"), if (o.has("acc")) o.getDouble("acc") else null, notes)
    }.getOrNull()

    /** Dalla più recente; i file illeggibili si ignorano. */
    fun list(): List<TakeMeta> = (dir.listFiles { f -> f.extension == "json" }.orEmpty()).mapNotNull { f ->
        load(f.name)?.let { t -> TakeMeta(f.name, t.title, t.whenMillis, t.accuracy, t.notes.size, t.notes.maxOfOrNull { it.startSec + it.durationSec } ?: 0.0) }
    }.sortedByDescending { it.whenMillis }

    fun delete(name: String) { File(dir, name).delete() }

    private fun prune() {
        val all = list()
        for (old in all.drop(max)) delete(old.name)
    }
}
