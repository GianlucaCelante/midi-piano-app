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

enum class Tab(val label: String) { HOME("Home"), SONGS("Brani"), SEARCH("Cerca"), COURSES("Corsi"), EXERCISES("Esercizi"), PROGRESS("Progressi"), PROFILE("Profilo") }

/** Tavolozza dell'app: blu notte con accento arancione (la nostra interpretazione dello stile). */
/** Arrangiamenti disponibili per i brani che ne hanno più di uno (id del brano mostrato -> arrangiamenti). */
val LocalSongVariants = androidx.compose.runtime.compositionLocalOf<Map<String, List<com.gianlucacelante.midipiano.song.Variant>>> { emptyMap() }

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

enum class NavIcon { HOME, NOTE, SEARCH, CAP, TARGET, CHART, GEAR, BACK }

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
            NavIcon.TARGET -> {
                drawCircle(color, w * 0.4f, Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, w * 0.22f, Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, w * 0.05f, Offset(w * 0.5f, h * 0.5f))
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
            // brano importato con la sua foto
            val own = java.io.File(java.io.File(context.filesDir, "user_covers"), "$id.jpg")
            if (own.exists()) return@withContext android.graphics.BitmapFactory.decodeFile(own.path)?.asImageBitmap()
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

/** Colore di un arrangiamento dal suo livello: 0-1 verde, 2 giallo, 3 arancio (originale), 4 rosso (avanzato). */
fun rankColor(rank: Int): Color = when { rank <= 1 -> FlowColors.Green; rank == 2 -> FlowColors.Yellow; rank == 3 -> Accent; else -> FlowColors.Red }

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
    // più arrangiamenti dello stesso brano: un pallino per arrangiamento, gli stessi della scheda del brano (i livelli SONO gli arrangiamenti)
    val variants = LocalSongVariants.current[songId]
    if (variants != null && variants.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            variants.forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(rankColor(it.rank))) }
        }
        return
    }
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
        Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (subtitle != null) Text(subtitle, color = FlowColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

// ---------- barra di navigazione verticale ----------

@Composable
fun MainNavRail(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(88.dp).fillMaxHeight().background(FlowColors.Rail).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(Tab.HOME to NavIcon.HOME, Tab.SONGS to NavIcon.NOTE, Tab.SEARCH to NavIcon.SEARCH, Tab.COURSES to NavIcon.CAP, Tab.EXERCISES to NavIcon.TARGET, Tab.PROGRESS to NavIcon.CHART).forEach { (tab, icon) ->
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    routines: List<Course> = emptyList(),
    routineId: String = "",
    routineSize: Int = 0,
    onStartRoutine: () -> Unit = {},
    onRoutineSettings: (String, Int) -> Unit = { _, _ -> },
) {
    var routineDialog by remember { mutableStateOf(false) }
    val routine = routines.firstOrNull { it.id == routineId } ?: routines.firstOrNull()
    if (routineDialog && routine != null) RoutineDialog(routines, routine.id, routineSize, onChange = onRoutineSettings, onDismiss = { routineDialog = false })
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
        // OGGI: la routine quotidiana e le due sfide del giorno, in un solo blocco con il conto di quante sono fatte
        val dailySong = progress.dailySongId?.let { id -> songs.firstOrNull { it.id == id } }
        val dailyTrial = progress.dailyTrialId?.let { id -> courses.flatMap { it.exercises }.firstOrNull { it.id == id } }
        val todayTotal = (if (routine != null) 1 else 0) + (if (dailySong != null) 1 else 0) + (if (dailyTrial != null) 1 else 0)
        val todayDone = (if (routine != null && progress.routineCompletedToday) 1 else 0) + (if (dailySong != null && progress.dailySongDone) 1 else 0) + (if (dailyTrial != null && progress.dailyTrialDone) 1 else 0)
        if (todayTotal > 0) {
            item { SectionHeader("Oggi  ·  $todayDone/$todayTotal") }
            if (routine != null) item {
                val ids = progress.routineQueueIds.ifEmpty { com.gianlucacelante.midipiano.learn.RoutinePlan.queue(routine, routineSize).map { it.id } }
                RoutineCard(routine.title, ids.map { it in progress.routineDoneToday }, progress.routineCompletedToday, progress.routineStreak, onStartRoutine, onConfig = { routineDialog = true }, targeted = progress.routineTargeted)
            }
            if (dailySong != null || dailyTrial != null) item {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (dailySong != null) DailyCard("CANZONE DEL GIORNO", dailySong.id, dailySong.title, dailySong.composer ?: dailySong.origin, progress.dailySongDone, song = true) { onOpenSong(dailySong) }
                    if (dailyTrial != null) DailyCard(
                        if ((progress.stars[dailyTrial.id] ?: 0) in 1..2) "PROVA DEL GIORNO · RIPASSO" else "PROVA DEL GIORNO",
                        dailyTrial.id, dailyTrial.title, "${dailyTrial.steps.size} passi", progress.dailyTrialDone,
                    ) { onOpenExercise(dailyTrial) }
                }
            }
        }
        // CONTINUA: una sola riga compatta, la lezione da riprendere e l'ultimo brano
        val next = todo.firstOrNull()
        val last = progress.lastSongId?.let { id -> songs.firstOrNull { it.id == id } }
        if (resume != null || next != null || last != null) {
            item { SectionHeader("Continua") }
            item {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val ex = resume?.exercise ?: next
                    if (ex != null) CompactCard(ex.id, resume?.course?.title ?: "Corsi", ex.title, resume?.let { "${it.done}/${it.total} esercizi della lezione" } ?: "Prossimo esercizio", "Suona") { onOpenExercise(ex) }
                    if (last != null) CompactCard(last.id, "Ultimo brano", last.title, last.composer ?: last.origin, "Riprendi", song = true) { onOpenSong(last) }
                }
            }
        }
    }
}

