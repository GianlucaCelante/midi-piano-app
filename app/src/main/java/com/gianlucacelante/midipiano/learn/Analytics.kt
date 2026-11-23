package com.gianlucacelante.midipiano.learn

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ExerciseDifficulty(val id: String, val title: String, val attempts: Int, val failRate: Double, val avgAccuracy: Double, val avgErrors: Double, val score: Double)
data class TimingProfile(
    val meanDevMs: Double, val early: Int, val late: Int, val offBeatPct: Double, val tendency: String,
    val restEarly: Int, val restLate: Int, val restInRest: Int, val holdShort: Int, val holdLong: Int,
)
data class HandStat(val runs: Int, val accuracy: Double)
data class DayPoint(val date: LocalDate, val accuracy: Double, val runs: Int, val errors: Int)
data class Improvement(val recent: Double, val previous: Double, val delta: Double)
data class KeyStat(val key: Int, val wrong: Int, val missed: Int) { val total: Int get() = wrong + missed }

/** Tutte le statistiche ricavate dai giri registrati. */
data class StatsReport(
    val runs: Int, val finished: Int, val failed: Int, val abandoned: Int, val abandonRate: Double,
    val totalPlaySec: Double, val avgAccuracy: Double?,
    val hardest: List<ExerciseDifficulty>, val timing: TimingProfile,
    val byHand: Map<String, HandStat>, val weakestHand: String?,
    val daily: List<DayPoint>, val bestDay: LocalDate?, val worstDay: LocalDate?, val improvement: Improvement?,
    val weakKeys: List<KeyStat>, val byPartOfDay: Map<String, Double>, val bestPartOfDay: String?,
    val bySpeed: Map<Double, Double>, val weakBarsByExercise: Map<String, List<Pair<Int, Int>>>,
)

/** Calcolo delle statistiche: logica pura, nessuna dipendenza da Android. */
object Analytics {
    private const val MIN_ATTEMPTS_FOR_HARDEST = 2
    private const val MAX_HARDEST = 5
    private const val MIN_HARDEST_SCORE = 0.15 // sotto questa soglia un esercizio va bene così: non si propone di rivederlo
    private const val MAX_WEAK_KEYS = 8
    private const val MIN_RUNS_FOR_TREND = 6
    private const val TENDENCY_MS = 15

    private fun median(v: List<Double>): Double { val x = v.sorted(); return if (x.size % 2 == 1) x[x.size / 2] else (x[x.size / 2 - 1] + x[x.size / 2]) / 2 }

    private val RunRecord.timed: Boolean get() = mode == "TEMPO" && outcome != "abandoned"
    private fun part(hour: Int) = when (hour) { in 5..11 -> "mattina"; in 12..17 -> "pomeriggio"; else -> "sera" }

