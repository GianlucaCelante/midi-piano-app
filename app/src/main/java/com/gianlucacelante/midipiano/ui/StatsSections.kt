package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gianlucacelante.midipiano.learn.ExerciseDifficulty
import com.gianlucacelante.midipiano.learn.Insights
import com.gianlucacelante.midipiano.learn.StatsReport
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val Good = Color(0xFF3FB950)
private val Warn = Color(0xFFF2A33A)
private val Bad = Color(0xFFE5484D)

private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
private fun duration(sec: Double): String {
    val m = (sec / 60).roundToInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
private fun accuracyColor(a: Double) = when { a >= 0.85 -> Good; a >= 0.65 -> Warn; else -> Bad }

/** Le statistiche dettagliate, ricavate da tutti i giri registrati: si inseriscono nella schermata dei progressi. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.statsOverview(s: StatsReport, routineDays: Int, routineStreak: Int) {
    // In sintesi
    item {
        StatsHeader("In sintesi")
        val tiles = buildList<Triple<String, String, Color>> {
            add(Triple("Giri suonati", s.runs.toString(), Color.White))
            add(Triple("Giri riusciti", s.finished.toString(), Color.White))
            add(Triple("Precisione media", s.avgAccuracy?.let(::pct) ?: "–", s.avgAccuracy?.let(::accuracyColor) ?: FlowColors.Muted))
            add(Triple("Tempo suonato", duration(s.totalPlaySec), Color.White))
            add(Triple("Giri lasciati a metà", if (s.runs == 0) "–" else pct(s.abandonRate), if (s.abandonRate >= 0.4) Warn else Color.White))
            add(Triple("Routine completata", "$routineDays ${if (routineDays == 1) "giorno" else "giorni"}", Color.White))
            if (routineStreak > 0) add(Triple("Serie di routine", "$routineStreak di fila", Good))
        }
        // righe di pari numero e schede che le riempiono: niente scheda sola in fondo
        val rows = (tiles.size + 3) / 4
        val perRow = (tiles.size + rows - 1) / rows
        FlowRow(maxItemsInEachRow = perRow, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for ((label, value, color) in tiles) StatTile(label, value, color, Modifier.weight(1f))
        }
    }

    // Consigli
    item {
        StatsHeader("Consigli per te")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (tip in Insights.build(s)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("•", color = FlowColors.Accent, fontSize = 18.sp)
                Text(tip, color = Color.White, fontSize = 16.sp)
            }
        }
    }

    if (s.runs == 0) return

    // Andamento
    if (s.daily.isNotEmpty()) item {
        StatsHeader("Andamento della precisione · ultimi giorni a tempo")
        val days = s.daily.takeLast(21)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp)) {
            s.improvement?.let { imp ->
                val points = (imp.delta * 100).roundToInt()
                Text(
                    "Ultimi giri: ${pct(imp.recent)}  (${if (points >= 0) "+" else ""}$points punti rispetto ai precedenti)",
                    color = if (points >= 0) Good else Warn, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val slot = size.width / days.size
                val bar = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                for ((i, d) in days.withIndex()) {
                    val h = (size.height * d.accuracy.toFloat()).coerceAtLeast(3f)
                    drawRoundRect(accuracyColor(d.accuracy), Offset(i * slot + (slot - bar) / 2, size.height - h), Size(bar, h), CornerRadius(4f, 4f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val fmt = DateTimeFormatter.ofPattern("d/M")
                Text(days.first().date.format(fmt), color = FlowColors.Muted, fontSize = 12.sp)
                if (days.size > 1) Text(days.last().date.format(fmt), color = FlowColors.Muted, fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                s.bestDay?.let { Text("Giorno migliore: ${it.format(DateTimeFormatter.ofPattern("d MMM"))}", color = FlowColors.Muted, fontSize = 13.sp) }
                s.worstDay?.let { Text("Giorno più difficile: ${it.format(DateTimeFormatter.ofPattern("d MMM"))}", color = FlowColors.Muted, fontSize = 13.sp) }
            }
        }
    }

}

/** I dettagli: esercizi da rivedere, tempismo, mani, velocità, momento della giornata e note più difficili. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.statsDetails(s: StatsReport, onOpenExercise: (String) -> Unit) {
    if (s.runs == 0) { item { Text("Qui compaiono i dettagli man mano che suoni a tempo.", color = FlowColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }; return }
    // Esercizi da rivedere
    if (s.hardest.isNotEmpty()) item {
        StatsHeader("Esercizi da rivedere")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in s.hardest) HardRow(h, s.weakBarsByExercise[h.id]?.take(3)?.map { it.first }.orEmpty()) { onOpenExercise(h.id) }
        }
    }

    // Tempismo
    item {
        StatsHeader("Tempismo, durata e pause")
        val t = s.timing
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ms = abs(t.meanDevMs).roundToInt()
            Text(
                when (t.tendency) { "in anticipo" -> "In media suoni in anticipo di $ms ms"; "in ritardo" -> "In media suoni in ritardo di $ms ms"; else -> "In media suoni a tempo (±$ms ms)" },
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            val total = t.early + t.late
            if (total > 0) SplitBar(t.early.toFloat() / total, "In anticipo ${t.early}", "In ritardo ${t.late}")
            else Text("Nessun colpo fuori dal tempo perfetto: ottimo.", color = FlowColors.Muted, fontSize = 14.sp)
            Text("Note non perfette: ${pct(t.offBeatPct)}", color = FlowColors.Muted, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Pause: troppo presto", t.restEarly.toString(), if (t.restEarly > 0) Warn else Color.White)
                StatTile("Pause: troppo tardi", t.restLate.toString(), if (t.restLate > 0) Warn else Color.White)
                StatTile("Tasti in pausa", t.restInRest.toString(), if (t.restInRest > 0) Warn else Color.White)
                StatTile("Note troppo corte", t.holdShort.toString(), if (t.holdShort > 0) Warn else Color.White)
                StatTile("Note troppo lunghe", t.holdLong.toString(), if (t.holdLong > 0) Warn else Color.White)
            }
        }
    }

    // Mani, velocità, momento della giornata
    if (s.byHand.isNotEmpty()) item {
        StatsHeader("Le mani")
        BarsCard(listOf("RIGHT" to "Destra", "LEFT" to "Sinistra", "BOTH" to "Due mani").mapNotNull { (k, n) -> s.byHand[k]?.let { Triple(n, it.accuracy, "${pct(it.accuracy)} · ${it.runs} giri") } })
    }
    if (s.bySpeed.isNotEmpty()) item {
        StatsHeader("Precisione per velocità")
        BarsCard(s.bySpeed.map { (v, a) -> Triple("${(v * 100).roundToInt()}%", a, pct(a)) })
    }
    if (s.byPartOfDay.isNotEmpty()) item {
        StatsHeader("Quando suoni meglio")
        BarsCard(listOf("mattina", "pomeriggio", "sera").mapNotNull { k -> s.byPartOfDay[k]?.let { Triple(k.replaceFirstChar { c -> c.uppercase() }, it, pct(it)) } })
    }

    // Tasti più difficili
    if (s.weakKeys.isNotEmpty()) item {
        StatsHeader("Le note più difficili")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in s.weakKeys) Column(Modifier.clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Insights.keyNameOctave(k.key), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (k.wrong > 0) Text("sbagliata ${k.wrong}×", color = Bad, fontSize = 12.sp)
                if (k.missed > 0) Text("mancata ${k.missed}×", color = Warn, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatsHeader(text: String) {
    Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color = Color.White, modifier: Modifier = Modifier.width(190.dp)) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FlowColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HardRow(h: ExerciseDifficulty, weakBars: List<Int>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).clickable { onClick() }.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(h.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${h.attempts} giri · ${pct(h.failRate)} non riusciti · precisione ${pct(h.avgAccuracy)}", color = FlowColors.Muted, fontSize = 13.sp)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth(h.score.toFloat().coerceIn(0.03f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(if (h.score > 0.5) Bad else Warn))
        }
        if (weakBars.isNotEmpty()) Text("Battute da ripassare: ${weakBars.sorted().joinToString(", ")}", color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SplitBar(leftShare: Float, leftLabel: String, rightLabel: String) {
    Column {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh)) {
            if (leftShare > 0f) Box(Modifier.weight(leftShare).height(12.dp).background(Warn))
            if (leftShare < 1f) Box(Modifier.weight(1f - leftShare).height(12.dp).background(Color(0xFF5B8DEF)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, color = FlowColors.Muted, fontSize = 13.sp); Text(rightLabel, color = FlowColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BarsCard(rows: List<Triple<String, Double, String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((label, value, text) in rows) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.width(110.dp))
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh)) {
                Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0.02f, 1f)).height(10.dp).background(accuracyColor(value)))
            }
            Text(text, color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        }
    }
}
