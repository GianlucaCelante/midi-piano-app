package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gianlucacelante.midipiano.DayFlags
import com.gianlucacelante.midipiano.learn.DaySummary
import com.gianlucacelante.midipiano.learn.RunRecord
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val IT = Locale.ITALIAN
private val Good = Color(0xFF3FB950)
private val Warn = Color(0xFFF2A33A)
private val Bad = Color(0xFFE5484D)

private fun heat(accuracy: Double?, runs: Int): Color = when {
    runs == 0 -> FlowColors.Surface
    accuracy == null -> FlowColors.SurfaceHigh
    accuracy >= 0.85 -> Good.copy(alpha = 0.55f)
    accuracy >= 0.65 -> Warn.copy(alpha = 0.55f)
    else -> Bad.copy(alpha = 0.55f)
}

private fun minutesText(sec: Double): String = if (sec < 60) "${sec.roundToInt()} s" else "${(sec / 60).roundToInt()} min"

/** Vista a calendario: a sinistra il mese (colore = precisione del giorno), a destra tutto ciò che è successo nel giorno scelto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarTab(records: List<RunRecord>, flags: Map<LocalDate, DayFlags>, today: LocalDate = LocalDate.now()) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val sel = LocalDate.parse(selected)
    val cells = DaySummary.month(records, ym.year, ym.monthValue)
    val byDate = cells.associateBy { it.date }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // ---- il mese
        Column(Modifier.weight(1.15f)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clip(CircleShape).clickable { month = ym.minusMonths(1).toString() }.padding(horizontal = 14.dp))
                Text(
                    ym.month.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.uppercase() } + " " + ym.year,
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text("›", color = if (ym.isBefore(YearMonth.from(today))) Color.White else FlowColors.Muted, fontSize = 30.sp,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = ym.isBefore(YearMonth.from(today))) { month = ym.plusMonths(1).toString() }.padding(horizontal = 14.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                for (l in listOf("L", "M", "M", "G", "V", "S", "D")) Text(l, color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            val lead = ym.atDay(1).dayOfWeek.value - 1
            val all: List<LocalDate?> = List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (week in all.chunked(7)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 7) {
                        val d = week.getOrNull(i)
                        if (d == null) { Box(Modifier.weight(1f).aspectRatio(1f)); continue }
                        val c = byDate[d]
                        val f = flags[d]
                        val isSel = d == sel
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(heat(c?.accuracy, c?.runs ?: 0))
                                .border(if (isSel) 3.dp else if (d == today) 1.5.dp else 0.dp, if (isSel) FlowColors.Accent else Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable(enabled = !d.isAfter(today)) { selected = d.toString() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.dayOfMonth.toString(), color = if (d.isAfter(today)) FlowColors.Muted.copy(alpha = 0.4f) else Color.White, fontSize = 15.sp, fontWeight = if (isSel) FlowColors.Accent.let { FontWeight.Bold } else FontWeight.Normal)
                            // piccoli segnali: routine completata (in alto) e sfide fatte (in basso)
                            if (f?.routineDone == true) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(FlowColors.Accent))
                            if (f != null && (f.songDone || f.trialDone)) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(Color(0xFF5B8DEF)))
                        }
                    }
                }
            }
            // legenda
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendDot(Good, "precisione alta"); LegendDot(Warn, "media"); LegendDot(Bad, "bassa"); LegendDot(FlowColors.Accent, "routine fatta"); LegendDot(Color(0xFF5B8DEF), "sfida fatta")
            }
            // il mese in breve
            val played = cells.count { it.runs > 0 || flags[it.date]?.practiced == true }
            val busiest = DaySummary.busiest(cells)
            val best = DaySummary.best(cells)
            Text(
                "Giorni in cui hai suonato: $played" + (busiest?.let { " · più attivo: il ${it.dayOfMonth}" } ?: "") + (best?.let { " · giorno migliore: il ${it.dayOfMonth}" } ?: ""),
                color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        // ---- il giorno scelto
        DayDetail(sel, DaySummary.of(records, sel), flags[sel], Modifier.weight(1f))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, color = FlowColors.Muted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetail(date: LocalDate, s: DaySummary, flags: DayFlags?, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(FlowColors.Surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.uppercase() } + " " + date.dayOfMonth + " " + date.month.getDisplayName(TextStyle.FULL, IT),
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        val minutes = maxOf(flags?.seconds ?: 0L, s.playSec.toLong())
        if (s.runs == 0 && flags?.any != true) { Text("Niente di registrato in questo giorno.", color = FlowColors.Muted, fontSize = 15.sp); return@Column }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayChip("Pratica", minutesText(minutes.toDouble()))
            if (s.runs > 0) DayChip("Giri", s.runs.toString())
            s.accuracy?.let { DayChip("Precisione", "${(it * 100).roundToInt()}%", if (it >= 0.85) Good else if (it >= 0.65) Warn else Bad) }
            if (s.runs > 0) DayChip("Errori", s.errors.toString())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (flags?.routineDone == true) Badge("Routine completata", FlowColors.Accent)
            if (flags?.songDone == true) Badge("Canzone del giorno", Color(0xFF5B8DEF))
            if (flags?.trialDone == true) Badge("Prova del giorno", Color(0xFF5B8DEF))
        }
        if (s.runs > 0) {
            Text("Giri riusciti ${s.finished} · non riusciti ${s.failed} · lasciati a metà ${s.abandoned}", color = FlowColors.Muted, fontSize = 13.sp)
            s.meanDevMs?.let { d ->
                val ms = abs(d).roundToInt()
                Text(
                    when { d <= -15 -> "Tempismo: in anticipo di circa $ms ms"; d >= 15 -> "Tempismo: in ritardo di circa $ms ms"; else -> "Tempismo: a tempo (±$ms ms)" },
                    color = FlowColors.Muted, fontSize = 13.sp,
                )
            }
            Text("Cosa hai suonato", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            for (it in s.items) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (it.source == "song") "♪" else "▸", color = FlowColors.Accent, fontSize = 15.sp)
                Text(it.title, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${it.attempts} ${if (it.attempts == 1) "giro" else "giri"}" + (it.accuracy?.let { a -> " · ${(a * 100).roundToInt()}%" } ?: ""), color = FlowColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DayChip(label: String, value: String, valueColor: Color = Color.White) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(FlowColors.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FlowColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = Color(0xFF0A0F1E), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}