    fun compute(all: List<RunRecord>, zone: ZoneId = ZoneId.systemDefault()): StatsReport {
        val records = all.sortedBy { it.whenMillis }
        val timed = records.filter { it.timed }
        val finished = records.count { it.outcome == "passed" || it.outcome == "finished" }
        val failed = records.count { it.outcome == "failed" }
        val abandoned = records.count { it.outcome == "abandoned" }

        // esercizi più difficili: tanti giri falliti o lasciati e precisione bassa
        val hardest = records.filter { it.source == "exercise" }.groupBy { it.id }.mapNotNull { (id, rs) ->
            if (rs.size < MIN_ATTEMPTS_FOR_HARDEST) return@mapNotNull null
            val failRate = rs.count { it.outcome == "failed" || it.outcome == "abandoned" }.toDouble() / rs.size
            val t = rs.filter { it.timed }
            val acc = if (t.isEmpty()) 1.0 - failRate else t.map { it.accuracy }.average() // senza giri a tempo: conta solo quanti ne sono falliti
            val errors = rs.map { (it.wrong + it.misses).toDouble() }.average()
            ExerciseDifficulty(id, rs.last().title.ifEmpty { id }, rs.size, failRate, acc, errors, failRate * 0.6 + (1 - acc) * 0.4)
        }.filter { it.score >= MIN_HARDEST_SCORE }.sortedByDescending { it.score }.take(MAX_HARDEST)

        // tempismo: media pesata sulle note colpite
        val nh = records.sumOf { it.noteHits }
        val meanDev = if (nh == 0) 0.0 else records.sumOf { it.meanDevMs.toDouble() * it.noteHits } / nh
        val timing = TimingProfile(
            meanDevMs = meanDev, early = records.sumOf { it.early }, late = records.sumOf { it.late },
            offBeatPct = if (nh == 0) 0.0 else records.sumOf { it.offBeat }.toDouble() / nh,
            tendency = when { nh == 0 -> "a tempo"; meanDev <= -TENDENCY_MS -> "in anticipo"; meanDev >= TENDENCY_MS -> "in ritardo"; else -> "a tempo" },
            restEarly = records.sumOf { it.restEarly }, restLate = records.sumOf { it.restLate }, restInRest = records.sumOf { it.restInRest },
            holdShort = records.sumOf { it.holdShort }, holdLong = records.sumOf { it.holdLong },
        )

        val byHand = timed.groupBy { it.hand }.mapValues { (_, rs) -> HandStat(rs.size, rs.map { it.accuracy }.average()) }
        val weakestHand = if (byHand.size >= 2) byHand.minByOrNull { it.value.accuracy }?.key else null

        val daily = timed.groupBy { Instant.ofEpochMilli(it.whenMillis).atZone(zone).toLocalDate() }.toSortedMap().map { (d, rs) ->
            DayPoint(d, rs.map { it.accuracy }.average(), rs.size, rs.sumOf { it.wrong + it.misses })
        }
        val best = daily.maxByOrNull { it.accuracy }?.date
        val worst = if (daily.size >= 2) daily.minByOrNull { it.accuracy }?.date else null

        val improvement = if (timed.size >= MIN_RUNS_FOR_TREND) {
            val n = minOf(5, timed.size / 2)
            val recent = median(timed.takeLast(n).map { it.accuracy }) // la mediana: un solo giro pessimo non deve far crollare l'andamento
            val previous = median(timed.dropLast(n).takeLast(n).map { it.accuracy })
            Improvement(recent, previous, recent - previous)
        } else null

        val wrong = HashMap<Int, Int>(); val missed = HashMap<Int, Int>()
        for (r in records) { r.wrongKeys.forEach { (k, n) -> wrong.merge(k, n, Int::plus) }; r.missedKeys.forEach { (k, n) -> missed.merge(k, n, Int::plus) } }
        val weakKeys = (wrong.keys + missed.keys).map { KeyStat(it, wrong[it] ?: 0, missed[it] ?: 0) }
            .sortedWith(compareByDescending<KeyStat> { it.total }.thenBy { it.key }).take(MAX_WEAK_KEYS)

        val byPart = timed.groupBy { part(Instant.ofEpochMilli(it.whenMillis).atZone(zone).hour) }.mapValues { (_, rs) -> rs.map { it.accuracy }.average() }
        val bySpeed = timed.groupBy { Math.round(it.speed * 100) / 100.0 }.mapValues { (_, rs) -> rs.map { it.accuracy }.average() }.toSortedMap()

        val weakBars = records.filter { it.source == "exercise" && it.barErrors.isNotEmpty() }.groupBy { it.id }.mapValues { (_, rs) ->
            val m = HashMap<Int, Int>(); rs.forEach { r -> r.barErrors.forEach { (b, n) -> m.merge(b, n, Int::plus) } }
            m.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }
        }

        return StatsReport(
            runs = records.size, finished = finished, failed = failed, abandoned = abandoned,
            abandonRate = if (records.isEmpty()) 0.0 else abandoned.toDouble() / records.size,
            totalPlaySec = records.sumOf { it.durationSec }, avgAccuracy = if (timed.isEmpty()) null else timed.map { it.accuracy }.average(),
            hardest = hardest, timing = timing, byHand = byHand, weakestHand = weakestHand,
            daily = daily, bestDay = best, worstDay = worst, improvement = improvement,
            weakKeys = weakKeys, byPartOfDay = byPart, bestPartOfDay = byPart.maxByOrNull { it.value }?.key,
            bySpeed = bySpeed, weakBarsByExercise = weakBars,
        )
    }
}
