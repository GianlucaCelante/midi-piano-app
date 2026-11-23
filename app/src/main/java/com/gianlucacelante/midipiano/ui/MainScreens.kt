package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gianlucacelante.midipiano.ProgressSnapshot
import com.gianlucacelante.midipiano.Settings
import com.gianlucacelante.midipiano.learn.Course
import com.gianlucacelante.midipiano.learn.Courses
import com.gianlucacelante.midipiano.learn.Covers
import com.gianlucacelante.midipiano.learn.Simplifier
import com.gianlucacelante.midipiano.learn.ExerciseSlide
import com.gianlucacelante.midipiano.learn.Lesson
import com.gianlucacelante.midipiano.learn.TextSlide
import com.gianlucacelante.midipiano.midi.BleMidiDevice
import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.SongEntry
import kotlin.math.cos
import kotlin.math.sin

enum class Tab(val label: String) { HOME("Home"), SONGS("Brani"), SEARCH("Cerca"), COURSES("Corsi"), PROGRESS("Progressi"), PROFILE("Profilo") }

/** Tavolozza dell'app: blu notte con accento arancione (la nostra interpretazione dello stile). */
object FlowColors {
    val Navy = Color(0xFF0A0F1E)
    val Rail = Color(0xFF0D1426)
    val Surface = Color(0xFF141B2D)
    val SurfaceHigh = Color(0xFF1A2338)
    val Muted = Color(0xFF6C8196)
    val Accent = Color(0xFFF07F00)
    val Green = Color(0xFF4CE600)
    val Yellow = Color(0xFFFFD60A)
    val Red = Color(0xFFE5182C)
    val DialogBg = Color(0xFFF8F9FB)
    val DialogText = Color(0xFF111827)
}

private val Accent = FlowColors.Accent

// ---------- icone disegnate ----------

enum class NavIcon { HOME, NOTE, SEARCH, CAP, CHART, GEAR, BACK }

@Composable
fun NavGlyph(icon: NavIcon, color: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        when (icon) {
            NavIcon.HOME -> drawPath(
                Path().apply {
                    moveTo(w * 0.5f, h * 0.14f); lineTo(w * 0.92f, h * 0.5f); lineTo(w * 0.8f, h * 0.5f)
                    lineTo(w * 0.8f, h * 0.86f); lineTo(w * 0.2f, h * 0.86f); lineTo(w * 0.2f, h * 0.5f)
                    lineTo(w * 0.08f, h * 0.5f); close()
                },
                color, style = stroke,
            )
            NavIcon.NOTE -> {
                drawCircle(color, w * 0.14f, Offset(w * 0.3f, h * 0.76f), style = stroke)
                drawLine(color, Offset(w * 0.44f, h * 0.76f), Offset(w * 0.44f, h * 0.18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.18f), Offset(w * 0.82f, h * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.3f), Offset(w * 0.82f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, w * 0.14f, Offset(w * 0.68f, h * 0.66f), style = stroke)
            }
            NavIcon.CHART -> {
                drawLine(color, Offset(w * 0.2f, h * 0.84f), Offset(w * 0.2f, h * 0.5f), strokeWidth = stroke.width * 1.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke.width * 1.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.8f, h * 0.84f), Offset(w * 0.8f, h * 0.4f), strokeWidth = stroke.width * 1.6f, cap = StrokeCap.Round)
            }
            NavIcon.SEARCH -> {
                drawCircle(color, w * 0.27f, Offset(w * 0.42f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.9f, h * 0.9f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            NavIcon.CAP -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.95f, h * 0.42f); lineTo(w * 0.5f, h * 0.64f); lineTo(w * 0.05f, h * 0.42f); close()
                    },
                    color, style = stroke,
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.25f, h * 0.55f); lineTo(w * 0.25f, h * 0.74f)
                        quadraticBezierTo(w * 0.5f, h * 0.92f, w * 0.75f, h * 0.74f); lineTo(w * 0.75f, h * 0.55f)
                    },
                    color, style = stroke,
                )
            }
            NavIcon.BACK -> {
                drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.34f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.34f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.18f), Offset(w * 0.86f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            NavIcon.GEAR -> {
                drawCircle(color, w * 0.17f, Offset(w * 0.5f, h * 0.5f), style = stroke)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    drawLine(
                        color,
                        Offset(w * 0.5f + (w * 0.3f * cos(a)).toFloat(), h * 0.5f + (h * 0.3f * sin(a)).toFloat()),
                        Offset(w * 0.5f + (w * 0.43f * cos(a)).toFloat(), h * 0.5f + (h * 0.43f * sin(a)).toFloat()),
                        strokeWidth = stroke.width * 1.4f, cap = StrokeCap.Round,
                    )
                }
                drawCircle(color, w * 0.31f, Offset(w * 0.5f, h * 0.5f), style = stroke)
            }
        }
    }
}

