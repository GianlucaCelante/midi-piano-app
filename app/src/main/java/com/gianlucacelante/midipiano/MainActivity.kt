package com.gianlucacelante.midipiano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.Build
import android.view.WindowManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
import com.gianlucacelante.midipiano.ui.FlowColors
import com.gianlucacelante.midipiano.ui.MainNavRail
import com.gianlucacelante.midipiano.ui.SearchScreen
import com.gianlucacelante.midipiano.ui.SongDetailDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gianlucacelante.midipiano.learn.Courses
import com.gianlucacelante.midipiano.ui.CourseScreen
import com.gianlucacelante.midipiano.ui.CoursesScreen
import com.gianlucacelante.midipiano.ui.HomeScreen
import com.gianlucacelante.midipiano.ui.OnboardingDialog
import com.gianlucacelante.midipiano.ui.ProfileScreen
import com.gianlucacelante.midipiano.ui.ProgressScreen
import com.gianlucacelante.midipiano.ui.SongsScreen
import com.gianlucacelante.midipiano.ui.Tab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gianlucacelante.midipiano.midi.BlePermissions
import com.gianlucacelante.midipiano.ui.PracticeScreen

/** Tavolozza scura con accento arancione (la nostra interpretazione, non i colori di un marchio). */
private val flowTheme = darkColorScheme(
    primary = Color(0xFFF28C0F),
    onPrimary = Color(0xFF1B1200),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF1B1200),
    primaryContainer = Color(0xFF5A3300),
    onPrimaryContainer = Color(0xFFFFDDB4),
    secondaryContainer = Color(0xFF4A3A22),
    onSecondaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF121212),
    surface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFF2A2A2A),
)

class MainActivity : ComponentActivity() {
    private val viewModel: MidiViewModel by viewModels()

    /** Solo debug: permette di avviare da adb la simulazione di esecuzione di un brano. */
    private val autoplayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.gianlucacelante.midipiano.IMPORT_DIR") {
                viewModel.debugImportFolder(java.io.File(getExternalFilesDir(null), "import"), intent.getBooleanExtra("replace", false))
                return
            }
            if (intent.action == "com.gianlucacelante.midipiano.AUTOPLAY_EXERCISE") {
                viewModel.debugAutoplayExercise(intent.getIntExtra("jitter", 20), intent.getFloatExtra("hold", 0.95f).toDouble())
                return
            }
            val query = intent.getStringExtra("song") ?: return
            val entry = viewModel.songs.value.firstOrNull { it.id.contains(query, true) || it.title.contains(query, true) } ?: return
            val mode = if (intent.getStringExtra("mode") == "wait") StudyMode.WAIT else StudyMode.TEMPO
            val speed = intent.getFloatExtra("speed", 1f).toDouble()
            val jitter = intent.getIntExtra("jitter", 20)
            val hand = when (intent.getStringExtra("hand")) { "left" -> Hand.LEFT; "right" -> Hand.RIGHT; else -> Hand.BOTH }
            val countIn = intent.getBooleanExtra("countin", true)
            viewModel.openSong(entry)
            lifecycleScope.launch {
                viewModel.practice.first { it != null && it.title == entry.title }
                viewModel.debugAutoplay(mode, speed, jitter, hand, countIn)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(applicationContext)
        // Mentre si studia lo schermo resta acceso (senza fotogrammi anche l'orologio del brano si fermerebbe)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (BuildConfig.DEBUG) {
            ContextCompat.registerReceiver(
                this, autoplayReceiver, IntentFilter("com.gianlucacelante.midipiano.AUTOPLAY").apply { addAction("com.gianlucacelante.midipiano.AUTOPLAY_EXERCISE"); addAction("com.gianlucacelante.midipiano.IMPORT_DIR") }, ContextCompat.RECEIVER_EXPORTED,
            )
        }
        setContent {
            MaterialTheme(colorScheme = flowTheme) {
                // Surface imposta anche il colore del testo (altrimenti scuro su sfondo scuro)
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground() // fuori dallo schermo: il brano si ferma e l'audio si libera
        super.onStop()
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) unregisterReceiver(autoplayReceiver)
        super.onDestroy()
    }
}

