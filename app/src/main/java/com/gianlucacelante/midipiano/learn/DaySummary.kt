package com.gianlucacelante.midipiano.learn

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Un esercizio o un brano suonato in un giorno, con quanti giri e quanti riusciti. */
data class DayItem(val id: String, val title: String, val source: String, val attempts: Int, val passed: Int, val accuracy: Double?)

/** Tutto quello che è successo in un giorno (dai giri registrati). */
data class DaySummary(
    val date: LocalDate, val runs: Int, val finished: Int, val failed: Int, val abandoned: Int,
    val accuracy: Double?, val errors: Int, val playSec: Double, val meanDevMs: Double?, val items: List<DayItem>,
) {
    /** Una cella del calendario di un mese: giri del giorno e precisione media (null se non si è suonato a tempo). */
    data class Cell(val date: LocalDate, val runs: Int, val accuracy: Double?)

    companion object {
        private fun dayOf(r: RunRecord, zone: ZoneId) = Instant.ofEpochMilli(r.whenMillis).atZone(zone).toLocalDate()
        private val RunRecord.timed: Boolean get() = mode == "TEMPO" && outcome != "abandoned"

        fun of(all: List<RunRecord>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DaySummary {
            val rs = all.filter { dayOf(it, zone) == date }.sortedBy { it.whenMillis }
            val timed = rs.filter { it.timed }
            val nh = timed.sumOf { it.noteHits }
            val items = rs.groupBy { it.id }.map { (id, g) ->
                val t = g.filter { it.timed }
                DayItem(id, g.last().title.ifEmpty { id }, g.first().source, g.size, g.count { it.outcome == "passed" || it.outcome == "finished" }, if (t.isEmpty()) null else t.map { it.accuracy }.average())
            }
            return DaySummary(
                date = date, runs = rs.size, finished = rs.count { it.outcome == "passed" || it.outcome == "finished" }, failed = rs.count { it.outcome == "failed" }, abandoned = rs.count { it.outcome == "abandoned" },
                accuracy = if (timed.isEmpty()) null else timed.map { it.accuracy }.average(), errors = rs.sumOf { it.wrong + it.misses }, playSec = rs.sumOf { it.durationSec },
                meanDevMs = if (nh == 0) null else timed.sumOf { it.meanDevMs.toDouble() * it.noteHits } / nh, items = items,
            )
        }

        /** Le celle di tutti i giorni di un mese (1..ultimo giorno). */
        fun month(all: List<RunRecord>, year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): List<Cell> {
            val ym = YearMonth.of(year, month)
            val byDay = all.groupBy { dayOf(it, zone) }
            return (1..ym.lengthOfMonth()).map { d ->
                val date = ym.atDay(d)
                val rs = byDay[date].orEmpty()
                val timed = rs.filter { it.timed }
                Cell(date, rs.size, if (timed.isEmpty()) null else timed.map { it.accuracy }.average())
            }
        }

        /** Il giorno del mese con più giri (null se non si è suonato). */
        fun busiest(cells: List<Cell>): LocalDate? = cells.filter { it.runs > 0 }.maxByOrNull { it.runs }?.date

        /** Il giorno del mese con la precisione media più alta. */
        fun best(cells: List<Cell>): LocalDate? = cells.filter { it.accuracy != null }.maxByOrNull { it.accuracy!! }?.date
    }
}
