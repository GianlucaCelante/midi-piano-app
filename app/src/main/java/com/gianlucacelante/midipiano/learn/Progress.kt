package com.gianlucacelante.midipiano.learn

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Progressi dell'utente: stelle per esercizio, giorni di pratica, tempo totale, preferiti. */
class Progress {
    private val stars = HashMap<String, Int>()
    private val days = HashSet<LocalDate>()
    private val favorites = HashSet<String>()
    private val daySeconds = HashMap<LocalDate, Long>() // secondi di pratica per giorno (gli ultimi 90 giorni)
    private val dailyPicks = HashMap<String, String>() // "giorno|tipo" -> id (canzone o esercizio scelti per quel giorno)
    private val songRuns = HashMap<String, MutableList<Double>>() // ultime precisioni a tempo per brano
    private val weakBars = HashMap<String, List<Int>>()           // battute più sbagliate dell'ultimo giro per brano
    private val dailyDone = HashSet<String>()           // "giorno|tipo" completati
    var lastSongId: String? = null
    var lastSongVersion: Int = 3
    private val songsDone = HashSet<String>() // "idBrano:versione" suonate fino in fondo
    var totalPracticeSeconds: Long = 0
        private set
    var lastExerciseId: String? = null
    /** Lettura a prima vista: livello raggiunto (1-5) e melodie finite. */
    var sightLevel: Int = 1
        private set
    var sightDone: Int = 0
        private set
    /** Accordi: livello (1-3), risposte giuste e domande fatte nell'ultimo gruppo di 10, e giuste in tutto. */
    var chordLevel: Int = 1
    var chordCorrectTotal: Int = 0
    var chordRoundCorrect: Int = 0
    var chordRoundAsked: Int = 0

    fun recordSight(stars: Int) {
        sightDone++
        sightLevel = SightReading.nextLevel(sightLevel, stars)
    }

    fun recordChordAnswer(correct: Boolean) {
        chordRoundAsked++
        if (correct) { chordRoundCorrect++; chordCorrectTotal++ }
        val nl = com.gianlucacelante.midipiano.practice.ChordQuiz.adjustLevel(chordLevel, chordRoundCorrect, chordRoundAsked)
        if (nl != chordLevel || chordRoundAsked >= 10) { chordLevel = nl; chordRoundAsked = 0; chordRoundCorrect = 0 }
    }

    fun starsOf(exerciseId: String): Int = stars[exerciseId] ?: 0
    fun allStars(): Map<String, Int> = stars.toMap()

    fun recordExercise(exerciseId: String, earned: Int) {
        stars[exerciseId] = maxOf(starsOf(exerciseId), earned.coerceIn(0, 3))
    }

    fun recordPractice(seconds: Long, day: LocalDate) {
        if (seconds <= 0) return
        totalPracticeSeconds += seconds
        days.add(day)
        if (day.isAfter(LocalDate.now().minusDays(KEEP_DAYS))) daySeconds.merge(day, seconds, Long::plus)
    }

    /** Un brano finito in "A tempo": precisione e battute più sbagliate. Si tengono gli ultimi 10 giri per brano. */
    fun recordRun(songId: String, accuracy: Double, worstBars: List<Int>) {
        val l = songRuns.getOrPut(songId) { mutableListOf() }
        l.add(accuracy.coerceIn(0.0, 1.0))
        while (l.size > 10) l.removeAt(0)
        weakBars[songId] = worstBars
    }
    fun runsOf(songId: String): List<Double> = songRuns[songId]?.toList() ?: emptyList()
    fun bestAccuracy(songId: String): Double? = songRuns[songId]?.maxOrNull()
    fun weakBarsOf(songId: String): List<Int> = weakBars[songId] ?: emptyList()
    fun songsWithRuns(): Set<String> = songRuns.keys.toSet()
    fun dailyDaysDone(): Int = dailyDone.map { it.substringBefore('|') }.toSet().size

    fun dailyPick(day: LocalDate, kind: String): String? = dailyPicks["$day|$kind"]
    /** La scelta del giorno si salva una volta sola: non cambia nemmeno se nel frattempo cambiano le stelle. */
    fun setDailyPick(day: LocalDate, kind: String, id: String) {
        if (day.isAfter(LocalDate.now().minusDays(KEEP_DAYS_PICKS))) dailyPicks.putIfAbsent("$day|$kind", id)
    }
    fun markDailyDone(day: LocalDate, kind: String) { dailyDone.add("$day|$kind") }
    fun isDailyDone(day: LocalDate, kind: String): Boolean = "$day|$kind" in dailyDone

    fun practicedOn(day: LocalDate): Boolean = day in days
    fun runCount(): Int = songRuns.values.sumOf { it.size }

    fun secondsOn(day: LocalDate): Long = daySeconds[day] ?: 0L
    fun goalReached(day: LocalDate, minutes: Int): Boolean = secondsOn(day) >= minutes * 60L

