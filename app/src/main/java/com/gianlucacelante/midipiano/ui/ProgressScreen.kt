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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.gianlucacelante.midipiano.learn.Analytics
import com.gianlucacelante.midipiano.learn.RunRecord
import com.gianlucacelante.midipiano.learn.StatsReport
import com.gianlucacelante.midipiano.song.SongEntry

private val TABS = listOf("Panoramica", "Calendario", "Dettagli", "Traguardi e sfide")

/** Progressi in quattro schede: panoramica, calendario giorno per giorno, dettagli statistici, traguardi e sfide. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    progress: ProgressSnapshot,
    songs: List<SongEntry>,
    onOpenSong: (SongEntry) -> Unit,
    modifier: Modifier = Modifier,
    stats: StatsReport = Analytics.compute(emptyList()),
    records: List<RunRecord> = emptyList(),
    onOpenExercise: (String) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Text("Progressi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp))
        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, name) in TABS.withIndex()) {
                val on = i == tab
                Text(
                    name, color = if (on) Color(0xFF0A0F1E) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) FlowColors.Accent else FlowColors.Surface).clickable { tab = i }.padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statsOverview(stats, progress.routineDays, progress.routineStreak)
                item { MinutesChart(progress) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            1 -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(6.dp)); CalendarTab(records, progress.dayFlags) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            2 -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statsDetails(stats, onOpenExercise)
                songRunsSection(progress, songs, onOpenSong)
                item { Spacer(Modifier.height(24.dp)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ChallengesCard(progress) }
                item {
                    val done = progress.achievements.count { it.unlocked }
                    Header("Traguardi · $done/${progress.achievements.size}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (a in progress.achievements) Badge(a)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** Minuti degli ultimi 14 giorni. */
@Composable
private fun MinutesChart(progress: ProgressSnapshot) {
    val accent = FlowColors.Accent
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

/** Le sfide di oggi (routine, canzone, prova) e le serie: tutto in un colpo d'occhio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengesCard(progress: ProgressSnapshot) {
    Header("Sfide di oggi")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChallengeRow("Routine quotidiana", progress.routineCompletedToday, "${progress.routineDoneToday.size}${if (progress.routineTotalToday > 0) "/${progress.routineTotalToday}" else ""} esercizi fatti oggi")
        ChallengeRow("Canzone del giorno", progress.dailySongDone, if (progress.dailySongId == null) "in arrivo" else "")
        ChallengeRow("Prova del giorno", progress.dailyTrialDone, if (progress.dailyTrialId == null) "in arrivo" else "")
    }
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallStat("Giorni di fila", progress.streak.toString())
        SmallStat("Routine di fila", progress.routineStreak.toString())
        SmallStat("Routine completate", progress.routineDays.toString())
        SmallStat("Giorni con una sfida", progress.dayFlags.values.count { it.songDone || it.trialDone }.toString())
    }
}

@Composable
private fun ChallengeRow(title: String, done: Boolean, note: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (done) FlowColors.Accent else FlowColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            if (done) Text("✓", color = Color(0xFF0A0F1E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (!done && note.isNotEmpty()) Text(note, color = FlowColors.Muted, fontSize = 13.sp)
        if (done) Text("fatta", color = FlowColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column(Modifier.width(180.dp).clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FlowColors.Muted, fontSize = 13.sp)
    }
}

/** Brani suonati a tempo, con le ultime precisioni e le battute da ripassare. */
private fun androidx.compose.foundation.lazy.LazyListScope.songRunsSection(progress: ProgressSnapshot, songs: List<SongEntry>, onOpenSong: (SongEntry) -> Unit) {
    val rows = progress.songRuns.entries.mapNotNull { (id, runs) -> songs.firstOrNull { it.id == id }?.let { it to runs } }.sortedByDescending { it.second.size }
    if (rows.isEmpty()) return
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
            Sparkline(runs, FlowColors.Accent)
        }
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
        Modifier.width(250.dp).height(112.dp).clip(RoundedCornerShape(14.dp)).background(if (on) FlowColors.SurfaceHigh else FlowColors.Surface).padding(12.dp),
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