// ---------- componenti comuni ----------

/** Foto di copertina (pubblico dominio / CC0, in assets/covers) con cache; decodificate fuori dal thread principale. */
private object CoverPhotos {
    private var names: List<String>? = null
    private var keywordMap: Map<String, String>? = null
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(24) {}

    fun names(context: android.content.Context): List<String> =
        names ?: (context.assets.list("covers") ?: emptyArray()).filter { it.endsWith(".jpg") }.sorted().also { names = it }

    fun keywords(context: android.content.Context): Map<String, String> =
        keywordMap ?: runCatching {
            val o = org.json.JSONObject(context.assets.open("covers/map.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap()).also { keywordMap = it }

    fun load(context: android.content.Context, name: String): android.graphics.Bitmap? {
        cache.get(name)?.let { return it }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = try { context.assets.open("covers/$name").use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } } catch (e: Exception) { null }
        if (bmp != null) cache.put(name, bmp)
        return bmp
    }
}

/** Copertina: una foto libera scelta in modo stabile per brano; se manca, sfumatura con le iniziali. */
@Composable
fun CoverBox(id: String, title: String, size: Dp, modifier: Modifier = Modifier, radius: Dp = 16.dp, hint: String = "", song: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (c1, c2) = remember(id) { Covers.colors(id) }
    var photo by remember(id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val name = Covers.pickPhoto(id, "$title $hint", CoverPhotos.names(context), CoverPhotos.keywords(context), allowGeneric = !song)
            if (name == null) null else CoverPhotos.load(context, name)?.asImageBitmap()
        }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(Color(c1), Color(c2)))),
        contentAlignment = Alignment.Center,
    ) {
        val img = photo
        if (img != null) {
            androidx.compose.foundation.Image(img, contentDescription = title, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))))
        } else {
            Text(Covers.initials(title), color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
        }
    }
}

/** Versioni disponibili per brano (1 facile, 2 intermedia, 3 originale), calcolate all'avvio. */
val LocalSongVersions = androidx.compose.runtime.compositionLocalOf<Map<String, List<Int>>> { emptyMap() }

/** Brani con gli accordi: id -> true se è una griglia di accordi. */
val LocalChordSongs = androidx.compose.runtime.compositionLocalOf<Map<String, Boolean>> { emptyMap() }

private fun versionColor(version: Int) = when (version) { 1 -> FlowColors.Green; 2 -> FlowColors.Yellow; else -> FlowColors.Red }

/** Un pallino solo, colorato dalla difficoltà stimata (1-2 verde, 3 giallo, 4-5 rosso): per i brani con una sola versione. */
@Composable
fun LevelDots(level: Int) {
    val c = when { level <= 2 -> FlowColors.Green; level == 3 -> FlowColors.Yellow; else -> FlowColors.Red }
    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
}

/** Un pallino per ogni versione del brano (verde facile, giallo intermedia, rosso originale); con una sola versione, la difficoltà stimata. */
@Composable
fun SongDots(songId: String, estimatedLevel: Int) {
    val versions = LocalSongVersions.current[songId]
    if (versions == null || versions.size <= 1) { LevelDots(estimatedLevel); return }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        versions.forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(versionColor(it))) }
    }
}

@Composable
fun StarsRow(stars: Int, max: Int = 3) {
    Text("★".repeat(stars) + "☆".repeat(max - stars), color = if (stars > 0) Accent else Color(0xFF55657A), fontSize = 16.sp)
}

/** Pillola scura con la percentuale e una piccola barra arancione. */
@Composable
fun ProgressPill(percent: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0A0F1E)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(34.dp).height(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3A4458))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(Accent))
        }
        Text("$percent%", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (onSeeAll != null) Text("Visualizza tutto", color = Accent, fontSize = 18.sp, modifier = Modifier.clickable { onSeeAll() })
    }
}