/** Scheda bassa per la Home: copertina, un titolo, una riga di dettaglio. */
@Composable
private fun CompactCard(id: String, label: String, title: String, detail: String, action: String, song: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.width(420.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverBox(id, title, 84.dp, radius = 12.dp, hint = label, song = song)
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(detail, color = FlowColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(action, color = Color(0xFF0A0F1E), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 16.dp, vertical = 8.dp))
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
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    takes: List<com.gianlucacelante.midipiano.practice.TakeMeta> = emptyList(),
    replayingTake: String? = null,
    onReplayTake: (String) -> Unit = {},
    onDeleteTake: (String) -> Unit = {},
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
        val plain = songs.filter { !it.file.startsWith("user:") && it.format != com.gianlucacelante.midipiano.song.SongFormat.CHORDS && !com.gianlucacelante.midipiano.learn.Studies.isStudy(it.id) } // gli studi stanno in Esercizi
        val levelNames = listOf("Molto facili", "Facili", "Intermedi", "Impegnativi", "Avanzati")
        val rows = listOf(
            "I miei brani" to songs.filter { it.file.startsWith("user:") },
            "Preferiti" to songs.filter { it.id in favorites },
            "Giri di accordi" to songs.filter { it.format == com.gianlucacelante.midipiano.song.SongFormat.CHORDS },
        ) + (1..5).map { lv -> "Livello $lv  ·  ${levelNames[lv - 1]}" to plain.filter { (levels[it.id] ?: 3).coerceIn(1, 5) == lv }.sortedBy { it.title } } // i brani divisi per difficoltà, dal più facile
        val shown = rows.filter { it.second.isNotEmpty() }
        shown.forEach { (title, list) ->
            item { SectionHeader(title) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(list, key = { title + it.id }) { s -> CoverCard(s.id, s.title, s.composer, levels[s.id] ?: 1, size = 210.dp, song = true, onClick = { onOpen(s) }) }
                }
            }
        }
        // Registrazioni delle tue esecuzioni a tempo (prima stavano fra i progressi)
        item { SectionHeader("Le tue registrazioni") }
        if (takes.isEmpty()) item { Text("Quando finisci un brano in «A tempo» la tua esecuzione si salva qui, e puoi riascoltarla.", color = FlowColors.Muted, fontSize = 14.sp) }
        items(takes.size) { i ->
            val t = takes[i]
            val playing = replayingTake == t.name
            val whenText = java.time.Instant.ofEpochMilli(t.whenMillis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Accent).clickable { onReplayTake(t.name) }, contentAlignment = Alignment.Center) {
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
private fun arrangementExplanation(rank: Int) = when (rank) {
    0, 1 -> "Arrangiamento semplice, pensato per chi comincia."
    2 -> "Arrangiamento di difficoltà media."
    3 -> "L'arrangiamento originale, come è scritto nel file."
    else -> "Arrangiamento difficile, per chi ha già dimestichezza."
}

@Composable
fun SongDetailDialog(
    rep: SongEntry, level: Int, favorite: Boolean,
    onToggleFavorite: () -> Unit, onDismiss: () -> Unit, onLearn: (StudyMode, Double, Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit,
    onPreview: (Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit = { _, _ -> }, onStopPreview: () -> Unit = {},
    preview: com.gianlucacelante.midipiano.PreviewState = com.gianlucacelante.midipiano.PreviewState(), onTogglePreview: (Int, com.gianlucacelante.midipiano.song.SongRepository.Style?) -> Unit = { _, _ -> },
    onVariant: (SongEntry) -> Unit = {},
) {
    // più arrangiamenti dello stesso brano: si sceglie quale suonare; da qui in poi «song» è quello scelto
    val variants = LocalSongVariants.current[rep.id]
    var variantIdx by remember(rep.id) { mutableStateOf(0) }
    val song = variants?.getOrNull(variantIdx)?.entry ?: rep
    androidx.compose.runtime.LaunchedEffect(song.id) { onVariant(song) }
    val chordInfo = LocalChordSongs.current[song.id] // null: nessun accordo; true: griglia di accordi; false: sigle sopra lo spartito
    var style by remember(song.id) { mutableStateOf<com.gianlucacelante.midipiano.song.SongRepository.Style?>(if (chordInfo == null) null else if (chordInfo) com.gianlucacelante.midipiano.song.SongRepository.Style.CHORDS else com.gianlucacelante.midipiano.song.SongRepository.Style.NOTES) }
    // con gli accordi interi non esistono versioni più facili: si offre solo l'originale
    // con più arrangiamenti i livelli SONO gli arrangiamenti (ognuno si suona com'è scritto): niente versioni semplificate sopra
    val versions = if (variants != null || style == com.gianlucacelante.midipiano.song.SongRepository.Style.CHORDS || chordInfo == true) listOf(Simplifier.ORIGINAL) else (LocalSongVersions.current[song.id]?.takeIf { it.isNotEmpty() } ?: listOf(Simplifier.ORIGINAL))
    var chosen by remember(song.id, style) { mutableStateOf(versions.first()) } // si parte dalla più facile (Principiante), come in Flowkey
    // anteprima: parte da sola e si cambia con la versione scelta; si ferma uscendo dalla scheda
    androidx.compose.runtime.LaunchedEffect(song.id, chosen, style) { onPreview(chosen, style) }
    androidx.compose.runtime.DisposableEffect(song.id) { onDispose { onStopPreview() } }
    val (c1, c2) = remember(rep.id) { Covers.colors(rep.id) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(520.dp).clip(RoundedCornerShape(24.dp)).background(FlowColors.Surface)) {
            Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(Color(c2), FlowColors.Surface)))) {
                IconButton(Modifier.align(Alignment.TopStart).padding(16.dp), onToggleFavorite) { HeartIcon(favorite) }
                IconButton(Modifier.align(Alignment.TopEnd).padding(16.dp), onDismiss) { CrossIcon() }
                CoverBox(rep.id, rep.title, 170.dp, Modifier.align(Alignment.Center), radius = 14.dp, hint = song.composer ?: "", song = true)
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rep.title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, maxLines = 2, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (variants != null) variants.forEachIndexed { i, v ->
                        val color = rankColor(v.rank)
                        LevelCircle(v.label.substringBefore(" ("), color, selected = i == variantIdx, playing = preview.playing, progress = preview.progress) {
                            if (i == variantIdx) onTogglePreview(chosen, style) else variantIdx = i // il cerchio scelto è play/pausa dell'anteprima
                        }
                    } else versions.forEachIndexed { idx, v ->
                        val color = if (versions.size == 1) { when { level <= 2 -> FlowColors.Green; level == 3 -> FlowColors.Yellow; else -> FlowColors.Red } } else versionColor(v)
                        val label = when { versions.size == 1 -> "Originale"; v == 1 -> "Principiante"; v == 2 -> "Intermedio"; else -> "Avanzato" }
                        LevelCircle(label, color, selected = chosen == v, playing = preview.playing, progress = preview.progress) {
                            if (chosen == v) onTogglePreview(v, style) else chosen = v // il cerchio scelto è play/pausa dell'anteprima
                        }
                    }
                }
                // che cosa cambia in questa versione
                val note = variants?.getOrNull(variantIdx)?.let { arrangementExplanation(it.rank) } ?: versionExplanation(chosen, versions.size)
                Text(note, color = FlowColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp).widthIn(max = 420.dp))
                Button(
                    onClick = { onLearn(StudyMode.WAIT, 1.0, chosen, style); onDismiss() }, // si comincia in "Aspetta": il modo si cambia dall'ingranaggio
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.height(52.dp),
                ) { Text("Impara brano", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp)) }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (variants != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        variants.forEach { v -> Box(Modifier.size(8.dp).clip(CircleShape).background(rankColor(v.rank))) } // un pallino per arrangiamento
                    } else SongDots(song.id, level)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CoursesScreen(courses: List<Course>, progress: ProgressSnapshot, onOpenCourse: (Course) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item { Text("Corsi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp)) }
        if (courses.isEmpty()) item { Text("Caricamento dei corsi…", color = FlowColors.Muted) }
        // per livello, tutti i corsi in vista (niente carosello da scorrere): si tocca un corso e si vedono lezioni ed esercizi
        val groups = listOf("Principiante" to courses.filter { it.level <= 1 }, "Intermedio" to courses.filter { it.level in 2..3 }, "Avanzato" to courses.filter { it.level >= 4 })
        for ((label, list) in groups) {
            if (list.isEmpty()) continue
            item { SectionHeader("$label  ·  ${list.size} ${if (list.size == 1) "corso" else "corsi"}") }
            item {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (c in list) CourseCard(c, progress) { onOpenCourse(c) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CourseCard(c: Course, progress: ProgressSnapshot, onClick: () -> Unit) {
    val done = c.exercises.count { (progress.stars[it.id] ?: 0) > 0 }
    val percent = if (c.exercises.isEmpty()) 0 else done * 100 / c.exercises.size
    Column(
        Modifier.width(300.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoverBox(c.id, c.title, 272.dp, Modifier.height(130.dp).width(272.dp), radius = 12.dp)
        Text(c.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (c.description.isNotBlank()) Text(c.description, color = FlowColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.lessons.size} lezioni · ${c.exercises.size} esercizi", color = FlowColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ProgressPill(percent)
        }
    }
}

/** Un corso in una sola pagina: le lezioni sono sezioni e i loro esercizi sono righe da toccare (niente schermata intermedia). */
@Composable
fun CourseScreen(course: Course, progress: ProgressSnapshot, onBack: () -> Unit, onOpenExercise: (ExerciseSlide) -> Unit, modifier: Modifier = Modifier) {
    val next = course.exercises.firstOrNull { (progress.stars[it.id] ?: 0) == 0 }
    val done = course.exercises.count { (progress.stars[it.id] ?: 0) > 0 }
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Corsi", color = Accent, fontSize = 18.sp) }
            Spacer(Modifier.weight(1f))
            Text("$done/${course.exercises.size} esercizi", color = FlowColors.Muted, fontSize = 15.sp)
            if (next != null) Button(onClick = { onOpenExercise(next) }, colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.padding(start = 14.dp)) {
                Text(if (done == 0) "Inizia" else "Continua: ${next.title}", maxLines = 1)
            }
        }
        Text(course.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (course.description.isNotBlank()) Text(course.description, color = FlowColors.Muted, fontSize = 15.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            for (l in course.lessons) {
                val exs = l.exercises
                val lessonDone = exs.count { (progress.stars[it.id] ?: 0) > 0 }
                item(key = "h-${l.id}") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (l.description.isNotBlank()) Text(l.description, color = FlowColors.Muted, fontSize = 14.sp)
                        }
                        ProgressPill(if (exs.isEmpty()) 0 else lessonDone * 100 / exs.size)
                    }
                }
                // la teoria è una riga che si apre al tocco
                val texts = l.slides.filterIsInstance<TextSlide>()
                if (texts.isNotEmpty()) item(key = "t-${l.id}") { TheoryRow(texts) }
                for (e in exs) item(key = "e-${e.id}") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FlowColors.SurfaceHigh).clickable { onOpenExercise(e) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (e.text.isNotBlank()) Text(e.text, color = FlowColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        StarsRow(progress.stars[e.id] ?: 0)
                        Button(onClick = { onOpenExercise(e) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Suona") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** La spiegazione di una lezione: una riga chiusa («Leggi prima») che al tocco mostra i testi. */
@Composable
private fun TheoryRow(texts: List<TextSlide>) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FlowColors.Surface).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(if (open) "▾ ${texts.first().title}" else "▸ Leggi prima: ${texts.first().title}", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (open) for (t in texts) {
            if (t !== texts.first()) Text(t.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(t.body, color = Color(0xFFC4CEDA), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------- Esercizi ----------

private val LEVEL_NAMES = listOf("Molto facili", "Facili", "Intermedi", "Impegnativi", "Avanzati")

/** La sezione Esercizi: allenamenti (routine, prima vista, accordi), esercizi di tecnica e studi, divisi per difficoltà. */
@Composable
fun ExercisesScreen(
    courses: List<Course>,
    songs: List<SongEntry>,
    levels: Map<String, Int>,
    progress: ProgressSnapshot,
    routines: List<Course>,
    routineId: String,
    routineSize: Int,
    onStartRoutine: () -> Unit,
    onSight: () -> Unit,
    onChords: () -> Unit,
    onEar: () -> Unit,
    onOpenExercise: (ExerciseSlide) -> Unit,
    onOpenSong: (SongEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routine = routines.firstOrNull { it.id == routineId } ?: routines.firstOrNull()
    val technique = courses.filter { it.id in com.gianlucacelante.midipiano.learn.Studies.TECHNIQUE_COURSES }
    val studies = songs.filter { com.gianlucacelante.midipiano.learn.Studies.isStudy(it.id) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        item { Text("Esercizi", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 20.dp)) }
        if (routine != null) item {
            val ids = progress.routineQueueIds.ifEmpty { com.gianlucacelante.midipiano.learn.RoutinePlan.queue(routine, routineSize).map { it.id } }
            RoutineCard(routine.title, ids.map { it in progress.routineDoneToday }, progress.routineCompletedToday, progress.routineStreak, onStartRoutine, onConfig = {}, targeted = progress.routineTargeted)
        }
        item { TrainingCards(progress, onSight, onChords, onEar) }
        // tecnica: gli esercizi dei corsi di tecnica, un livello per volta
        for (lv in 1..5) {
            val list = technique.filter { it.level.coerceIn(1, 5) == lv }.flatMap { c -> c.exercises.map { c to it } }
            if (list.isEmpty()) continue
            item { SectionHeader("Tecnica · Livello $lv  ·  ${LEVEL_NAMES[lv - 1]}") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.second.id }) { (c, e) ->
                        Column(
                            Modifier.width(260.dp).height(176.dp).clip(RoundedCornerShape(16.dp)).background(FlowColors.Surface).clickable { onOpenExercise(e) }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(c.title.uppercase(), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(e.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(e.text, color = FlowColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            StarsRow(progress.stars[e.id] ?: 0)
                        }
                    }
                }
            }
        }
        // studi: Czerny, Burgmüller, Diabelli… dal più facile
        for (lv in 1..5) {
            val list = studies.filter { (levels[it.id] ?: 3).coerceIn(1, 5) == lv }.sortedBy { it.title }
            if (list.isEmpty()) continue
            item { SectionHeader("Studi · Livello $lv  ·  ${LEVEL_NAMES[lv - 1]}") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(list, key = { "st-" + it.id }) { sg -> CoverCard(sg.id, sg.title, sg.composer, lv, size = 190.dp, song = true, onClick = { onOpenSong(sg) }) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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


/** Routine quotidiana in Home: quanti esercizi di oggi sono fatti, la serie di giorni e il pulsante per cominciare. */
@Composable
private fun RoutineCard(title: String, done: List<Boolean>, completedToday: Boolean, streak: Int, onStart: () -> Unit, onConfig: () -> Unit, targeted: Int = 0) {
    val finished = done.count { it }
    Row(
        Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("ROUTINE QUOTIDIANA", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (d in done) Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (d) Accent else FlowColors.SurfaceHigh))
                Text(
                    if (completedToday) "Fatta oggi ✓" else "$finished/${done.size} oggi",
                    color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp),
                )
                if (streak > 0) Text("·  $streak ${if (streak == 1) "giorno" else "giorni"} di fila", color = FlowColors.Muted, fontSize = 14.sp)
            }
            if (targeted > 0) Text("${if (targeted == 1) "1 esercizio scelto" else "$targeted esercizi scelti"} sui tuoi punti deboli", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("⚙", color = FlowColors.Muted, fontSize = 24.sp, modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { onConfig() }.padding(10.dp))
        androidx.compose.material3.Button(onClick = onStart) {
            Text(if (completedToday) "Rifai" else if (finished > 0) "Continua" else "Inizia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Scelta della routine e di quanti esercizi farne ogni giorno. */
@Composable
private fun RoutineDialog(routines: List<Course>, selectedId: String, size: Int, onChange: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val current = routines.firstOrNull { it.id == selectedId } ?: routines.first()
    val total = current.exercises.size
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(24.dp)).background(FlowColors.Surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Routine quotidiana", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Esercizi a due mani da fare ogni giorno per scaldare le mani e allenare le basi.", color = FlowColors.Muted, fontSize = 14.sp)
            Text("Quale routine", color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            for (r in routines) {
                val on = r.id == current.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) FlowColors.SurfaceHigh else Color.Transparent)
                        .border(2.dp, if (on) Accent else FlowColors.SurfaceHigh, RoundedCornerShape(14.dp)).clickable { onChange(r.id, size) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(r.title, color = if (on) Accent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (r.description.isNotEmpty()) Text(r.description, color = FlowColors.Muted, fontSize = 13.sp)
                    Text("${r.exercises.size} esercizi", color = FlowColors.Muted, fontSize = 12.sp)
                }
            }
            Text("Esercizi al giorno", color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val options = (listOf(0) + (3 until total).toList()).distinct() // 0 = tutti
                for (o in options) {
                    val on = (size <= 0 || size >= total) && o == 0 || size == o
                    Text(
                        if (o == 0) "Tutti ($total)" else "$o", color = if (on) Color(0xFF0A0F1E) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Accent else FlowColors.SurfaceHigh).clickable { onChange(current.id, o) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fatto", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
