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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.gianlucacelante.midipiano.ProgressSnapshot
import com.gianlucacelante.midipiano.learn.Achievements
import com.gianlucacelante.midipiano.practice.TakeMeta
import com.gianlucacelante.midipiano.song.SongEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Progressi: minuti, calendario delle sfide, traguardi, brani a tempo e registrazioni. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    progress: ProgressSnapshot,
    songs: List<SongEntry>,
    takes: List<TakeMeta>,
    replayingTake: String?,
    onReplayTake: (String) -> Unit,
    onDeleteTake: (String) -> Unit,
    onOpenSong: (SongEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = FlowColors.Accent
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Progressi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp)) }

        // Minuti degli ultimi 14 giorni
        item {
            Header("Minuti di pratica · ultimi 14 giorni")
            val max = (progress.minutes.maxOfOrNull { it.second } ?: 0).coerceAtLeast(10)
            Row(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((day, min) in progress.minutes) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (min > 0) Text("$min", color = FlowColors.Muted, fontSize = 11.sp)
                        Box(Modifier.fillMaxWidth().height((88f * min / max).coerceAtLeast(if (min > 0) 4f else 2f).dp).clip(RoundedCornerShape(4.dp)).background(if (min > 0) accent else FlowColors.SurfaceHigh))
                        Text(day.dayOfMonth.toString(), color = FlowColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        // Calendario di 5 settimane
        item {
            Header("Calendario · arancio pieno = entrambe le sfide fatte, tenue = hai suonato")
            val cal = progress.calendar
            if (cal.isNotEmpty()) {
                val lead = cal.first().first.dayOfWeek.value - 1 // si parte dal lunedì
                val cells: List<Pair<LocalDate, Int>?> = List(lead) { null } + cal
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in listOf("L", "M", "M", "G", "V", "S", "D")) Text(l, color = FlowColors.Muted, fontSize = 12.sp, modifier = Modifier.width(38.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    for (week in cells.chunked(7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 0 until 7) {
                                val c = week.getOrNull(i)
                                val color = when (c?.second) { 2 -> accent; 1 -> accent.copy(alpha = 0.35f); 0 -> FlowColors.SurfaceHigh; else -> Color.Transparent }
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
                                    if (c != null) Text(c.first.dayOfMonth.toString(), color = if (c.second == 2) Color(0xFF0A0F1E) else FlowColors.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Traguardi
        item {
            val done = progress.achievements.count { it.unlocked }
            Header("Traguardi · $done/${progress.achievements.size}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in progress.achievements) Badge(a)
            }
        }

        // Brani suonati a tempo
        val rows = progress.songRuns.entries.mapNotNull { (id, runs) -> songs.firstOrNull { it.id == id }?.let { it to runs } }.sortedByDescending { it.second.size }
        if (rows.isNotEmpty()) {
            item { Header("Brani suonati a tempo") }
            items(rows.size) { i ->
                val (song, runs) = rows[i]
                val weak = progress.weakBars[song.id].orEmpty()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).clickable { onOpenSong(song) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "Migliore ${(runs.max() * 100).toInt()}%  ·  ${runs.size} ${if (runs.size == 1) "giro" else "giri"}" + if (weak.isNotEmpty()) "  ·  da ripassare: battute ${weak.joinToString(", ")}" else "",
                            color = FlowColors.Muted, fontSize = 13.sp, maxLines = 2,
                        )
                    }
                    Sparkline(runs, accent)
                }
            }
        }

        // Registrazioni
        item { Header("Le tue registrazioni") }
        if (takes.isEmpty()) item { Text("Quando finisci un brano in «A tempo» la tua esecuzione si salva qui, e puoi riascoltarla.", color = FlowColors.Muted, fontSize = 14.sp) }
        items(takes.size) { i ->
            val t = takes[i]
            val playing = replayingTake == t.name
            val whenText = java.time.Instant.ofEpochMilli(t.whenMillis).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(accent).clickable { onReplayTake(t.name) }, contentAlignment = Alignment.Center) {
                    Text(if (playing) "■" else "▶", color = Color(0xFF0A0F1E), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 16.sp, maxLines = 1)
                    Text("$whenText  ·  ${t.noteCount} note" + (t.accuracy?.let { "  ·  precisione ${(it * 100).toInt()}%" } ?: ""), color = FlowColors.Muted, fontSize = 13.sp)
                }
                Text("✕", color = FlowColors.Muted, fontSize = 18.sp, modifier = Modifier.clickable { onDeleteTake(t.name) }.padding(10.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Badge(a: Achievements.Achievement) {
    val on = a.unlocked
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(14.dp)).background(if (on) FlowColors.SurfaceHigh else FlowColors.Surface).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (on) "★" else "☆", color = if (on) FlowColors.Accent else FlowColors.Muted, fontSize = 22.sp)
            Text(a.title, color = if (on) Color.White else FlowColors.Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(a.description, color = FlowColors.Muted, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        if (!on) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh)) {
                Box(Modifier.fillMaxWidth((a.current.toFloat() / a.target).coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(50)).background(FlowColors.Accent))
            }
            Text("${a.current}/${a.target}", color = FlowColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Piccolo grafico delle ultime precisioni (0-100 %). */
@Composable
private fun Sparkline(values: List<Double>, color: Color) {
    Canvas(Modifier.size(width = 120.dp, height = 36.dp)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val barW = minOf(w / values.size * 0.7f, 10.dp.toPx()) // con pochi giri le barre restano sottili
        val gap = barW * 0.45f
        val total = values.size * barW + (values.size - 1) * gap
        var x = w - total // allineate a destra: l'ultimo giro sta all'estremità
        for ((i, v) in values.withIndex()) {
            val bh = (h * v.toFloat()).coerceAtLeast(3f)
            drawRoundRect(color.copy(alpha = if (i == values.lastIndex) 1f else 0.55f), Offset(x, h - bh), Size(barW, bh), CornerRadius(3f, 3f))
            x += barW + gap
        }
    }
}