    /** Giorni consecutivi di pratica che finiscono oggi (o ieri, se oggi non si è ancora suonato). */
    fun streak(today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun recordSongDone(songId: String, version: Int) { songsDone.add("$songId:$version") }
    fun songsDoneIds(): Set<String> = songsDone.toSet()

    fun toggleFavorite(id: String): Boolean = if (favorites.remove(id)) false else favorites.add(id)
    fun isFavorite(id: String) = id in favorites
    fun favoriteIds(): Set<String> = favorites.toSet()
    fun exercisesDone(): Int = stars.values.count { it > 0 }

    /** (esercizi completati, totale) fra quelli indicati. */
    fun completion(exerciseIds: List<String>): Pair<Int, Int> = exerciseIds.count { starsOf(it) > 0 } to exerciseIds.size

    fun toJson(): String = JSONObject().apply {
        put("stars", JSONObject(stars as Map<*, *>))
        put("days", JSONArray(days.map { it.toString() }))
        put("seconds", totalPracticeSeconds)
        put("fav", JSONArray(favorites.toList()))
        put("songsDone", JSONArray(songsDone.toList()))
        put("daySec", JSONObject().apply { daySeconds.forEach { (d, s) -> put(d.toString(), s) } })
        put("dailyPicks", JSONObject(dailyPicks as Map<*, *>))
        put("runs", JSONObject().apply { songRuns.forEach { (k, v) -> put(k, JSONArray(v)) } })
        put("weak", JSONObject().apply { weakBars.forEach { (k, v) -> put(k, JSONArray(v)) } })
        put("dailyDone", JSONArray(dailyDone.toList()))
        lastSongId?.let { put("lastSong", it); put("lastSongV", lastSongVersion) }
        lastExerciseId?.let { put("last", it) }
        put("sight", JSONObject().put("level", sightLevel).put("done", sightDone))
        put("chords", JSONObject().put("level", chordLevel).put("total", chordCorrectTotal).put("ok", chordRoundCorrect).put("asked", chordRoundAsked))
    }.toString()

    companion object {
        private const val KEEP_DAYS = 90L
        private const val KEEP_DAYS_PICKS = 30L

        fun fromJson(json: String): Progress {
            val p = Progress()
            val o = try { JSONObject(json) } catch (e: Exception) { return p } // dati illeggibili: si riparte da zero
            // Ogni sezione è letta per conto suo: un dato rovinato non fa perdere tutto il resto
            runCatching { o.optJSONObject("stars")?.let { s -> s.keys().forEach { k -> runCatching { p.stars[k] = s.getInt(k) } } } }
            runCatching { o.optJSONArray("days")?.let { a -> for (i in 0 until a.length()) runCatching { p.days.add(LocalDate.parse(a.getString(i))) } } }
            runCatching { p.totalPracticeSeconds = o.optLong("seconds", 0) }
            runCatching { o.optJSONArray("fav")?.let { a -> for (i in 0 until a.length()) runCatching { p.favorites.add(a.getString(i)) } } }
            runCatching { o.optJSONObject("daySec")?.let { m -> m.keys().forEach { k -> runCatching { val d = LocalDate.parse(k); if (d.isAfter(LocalDate.now().minusDays(KEEP_DAYS))) p.daySeconds[d] = m.getLong(k) } } } }
            fun recent(key: String): Boolean = runCatching { LocalDate.parse(key.substringBefore('|')).isAfter(LocalDate.now().minusDays(KEEP_DAYS_PICKS)) }.getOrDefault(false)
            runCatching { o.optJSONObject("runs")?.let { m -> m.keys().forEach { k -> runCatching { val a = m.getJSONArray(k); p.songRuns[k] = (0 until a.length()).map { a.getDouble(it) }.toMutableList() } } } }
            runCatching { o.optJSONObject("weak")?.let { m -> m.keys().forEach { k -> runCatching { val a = m.getJSONArray(k); p.weakBars[k] = (0 until a.length()).map { a.getInt(it) } } } } }
            runCatching { o.optJSONObject("dailyPicks")?.let { m -> m.keys().forEach { k -> if (recent(k)) runCatching { p.dailyPicks[k] = m.getString(k) } } } }
            runCatching { o.optJSONArray("dailyDone")?.let { a -> for (i in 0 until a.length()) runCatching { val k = a.getString(i); if (recent(k)) p.dailyDone.add(k) } } }
            runCatching { p.lastSongId = o.optString("lastSong", "").ifEmpty { null }; p.lastSongVersion = o.optInt("lastSongV", 3) }
            runCatching { o.optJSONArray("songsDone")?.let { a -> for (i in 0 until a.length()) runCatching { p.songsDone.add(a.getString(i)) } } }
            runCatching { p.lastExerciseId = o.optString("last", "").ifEmpty { null } }
            runCatching { o.optJSONObject("sight")?.let { p.sightLevel = it.optInt("level", 1).coerceIn(1, SightReading.MAX_LEVEL); p.sightDone = it.optInt("done", 0) } }
            runCatching { o.optJSONObject("chords")?.let {
                p.chordLevel = it.optInt("level", 1).coerceIn(1, com.gianlucacelante.midipiano.practice.ChordQuiz.MAX_LEVEL)
                p.chordCorrectTotal = it.optInt("total", 0); p.chordRoundCorrect = it.optInt("ok", 0); p.chordRoundAsked = it.optInt("asked", 0)
            } }
            return p
        }
    }
}