/** Scheda con copertina, pallini di livello, titolo e sottotitolo (come le righe di brani). */
@Composable
fun CoverCard(
    id: String, title: String, subtitle: String?, level: Int, modifier: Modifier = Modifier,
    size: Dp = 190.dp, progress: Int? = null, song: Boolean = false, onClick: () -> Unit,
) {
    Column(modifier.width(size).clickable { onClick() }) {
        Box {
            CoverBox(id, title, size, hint = subtitle ?: "", song = song)
            if (progress != null) ProgressPill(progress, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        SongDots(id, level)
        Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (subtitle != null) Text(subtitle, color = FlowColors.Muted, fontSize = 15.sp, maxLines = 1)
    }
}

// ---------- barra di navigazione verticale ----------

@Composable
fun MainNavRail(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(88.dp).fillMaxHeight().background(FlowColors.Rail).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(Tab.HOME to NavIcon.HOME, Tab.SONGS to NavIcon.NOTE, Tab.SEARCH to NavIcon.SEARCH, Tab.COURSES to NavIcon.CAP, Tab.PROGRESS to NavIcon.CHART).forEach { (tab, icon) ->
            val on = tab == selected
            Column(
                Modifier.padding(vertical = 14.dp).clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NavGlyph(icon, if (on) Accent else FlowColors.Muted)
                Text(tab.label, color = if (on) Accent else FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clickable { onSelect(Tab.PROFILE) }.padding(12.dp)) {
            NavGlyph(NavIcon.GEAR, if (selected == Tab.PROFILE) Accent else FlowColors.Muted)
        }
        Text("MIDI Piano", color = Accent, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(
    progress: ProgressSnapshot,
    courses: List<Course>,
    songs: List<SongEntry>,
    levels: Map<String, Int>,
    onOpenExercise: (ExerciseSlide) -> Unit,
    onOpenSong: (SongEntry) -> Unit,
    onSeeAllSongs: () -> Unit,
    modifier: Modifier = Modifier,
    dailyGoalMinutes: Int = 0,
) {
    val resume = Courses.resume(courses, progress.lastExerciseId) { progress.stars[it] ?: 0 }
    val todo = courses.flatMap { it.exercises }.filter { (progress.stars[it.id] ?: 0) == 0 }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Ciao!", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Che cosa ti va di suonare?", fontSize = 26.sp, color = FlowColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                StreakPill(progress.streak)
            }
        }
        if (dailyGoalMinutes > 0) item { DailyGoalBar(progress.todaySeconds, dailyGoalMinutes) }
        // Riprendi la lezione dal punto in cui ti eri fermato
        if (resume != null) item {
            SectionHeader("Riprendi la lezione")
            Row(
                Modifier.width(560.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onOpenExercise(resume.exercise) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CoverBox(resume.exercise.id, resume.exercise.title, 96.dp, radius = 12.dp, hint = resume.course.title)
                Column(Modifier.weight(1f)) {
                    Text(resume.course.title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                    Text(resume.lesson.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Prossimo: ${resume.exercise.title}", color = FlowColors.Muted, fontSize = 14.sp, maxLines = 1)
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh)) {
                        Box(Modifier.fillMaxWidth(resume.done.toFloat() / resume.total.coerceAtLeast(1)).fillMaxSize().clip(RoundedCornerShape(50)).background(Accent))
                    }
                    Text("${resume.done}/${resume.total} esercizi della lezione", color = FlowColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Riprendi  ›", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Sfide di oggi: la canzone del giorno e una prova (esercizio) per variare
        val dailySong = progress.dailySongId?.let { id -> songs.firstOrNull { it.id == id } }
        val dailyTrial = progress.dailyTrialId?.let { id -> courses.flatMap { it.exercises }.firstOrNull { it.id == id } }
        if (dailySong != null || dailyTrial != null) {
            item {
                val done = listOf(progress.dailySongDone to (dailySong != null), progress.dailyTrialDone to (dailyTrial != null)).count { it.first && it.second }
                val total = listOf(dailySong, dailyTrial).count { it != null }
                SectionHeader("Sfide di oggi  ·  $done/$total")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (dailySong != null) DailyCard("CANZONE DEL GIORNO", dailySong.id, dailySong.title, dailySong.composer ?: dailySong.origin, progress.dailySongDone, song = true) { onOpenSong(dailySong) }
                    if (dailyTrial != null) DailyCard(
                        if ((progress.stars[dailyTrial.id] ?: 0) in 1..2) "PROVA DEL GIORNO · RIPASSO" else "PROVA DEL GIORNO",
                        dailyTrial.id, dailyTrial.title, "${dailyTrial.steps.size} passi", progress.dailyTrialDone,
                    ) { onOpenExercise(dailyTrial) }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val next = todo.firstOrNull()
                // con "Riprendi la lezione" già in alto, il prossimo esercizio sarebbe lo stesso: la carta serve solo senza storia
                if (next != null && resume == null) {
                    HeroCard(next.id, next.title, "Prossimo esercizio dei corsi", "Suona") { onOpenExercise(next) }
                }
                // "Continua da dove eri": l'ultimo brano aperto; altrimenti il più facile
                val last = progress.lastSongId?.let { id -> songs.firstOrNull { it.id == id } }
                val easy = last ?: songs.minByOrNull { levels[it.id] ?: 3 }
                if (easy != null) HeroCard(easy.id, easy.title, if (last != null) "Riprendi da dove eri" else (easy.composer ?: easy.origin), if (last != null) "Riprendi" else "Apri", song = true) { onOpenSong(easy) }
            }
        }
        if (todo.size > 1) {
            item { SectionHeader("Pratica di oggi") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(todo.drop(1).take(6), key = { it.id }) { ex ->
                        CoverCard(ex.id, ex.title, "${ex.steps.size} passi", 2, onClick = { onOpenExercise(ex) })
                    }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item { SectionHeader("Brani per te", onSeeAllSongs) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    items(songs.sortedBy { levels[it.id] ?: 3 }.take(10), key = { it.id }) { s ->
                        CoverCard(s.id, s.title, s.composer, levels[s.id] ?: 1, song = true, onClick = { onOpenSong(s) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(label: String, id: String, title: String, subtitle: String, done: Boolean, song: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.width(420.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onClick() }
            .border(2.dp, if (done) Accent else Color.Transparent, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverBox(id, title, 84.dp, radius = 12.dp, hint = subtitle, song = song)
        Column(Modifier.weight(1f)) {
            Text(label, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(if (done) "✓ Fatto oggi" else subtitle, color = if (done) Accent else FlowColors.Muted, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DailyGoalBar(todaySeconds: Long, goalMinutes: Int) {
    val goalSec = goalMinutes * 60L
    val done = todaySeconds >= goalSec
    Column(Modifier.padding(top = 16.dp).fillMaxWidth(0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "Obiettivo di oggi raggiunto!" else "Oggi: ${todaySeconds / 60} / $goalMinutes min", color = if (done) Accent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(FlowColors.Surface)) {
            Box(Modifier.fillMaxWidth((todaySeconds.toFloat() / goalSec).coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(50)).background(Accent))
        }
    }
}

@Composable
private fun StreakPill(streak: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(FlowColors.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (streak > 0) Accent else FlowColors.Muted))
        Text("$streak ${if (streak == 1) "giorno" else "giorni"} di fila", color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun HeroCard(id: String, title: String, subtitle: String, action: String, song: Boolean = false, onClick: () -> Unit) {
    val (c1, c2) = remember(id) { Covers.colors(id) }
    Row(
        Modifier.width(420.dp).height(210.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(c2), FlowColors.Surface))).clickable { onClick() }.padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverBox(id, title, 120.dp, radius = 12.dp, song = song)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------- Brani ----------

@Composable
fun SongsScreen(
    songs: List<SongEntry>,
    levels: Map<String, Int>,
    favorites: Set<String>,
    error: String?,
    onDismissError: () -> Unit,
    onOpen: (SongEntry) -> Unit,
    modifier: Modifier = Modifier,
    onImport: () -> Unit = {},
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Brani", Modifier.weight(1f), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedButton(onClick = onImport) { Text("Importa un brano (MIDI / MusicXML)") }
            }
        }
        if (error != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.errorContainer).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onDismissError) { Text("OK") }
                }
            }
        }
        val rows = listOf(
            "I miei brani" to songs.filter { it.file.startsWith("user:") },
            "Consigliati per te" to songs.filter { !it.file.startsWith("user:") }.sortedBy { levels[it.id] ?: 3 },
            "Facili" to songs.filter { (levels[it.id] ?: 3) <= 2 },
            "Preferiti" to songs.filter { it.id in favorites },
            "Tutti i brani" to songs,
        ).filter { it.second.isNotEmpty() }
        rows.forEach { (title, list) ->
            item { SectionHeader(title) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(list, key = { title + it.id }) { s -> CoverCard(s.id, s.title, s.composer, levels[s.id] ?: 1, size = 210.dp, song = true, onClick = { onOpen(s) }) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------- Cerca ----------

@Composable
fun SearchScreen(songs: List<SongEntry>, levels: Map<String, Int>, favorites: Set<String>, onOpen: (SongEntry) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("tutti") }
    val shown = songs.filter { s ->
        (query.isBlank() || s.title.contains(query, true) || (s.composer ?: "").contains(query, true)) &&
            when (filter) {
                "facili" -> (levels[s.id] ?: 3) <= 2
                "preferiti" -> s.id in favorites
                else -> true
            }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Text("Cerca", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Titolo o autore") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("tutti" to "Tutti", "facili" to "Facili", "preferiti" to "Preferiti").forEach { (k, label) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
            }
        }
        if (shown.isEmpty()) Text("Nessun brano trovato.", color = FlowColors.Muted, modifier = Modifier.padding(top = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).clickable { onOpen(s) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CoverBox(s.id, s.title, 64.dp, radius = 10.dp, hint = s.composer ?: "", song = true)
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 18.sp, maxLines = 1)
                        if (s.composer != null) Text(s.composer, color = FlowColors.Muted, fontSize = 15.sp, maxLines = 1)
                    }
                    SongDots(s.id, levels[s.id] ?: 1)
                    if (s.id in favorites) HeartIcon(true, size = 24.dp)
                }
            }
        }
    }
}

/** Scheda del brano: copertina, titolo, scelta del livello e "Impara brano". */
@Composable
fun SongDetailDialog(
    song: SongEntry, level: Int, favorite: Boolean,
    onToggleFavorite: () -> Unit, onDismiss: () -> Unit, onLearn: (StudyMode, Double, Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit,
    onPreview: (Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit = { _, _ -> }, onStopPreview: () -> Unit = {},
    preview: com.gianlucacelante.midipiano.PreviewState = com.gianlucacelante.midipiano.PreviewState(), onTogglePreview: (Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit = { _, _ -> },
    songsDone: Set<String> = emptySet(), unlockAll: Boolean = true,
) {
    val chordInfo = LocalChordSongs.current[song.id] // null: nessun accordo; true: griglia di accordi; false: sigle sopra lo spartito
    var style by remember(song.id) { mutableStateOf<com.gianlucacelante.midipiano.song.SongRepository.Style?>(if (chordInfo == null) null else if (chordInfo) com.gianlucacelante.midipiano.song.SongRepository.Style.CHORDS else com.gianlucacelante.midipiano.song.SongRepository.Style.NOTES) }
    // con gli accordi interi non esistono versioni più facili: si offre solo l'originale
    val versions = if (style == com.gianlucacelante.midipiano.song.SongRepository.Style.CHORDS || chordInfo == true) listOf(Simplifier.ORIGINAL) else (LocalSongVersions.current[song.id]?.takeIf { it.isNotEmpty() } ?: listOf(Simplifier.ORIGINAL))
    var chosen by remember(song.id, style) { mutableStateOf(versions.first()) } // si parte dalla più facile (Principiante), come in Flowkey
    // anteprima: parte da sola e si cambia con la versione scelta; si ferma uscendo dalla scheda
    androidx.compose.runtime.LaunchedEffect(song.id, chosen, style) { onPreview(chosen, style) }
    androidx.compose.runtime.DisposableEffect(song.id) { onDispose { onStopPreview() } }
    val (c1, c2) = remember(song.id) { Covers.colors(song.id) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(520.dp).clip(RoundedCornerShape(24.dp)).background(FlowColors.Surface)) {
            Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(Color(c2), FlowColors.Surface)))) {
                IconButton(Modifier.align(Alignment.TopStart).padding(16.dp), onToggleFavorite) { HeartIcon(favorite) }
                IconButton(Modifier.align(Alignment.TopEnd).padding(16.dp), onDismiss) { CrossIcon() }
                CoverBox(song.id, song.title, 170.dp, Modifier.align(Alignment.Center), radius = 14.dp, hint = song.composer ?: "", song = true)
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song.title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, maxLines = 2, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (song.composer != null) Text(song.composer, color = FlowColors.Muted, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                if (chordInfo != null) {
                    Text("Come vuoi suonarlo?", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(com.gianlucacelante.midipiano.song.SongRepository.Style.CHORDS to "Accordi", com.gianlucacelante.midipiano.song.SongRepository.Style.NOTES to (if (chordInfo) "Note singole (arpeggi)" else "Note dello spartito")).forEach { (st, label) ->
                            val on = style == st
                            Text(label, color = if (on) Color(0xFF0A0F1E) else Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Accent else Color.Transparent).border(2.dp, Accent, RoundedCornerShape(50))
                                    .clickable { style = st }.padding(horizontal = 18.dp, vertical = 8.dp))
                        }
                    }
                }
                Text("Tocca il cerchio scelto per mettere in pausa o riascoltare l'anteprima", color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                Text("Seleziona il tuo livello", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                var lockedHint by remember(song.id) { mutableStateOf<String?>(null) }
                Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    versions.forEachIndexed { idx, v ->
                        // si sblocca suonando fino in fondo la versione precedente (o dalle impostazioni)
                        val prev = versions.getOrNull(idx - 1)
                        val locked = !unlockAll && prev != null && "${song.id}:$prev" !in songsDone
                        val color = if (versions.size == 1) { when { level <= 2 -> FlowColors.Green; level == 3 -> FlowColors.Yellow; else -> FlowColors.Red } } else versionColor(v)
                        val label = when { versions.size == 1 -> "Originale"; v == 1 -> "Principiante"; v == 2 -> "Intermedio"; else -> "Avanzato" }
                        LevelCircle(label, color, selected = chosen == v, playing = preview.playing, progress = preview.progress, locked = locked) {
                            if (locked) lockedHint = "Suona fino in fondo la versione ${levelNameOf(prev!!)} per sbloccare questa."
                            else { lockedHint = null; if (chosen == v) onTogglePreview(v, style) else chosen = v } // il cerchio scelto è play/pausa dell'anteprima
                        }
                    }
                }
                // che cosa cambia in questa versione
                val note = lockedHint ?: versionExplanation(chosen, versions.size)
                Text(note, color = if (lockedHint != null) Accent else FlowColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp).widthIn(max = 420.dp))
                Button(
                    onClick = { onLearn(StudyMode.WAIT, 1.0, chosen, style); onDismiss() }, // si comincia in "Aspetta": il modo si cambia dall'ingranaggio
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.height(52.dp),
                ) { Text("Impara brano", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp)) }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SongDots(song.id, level)
                    Text("${song.origin} · ${song.license}", color = FlowColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun levelNameOf(v: Int) = when (v) { 1 -> "Principiante"; 2 -> "Intermedio"; else -> "Avanzato" }

private fun versionExplanation(v: Int, count: Int): String = when {
    count == 1 -> "Il brano com'è scritto."
    v == 1 -> "Una nota alla volta con la destra, sulle pulsazioni; a sinistra una nota per battuta. Le battute già facili restano come nell'originale."
    v == 2 -> "La melodia completa a destra, senza abbellimenti; a sinistra le note sulle pulsazioni, al massimo due insieme."
    else -> "Il brano com'è scritto, con tutte le note."
}

@Composable
private fun LevelCircle(label: String, color: Color, selected: Boolean, playing: Boolean, progress: Float, locked: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(if (selected) color else Color.Transparent).border(2.dp, color, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 20.sp)
                Text(label, color = color.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        } else if (!selected) {
            Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        } else {
            val ink = Color(0xFF0A0F1E)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                if (progress > 0f) drawArc(ink.copy(alpha = 0.55f), -90f, 360f * progress, false, Offset(w * 0.08f, w * 0.08f), Size(w * 0.84f, w * 0.84f), style = Stroke(width = w * 0.05f, cap = StrokeCap.Round))
                if (playing) { // pausa
                    drawRect(ink, Offset(w * 0.38f, w * 0.34f), Size(w * 0.09f, w * 0.32f))
                    drawRect(ink, Offset(w * 0.53f, w * 0.34f), Size(w * 0.09f, w * 0.32f))
                } else { // play
                    drawPath(Path().apply { moveTo(w * 0.40f, w * 0.32f); lineTo(w * 0.68f, w * 0.5f); lineTo(w * 0.40f, w * 0.68f); close() }, ink)
                }
            }
        }
    }
}

/** Pulsante tondo scuro per le icone sopra la copertina. */
@Composable
private fun IconButton(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.size(46.dp).clip(CircleShape).background(Color(0x99000000)).clickable { onClick() }, contentAlignment = Alignment.Center) { content() }
}

/** Cuore: pieno (arancio) se è tra i preferiti, solo contorno (bianco) altrimenti. */
@Composable
private fun HeartIcon(filled: Boolean, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.5f, w * 0.88f)
            cubicTo(w * 0.05f, w * 0.55f, w * 0.05f, w * 0.15f, w * 0.30f, w * 0.15f)
            cubicTo(w * 0.42f, w * 0.15f, w * 0.5f, w * 0.25f, w * 0.5f, w * 0.30f)
            cubicTo(w * 0.5f, w * 0.25f, w * 0.58f, w * 0.15f, w * 0.70f, w * 0.15f)
            cubicTo(w * 0.95f, w * 0.15f, w * 0.95f, w * 0.55f, w * 0.5f, w * 0.88f)
            close()
        }
        if (filled) drawPath(path, Accent) else drawPath(path, Color.White, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

/** Croce per chiudere. */
@Composable
private fun CrossIcon() {
    Canvas(Modifier.size(22.dp)) {
        val w = this.size.width
        val st = w * 0.12f
        drawLine(Color.White, Offset(w * 0.15f, w * 0.15f), Offset(w * 0.85f, w * 0.85f), strokeWidth = st, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.85f, w * 0.15f), Offset(w * 0.15f, w * 0.85f), strokeWidth = st, cap = StrokeCap.Round)
    }
}

// ---------- Corsi ----------

@Composable
fun CoursesScreen(courses: List<Course>, progress: ProgressSnapshot, onOpenCourse: (Course) -> Unit, onSight: () -> Unit, onChords: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item { Text("Corsi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp)) }
        item { TrainingCards(progress, onSight, onChords) }
        item { SectionHeader("Corsi per principianti") }
        if (courses.isEmpty()) item { Text("Caricamento dei corsi…", color = FlowColors.Muted) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(courses, key = { it.id }) { c ->
                    val done = c.exercises.count { (progress.stars[it.id] ?: 0) > 0 }
                    val percent = if (c.exercises.isEmpty()) 0 else done * 100 / c.exercises.size
                    Column(
                        Modifier.width(360.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onOpenCourse(c) }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CoverBox(c.id, c.title, 328.dp, Modifier.height(170.dp).fillMaxWidth().width(328.dp), radius = 12.dp)
                        Text(c.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (c.description.isNotBlank()) Text(c.description, color = FlowColors.Muted, fontSize = 15.sp, maxLines = 3)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(FlowColors.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(when (c.level) { 1 -> FlowColors.Green; 2 -> FlowColors.Yellow; else -> FlowColors.Red }))
                                Text(when (c.level) { 1 -> "Principiante"; 2, 3 -> "Intermedio"; else -> "Avanzato" }, color = Color.White, fontSize = 14.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            ProgressPill(percent)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun CourseScreen(course: Course, progress: ProgressSnapshot, onBack: () -> Unit, onOpenLesson: (Lesson) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        TextButton(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("‹ Corsi", color = Accent, fontSize = 18.sp) }
        Text(course.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            items(course.lessons, key = { it.id }) { l ->
                val ex = l.exercises
                val done = ex.count { (progress.stars[it.id] ?: 0) > 0 }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).clickable { onOpenLesson(l) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (l.description.isNotBlank()) Text(l.description, color = FlowColors.Muted, fontSize = 15.sp)
                    }
                    ProgressPill(if (ex.isEmpty()) 0 else done * 100 / ex.size)
                }
            }
        }
    }
}

@Composable
fun LessonScreen(lesson: Lesson, progress: ProgressSnapshot, onBack: () -> Unit, onOpenExercise: (ExerciseSlide) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        TextButton(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("‹ Lezioni", color = Accent, fontSize = 18.sp) }
        Text(lesson.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            items(lesson.slides, key = { it.id }) { slide ->
                when (slide) {
                    is TextSlide -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(slide.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(slide.body, color = Color(0xFFC4CEDA), fontSize = 16.sp)
                    }
                    is ExerciseSlide -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FlowColors.SurfaceHigh).clickable { onOpenExercise(slide) }.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(slide.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            if (slide.text.isNotBlank()) Text(slide.text, color = FlowColors.Muted, fontSize = 15.sp)
                            Text("${slide.steps.size} passi", color = FlowColors.Muted, fontSize = 13.sp)
                        }
                        StarsRow(progress.stars[slide.id] ?: 0)
                        Button(onClick = { onOpenExercise(slide) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Suona") }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------- Profilo ----------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    progress: ProgressSnapshot,
    devices: List<String>,
    midiSupported: Boolean,
    bleScanning: Boolean,
    bleFound: List<BleMidiDevice>,
    settings: Settings,
    onBleScan: () -> Unit,
    onBleConnect: (BleMidiDevice) -> Unit,
    onSettings: (Settings) -> Unit,
    onKeyboardCheck: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Impostazioni", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard("Serie", "${progress.streak} ${if (progress.streak == 1) "giorno" else "giorni"}")
                StatCard("Tempo totale", if (progress.totalSeconds < 60) "${progress.totalSeconds} s" else "${progress.totalSeconds / 60} min")
                StatCard("Esercizi", progress.exercisesDone.toString())
                StatCard("Brani preferiti", progress.favorites.size.toString())
            }
        }
        item {
            SectionHeader("Tastiera MIDI")
            Text(
                when {
                    !midiSupported -> "Questo dispositivo non supporta MIDI."
                    devices.isEmpty() -> "Nessuna tastiera collegata. Usa un cavo USB (OTG) oppure il Bluetooth."
                    else -> "Collegato: ${devices.joinToString()}"
                },
                color = Color(0xFFC4CEDA),
            )
            if (midiSupported) BluetoothSection(bleScanning, bleFound, onBleScan, onBleConnect)
            OutlinedButton(onClick = onKeyboardCheck, modifier = Modifier.padding(top = 8.dp)) { Text("Prova guidata della tastiera (note, forza, pedale)") }
        }
        item {
            SectionHeader("Promemoria giornaliero")
            Text("Una notifica all'ora scelta, solo se quel giorno non hai ancora suonato.", color = Color(0xFFC4CEDA), modifier = Modifier.padding(bottom = 8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "Spento", 9 to "9:00", 12 to "12:00", 17 to "17:00", 19 to "19:00", 21 to "21:00").forEach { (h, label) ->
                    FilterChip(selected = settings.reminderHour == h, onClick = { onSettings(settings.copy(reminderHour = h)) }, label = { Text(label) })
                }
            }
        }
        item {
            SectionHeader("Obiettivo giornaliero")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Nessuno", 5 to "5 min", 10 to "10 min", 15 to "15 min", 20 to "20 min", 30 to "30 min").forEach { (m, label) ->
                    FilterChip(selected = settings.dailyGoalMinutes == m, onClick = { onSettings(settings.copy(dailyGoalMinutes = m)) }, label = { Text(label) })
                }
            }
        }
        item {
            SectionHeader("Preferenze")
            OutlinedButton(onClick = { showSettings = true }) { Text("Suono, metronomo, conto alla rovescia…") }
        }
        item {
            SectionHeader("Registro di utilizzo")
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Text("L'app salva un registro di ciò che succede (tastiera collegata, note, errori) per aiutare a trovare i problemi. Resta sul tablet.", color = Color(0xFFC4CEDA))
            OutlinedButton(onClick = { com.gianlucacelante.midipiano.shareLog(ctx) }, modifier = Modifier.padding(top = 8.dp)) { Text("Condividi il registro") }
        }
    }
    if (showSettings) SettingsDialog(settings, onChange = onSettings, onDismiss = { showSettings = false })
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(value, color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FlowColors.Muted, fontSize = 14.sp)
    }
}