@Composable
private fun App(vm: MidiViewModel) {
    // lo stato dell'esercizio cambia a ogni nota: qui si legge solo «c'è o no» (App non si ricompone a ogni nota), il resto lo legge PracticeHost
    val practiceState = vm.practice.collectAsState()
    val practiceOpen by remember { androidx.compose.runtime.derivedStateOf { practiceState.value != null } }
    val pressedState = vm.pressed.collectAsState()
    val songs by vm.songs.collectAsState()
    val levels by vm.songLevels.collectAsState()
    val versions by vm.songVersions.collectAsState()
    val preview by vm.preview.collectAsState()
    val takes by vm.takes.collectAsState()
    val chordSongs by vm.chordSongs.collectAsState()
    val variants by vm.songVariants.collectAsState()
    val importedSong by vm.imported.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "brano.mid"
            vm.importSong(uri, name)
        }
    }
    val courses by vm.courses.collectAsState()
    val routines by vm.routines.collectAsState()
    val stats by vm.stats.collectAsState()
    val records by vm.records.collectAsState()
    val progress by vm.progressView.collectAsState()
    val settings by vm.settingsState.collectAsState()
    val devices by vm.devices.collectAsState()
    val error by vm.error.collectAsState()
    val clockTime = vm.clockTime.collectAsState()
    val bleFound by vm.bleDevices.collectAsState()
    val bleScanning by vm.bleScanning.collectAsState()
    // Con targetSdk 35 l'app è a tutto schermo: serve il margine per barre di sistema e tacca
    val bg = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    // schermate di allenamento a tutto campo: "chords" (accordi) e "keytest" (prova della tastiera)
    var training by rememberSaveable { mutableStateOf<String?>(null) }
    val kbCheck by vm.keyboardCheck.collectAsState()

    var pendingReminder by remember { mutableStateOf(-1) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onBlePermissionsResult(result.isNotEmpty() && result.values.all { it })
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings(settings.copy(mic = true))
    }
    // Attivare il microfono richiede il permesso: si chiede alla prima volta
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // anche senza permesso l'ora si salva (le notifiche partono appena lo si concede dalle impostazioni di sistema)
        vm.updateSettings(settings.copy(reminderHour = pendingReminder))
    }
    val applySettings: (Settings) -> Unit = { new ->
        if (new.mic && !settings.mic && !vm.micPermitted()) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        else if (new.reminderHour >= 0 && new.reminderHour != settings.reminderHour && !com.gianlucacelante.midipiano.Reminder.canNotify(context)) {
            pendingReminder = new.reminderHour
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        else vm.updateSettings(new)
    }
    val startScan = {
        if (vm.bleHasPermissions()) vm.startBleScan()
        else permissionLauncher.launch(BlePermissions.required(Build.VERSION.SDK_INT).toTypedArray())
    }

    if (!practiceOpen && !settings.onboarded) OnboardingDialog(onFinish = { vm.updateSettings(settings.copy(onboarded = true)) })
    if (practiceOpen) {
        PracticeHost(vm, practiceState, clockTime, applySettings, bg)
        return
    }

    val course = courses.firstOrNull { it.id == courseId }
    if (training != null) BackHandler { training = null }
    if (tab == Tab.COURSES && course != null && training == null) {
        BackHandler { courseId = null }
    }

    var detail by remember { mutableStateOf<com.gianlucacelante.midipiano.song.SongEntry?>(null) }
    var detailVariant by remember { mutableStateOf<com.gianlucacelante.midipiano.song.SongEntry?>(null) } // arrangiamento scelto nella scheda (null = il brano stesso)
    androidx.compose.runtime.CompositionLocalProvider(com.gianlucacelante.midipiano.ui.LocalSongVersions provides versions, com.gianlucacelante.midipiano.ui.LocalChordSongs provides chordSongs, com.gianlucacelante.midipiano.ui.LocalSongVariants provides variants) {
    Row(Modifier.fillMaxSize().background(FlowColors.Navy).safeDrawingPadding()) {
        MainNavRail(tab, { tab = it; training = null })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (training == "chords") com.gianlucacelante.midipiano.ui.ChordTrainerScreen(
                progress, pressedState.value, onKeyDown = vm::pressKey, onKeyUp = vm::releaseKey, onPlayChord = vm::playChord, onAnswer = vm::recordChordAnswer, onBack = { training = null },
            ) else if (training == "ear") com.gianlucacelante.midipiano.ui.EarTrainerScreen(
                progress, pressedState.value, onKeyDown = vm::pressKey, onKeyUp = vm::releaseKey, onPlay = { vm.playSequence(it) }, onAnswer = vm::recordEarAnswer, onBack = { training = null },
            ) else if (training == "keytest") com.gianlucacelante.midipiano.ui.KeyboardCheckScreen(
                kbCheck, devices, onStart = vm::startKeyboardCheck, onShareLog = { com.gianlucacelante.midipiano.shareLog(context) }, onBack = { vm.stopKeyboardCheck(); training = null },
            ) else when (tab) {
                Tab.HOME -> HomeScreen(
                    progress, courses, songs, levels, onOpenExercise = vm::openExercise, onOpenSong = { detail = it }, onSeeAllSongs = { tab = Tab.SONGS }, dailyGoalMinutes = settings.dailyGoalMinutes,
                    routines = routines, routineId = settings.routineId, routineSize = settings.routineSize, onStartRoutine = vm::startRoutine,
                    onRoutineSettings = { id, size -> vm.updateSettings(settings.copy(routineId = id, routineSize = size)) },
                )
                Tab.SONGS -> SongsScreen(
                    songs, levels, progress.favorites, error, vm::dismissError, onOpen = { detail = it }, onImport = { importLauncher.launch(arrayOf("*/*")) },
                    takes = takes, replayingTake = if (preview.playing) vm.replayingTakeName() else null, onReplayTake = vm::toggleSavedTake, onDeleteTake = vm::deleteTake,
                )
                Tab.SEARCH -> SearchScreen(songs, levels, progress.favorites, onOpen = { detail = it })
                Tab.COURSES -> when {
                    course == null -> CoursesScreen(courses, progress, onOpenCourse = { courseId = it.id })
                    else -> CourseScreen(course, progress, onBack = { courseId = null }, onOpenExercise = vm::openExercise)
                }
                Tab.EXERCISES -> com.gianlucacelante.midipiano.ui.ExercisesScreen(
                    courses, songs, levels, progress, routines, settings.routineId, settings.routineSize, onStartRoutine = vm::startRoutine,
                    onSight = vm::openSight, onChords = { training = "chords" }, onEar = { training = "ear" }, onOpenExercise = vm::openExercise, onOpenSong = { detail = it },
                )
                Tab.PROGRESS -> ProgressScreen(
                    progress, songs + variants.values.flatten().map { it.entry }, onOpenSong = { detail = vm.repEntry(it.id) ?: it },
                    stats = stats, records = records, onOpenExercise = { id -> (courses.flatMap { it.exercises } + routines.flatMap { it.exercises }).firstOrNull { it.id == id }?.let(vm::openExercise) },
                )
                Tab.PROFILE -> ProfileScreen(
                    progress, devices, vm.midiSupported, bleScanning, bleFound, settings,
                    onBleScan = startScan, onBleConnect = vm::connectBle, onSettings = applySettings, onKeyboardCheck = { training = "keytest" },
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(importedSong) { importedSong?.let { detail = vm.repEntry(it.id) ?: it; vm.consumeImported() } }
    detail?.let { d0 ->
        val d = vm.repEntry(d0.id) ?: d0 // l'elenco può essere stato rifatto mentre la scheda era aperta: si riprende il brano giusto
        val act = detailVariant ?: d // l'arrangiamento scelto (o il brano stesso)
        SongDetailDialog(
            d, levels[d.id] ?: 1, d.id in progress.favorites,
            onToggleFavorite = { vm.toggleFavorite(d.id) }, onDismiss = { detail = null; detailVariant = null },
            onLearn = { mode, speed, version, style -> vm.openSong(act, mode, speed, version, style) },
            onPreview = { v, st -> vm.startPreview(act, v, st) }, onStopPreview = vm::stopPreview,
            preview = preview, onTogglePreview = { v, st -> vm.togglePreview(act, v, st) },
            onVariant = { detailVariant = it },
        )
    }
    }
}

/** La schermata di studio: legge da sola lo stato (che cambia a ogni nota) senza far ricomporre tutta App. */
@Composable
private fun PracticeHost(
    vm: MidiViewModel,
    practiceState: androidx.compose.runtime.State<PracticeSnapshot?>,
    clockTime: androidx.compose.runtime.State<Float>,
    applySettings: (Settings) -> Unit,
    bg: Modifier,
) {
    val current = practiceState.value ?: return
    val pressed by vm.pressed.collectAsState()
    val press by vm.press.collectAsState()
    val wrongKeys by vm.wrong.collectAsState()
    val courses by vm.courses.collectAsState()
    val preview by vm.preview.collectAsState()
    val devices by vm.devices.collectAsState()
    BackHandler(onBack = vm::onPracticeBack)
    PracticeScreen(
        snapshot = current, pressed = pressed, wrongKeys = wrongKeys, clockTime = clockTime,
        onBack = vm::onPracticeBack, onHand = vm::setHand, onMode = vm::setMode, onPlayPause = vm::togglePlay,
        onSpeed = vm::setSpeed, onTranspose = vm::setTranspose, onReview = vm::reviewBars, onReviewExercise = vm::reviewExerciseBars, onListenFirst = vm::listenFirst, onSkipListen = vm::skipListenFirst, press = press, hasTake = vm.hasTake(), replaying = preview.playing, onReplay = vm::toggleTakeReplay, onSettings = applySettings, onLoop = vm::setLoop, onSeekBar = vm::seekToBar,
        onRestart = vm::restart, onSeek = vm::seek, onFrame = vm::onFrame,
        onExerciseNext = {
            val next = current.exercise?.let { if (it.routinePos != null) it.routineNext else Courses.nextExercise(courses, it.slideId) }
            if (current.exercise?.slideId?.let { com.gianlucacelante.midipiano.learn.SightReading.isSight(it) } == true) vm.openSight()
            else if (next != null) vm.openExercise(next) else vm.closeSong()
        },
        onExerciseRetry = vm::retryExercise, onExit = vm::closeSong, onEndReview = vm::endExerciseReview,
        onKeyDown = vm::pressKey, onKeyUp = vm::releaseKey, modifier = bg,
        nextExercise = current.exercise?.let { if (it.routinePos != null) it.routineNext else Courses.nextExercise(courses, it.slideId) },
        nextLessonTitle = current.exercise?.let { ex -> if (ex.routinePos != null) "Routine quotidiana" else Courses.nextExercise(courses, ex.slideId)?.let { n -> Courses.lessonOf(courses, n.id)?.title } },
        nextIsNewLesson = current.exercise?.let { ex -> if (ex.routinePos != null) false else Courses.nextExercise(courses, ex.slideId)?.let { n -> Courses.lessonOf(courses, n.id)?.id != Courses.lessonOf(courses, ex.slideId)?.id } } ?: false,
        previewing = preview.playing,
        onPreviewNext = { if (preview.playing) vm.stopPreview() else current.exercise?.let { ex -> Courses.nextExercise(courses, ex.slideId)?.let(vm::previewExercise) } },
        onStopPreview = vm::stopPreview, onTogglePedal = vm::togglePedal, onSkipStep = vm::skipOptionalStep, onRestartRun = vm::restartExerciseRun,
        midiStatus = if (devices.isEmpty()) "Nessuna tastiera collegata. Collegala con USB (OTG) o dal Bluetooth nelle Impostazioni dell'app." else "Collegato: ${devices.joinToString()}",
    )
}
