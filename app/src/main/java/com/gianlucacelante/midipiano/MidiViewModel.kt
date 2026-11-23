package com.gianlucacelante.midipiano

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gianlucacelante.midipiano.audio.AudioEngine
import com.gianlucacelante.midipiano.audio.MicInput
import com.gianlucacelante.midipiano.midi.BleMidiDevice
import com.gianlucacelante.midipiano.midi.BleMidiScanner
import com.gianlucacelante.midipiano.midi.MidiEvent
import com.gianlucacelante.midipiano.midi.MidiInput
import com.gianlucacelante.midipiano.learn.Awards
import com.gianlucacelante.midipiano.learn.Course
import com.gianlucacelante.midipiano.learn.CourseRepository
import com.gianlucacelante.midipiano.learn.DailyChallenge
import com.gianlucacelante.midipiano.learn.Difficulty
import com.gianlucacelante.midipiano.learn.ExerciseRunner
import com.gianlucacelante.midipiano.learn.ExerciseSlide
import com.gianlucacelante.midipiano.learn.LearnStep
import com.gianlucacelante.midipiano.learn.Progress
import com.gianlucacelante.midipiano.learn.ProgressStore
import com.gianlucacelante.midipiano.learn.RunOutcome
import com.gianlucacelante.midipiano.notation.NoteState
import com.gianlucacelante.midipiano.notation.NotationLayout
import com.gianlucacelante.midipiano.notation.StaffNote
import com.gianlucacelante.midipiano.practice.FallingNotes
import com.gianlucacelante.midipiano.practice.HitState
import com.gianlucacelante.midipiano.practice.Backing
import com.gianlucacelante.midipiano.practice.NoteScheduler
import com.gianlucacelante.midipiano.practice.PracticeSession
import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongEntry
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongRepository
import com.gianlucacelante.midipiano.ui.SheetData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.floor
import kotlin.random.Random

/** Stato di un esercizio dei corsi in corso: passo, giri, messaggio e, a fine esercizio, le stelle. */
data class ExerciseStatus(
    val slideId: String,
    val title: String,
    val text: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val stepText: String,
    val runsDone: Int,
    val runsRequired: Int,
    val message: String?,
    val stars: Int?,
    /** Il passo corrente è facoltativo (la prova rallentata): si può saltare. */
    val canSkip: Boolean = false,
    /** Pedale richiesto in questo momento dall'esercizio (true = giù), null se l'esercizio non lo chiede. */
    val pedalExpected: Boolean? = null,
    val pedalDown: Boolean = false,
    /** Dopo alcuni giri falliti: le battute da ripassare (null se non serve). */
    val reviewRange: IntRange? = null,
    /** Ripasso lento in corso (battute): l'esercizio è in pausa e si torna con «Fine ripasso». */
    val reviewing: IntRange? = null,
    /** Dentro la routine quotidiana: (posizione, totale) e l'esercizio successivo (null se è l'ultimo o se sono tutti fatti). */
    val routinePos: Pair<Int, Int>? = null,
    val routineNext: ExerciseSlide? = null,
    val routineComplete: Boolean = false,
)

/** Progressi dell'utente in forma immutabile per l'interfaccia. */
data class ProgressSnapshot(
    val stars: Map<String, Int> = emptyMap(),
    val streak: Int = 0,
    val totalSeconds: Long = 0,
    val favorites: Set<String> = emptySet(),
    val lastExerciseId: String? = null,
    val exercisesDone: Int = 0,
    val songsDone: Set<String> = emptySet(),
    val todaySeconds: Long = 0,
    val lastSongId: String? = null,
    val lastSongVersion: Int = 3,
    /** Sfide di oggi: canzone e prova scelte per oggi, e se sono già fatte. */
    val dailySongId: String? = null,
    val dailyTrialId: String? = null,
    val dailySongDone: Boolean = false,
    val dailyTrialDone: Boolean = false,
    /** Per la schermata dei progressi: minuti degli ultimi 14 giorni, calendario di 5 settimane (0 niente, 1 suonato, 2 sfide fatte), giri a tempo e traguardi. */
    val minutes: List<Pair<LocalDate, Int>> = emptyList(),
    val calendar: List<Pair<LocalDate, Int>> = emptyList(),
    val songRuns: Map<String, List<Double>> = emptyMap(),
    val weakBars: Map<String, List<Int>> = emptyMap(),
    val achievements: List<com.gianlucacelante.midipiano.learn.Achievements.Achievement> = emptyList(),
    val sightLevel: Int = 1,
    val sightDone: Int = 0,
    val chordLevel: Int = 1,
    val chordCorrect: Int = 0,
    val intervalLevel: Int = 1,
    val intervalCorrect: Int = 0,
    val melodyLevel: Int = 1,
    val melodyCorrect: Int = 0,
    /** Routine quotidiana: esercizi fatti oggi, giorni consecutivi, completata oggi, giorni completati in tutto. */
    val routineDoneToday: Set<String> = emptySet(),
    val routineStreak: Int = 0,
    val routineCompletedToday: Boolean = false,
    val routineTotalToday: Int = 0,
    val routineQueueIds: List<String> = emptyList(),
    val routineTargeted: Int = 0,
    val routineDays: Int = 0,
    /** Per il calendario: cosa è stato fatto negli ultimi sei mesi (solo i giorni con qualcosa). */
    val dayFlags: Map<LocalDate, DayFlags> = emptyMap(),
)

/** Cosa è successo in un giorno: secondi di pratica, sfide e routine completate. */
data class DayFlags(val seconds: Long, val songDone: Boolean, val trialDone: Boolean, val routineDone: Boolean, val practiced: Boolean) {
    val any: Boolean get() = seconds > 0 || songDone || trialDone || routineDone || practiced
}

/** Istantanea immutabile della sessione di studio, da mostrare in UI. */
/** Anteprima audio della scheda del brano. */
/** Ultimo tasto premuto dalla tastiera MIDI: forza, forza attesa dal brano (se ha una dinamica scritta) e quando. */
data class PressInfo(val velocity: Int, val expected: Int?, val id: Long)

data class PreviewState(val playing: Boolean = false, val progress: Float = 0f)

data class PracticeSnapshot(
    val title: String,
    val composer: String?,
    val license: String,
    val hand: Hand,
    val mode: StudyMode,
    val isPlaying: Boolean,
    val countInBeat: Int?,
    val speed: Double,
    val settings: Settings,
    val loopBars: IntRange?,
    val loopSec: ClosedFloatingPointRange<Double>?,
    val loopsCompleted: Int,
    val stepIndex: Int,
    val totalSteps: Int,
    val barCount: Int,
    val currentBar: Int,
    val expectedKeys: Set<Int>,
    val mistakes: Int,
    val hits: Int,
    val accuracy: Double,
    val isFinished: Boolean,
    val currentTimeSec: Double,
    val durationSec: Double,
    val progress: Float,
    val notes: List<SongNote>,
    val sheet: SheetData,
    val keyRange: IntRange,
    val exercise: ExerciseStatus?,
    /** Stato di una nota da disegnare (già suonata, da suonare ora, in arrivo, mancata). */
    val noteState: (StaffNote) -> NoteState,
    /** Mano che deve premere ciascun tasto atteso, e dito consigliato (1 = pollice ... 5 = mignolo). */
    val expectedHands: Map<Int, Hand> = emptyMap(),
    val fingers: Map<Int, Int> = emptyMap(),
    /** Note dei passi successivi (anteprima in chiaro sulla tastiera): tasto -> mano, e tasto -> dito. */
    val upcomingHands: Map<Int, Hand> = emptyMap(),
    val upcomingFingers: Map<Int, Int> = emptyMap(),
    /** Quanto mostrare ogni nota in arrivo (0..1): più è vicina più si vede. */
    val upcomingWeights: Map<Int, Float> = emptyMap(),
    /** A tempo: istante d'attacco (secondi del brano) di ogni nota in arrivo; la visibilità si calcola a ogni fotogramma. */
    val upcomingTimes: Map<Int, Double> = emptyMap(),
    /** L'ultimo tasto fuori tempo rispetto a una pausa (troppo presto / troppo tardi / durante la pausa), con un numero progressivo. */
    val timingFeedback: com.gianlucacelante.midipiano.practice.TimingFeedback? = null,
    /** Semitoni di trasposizione del brano aperto, e se si può trasporre (solo brani liberi con note). */
    val transpose: Int = 0,
    val canTranspose: Boolean = false,
    /** Anteprima di ascolto in corso prima di suonare. */
    val listeningFirst: Boolean = false,
    /** Mappa degli errori per battuta: solo a brano finito (brani liberi, non ascolto). */
    val barStats: List<com.gianlucacelante.midipiano.practice.BarStat> = emptyList(),
    /** Dopo un esercizio con meno di 3 stelle: le battute più sbagliate da ripassare. */
    val exerciseReview: IntRange? = null,
    /** Movimento corrente (1.. n) e movimenti nella battuta, per il metronomo visivo; null se non serve. */
    val beatInBar: Int? = null,
    val beatsInBar: Int = 4,
    /** Scarto medio di tempo e distanza fra le note degli accordi, a fine giro "A tempo". */
    val timing: com.gianlucacelante.midipiano.practice.TimingSummary? = null,
)

class MidiViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SongRepository(app)
    private val lock = Any() // gli eventi MIDI arrivano da un thread di sistema

    private var openJob: Job? = null
    private var autoplayJob: Job? = null
    private var deviceCount = 0
    private var session: PracticeSession? = null
    private var song: Song? = null
    private var notes: List<SongNote> = emptyList()
    private var sheet = SheetData.EMPTY
    private var fingering: Map<Long, Int> = emptyMap()
    private var keyRange: IntRange = 36..96
    private var meta: Triple<String, String?, String> = Triple("", null, "")
    private var lastCountInBeat: Int? = null
    private var lastBeatIdxShown = -2
    private var backingSched: NoteScheduler? = null
    private var backingFor: Pair<Song, Hand>? = null
    private var lastBackingTime = 0.0

    // Audio: il suono dei tasti (tocco, simulazione) e i "tic" di conto alla rovescia e metronomo.
    // I tasti di una tastiera MIDI collegata hanno già il loro suono: il sintetizzatore li ripete solo se richiesto.
    private val audio = AudioEngine(app)
    private val appSettings = AppSettings(app)
    private val mic = MicInput(
        app,
        onNoteOn = { note, velocity -> noteOn(note, velocity, fromMidi = true).also { } },
        onNoteOff = { note -> noteReleased(note); _pressed.update { it - note }; _wrong.update { it - note } },
    )
    @Volatile private var settings = appSettings.load()
    private var lastBeatIdx = Int.MIN_VALUE
    private var lastBar = 0
    private var lastExpected: Set<Int> = emptySet()
    private var scheduler: NoteScheduler? = null
    private var lastListenTime = 0.0

    // Corsi, esercizi e progressi
    private val courseRepository = CourseRepository(app)
    private val routineRepository = com.gianlucacelante.midipiano.learn.RoutineRepository(app)
    private val runStore = com.gianlucacelante.midipiano.learn.RunStore(app)
    private var runRecords: List<com.gianlucacelante.midipiano.learn.RunRecord> = emptyList()
    private val _records = MutableStateFlow<List<com.gianlucacelante.midipiano.learn.RunRecord>>(emptyList())
    /** Tutti i giri registrati (per il calendario e il riepilogo dei giorni). */
    val records: StateFlow<List<com.gianlucacelante.midipiano.learn.RunRecord>> = _records
    private val _stats = MutableStateFlow(com.gianlucacelante.midipiano.learn.Analytics.compute(emptyList()))
    /** Statistiche calcolate da tutti i giri registrati. */
    val stats: StateFlow<com.gianlucacelante.midipiano.learn.StatsReport> = _stats
    private val _routines = MutableStateFlow<List<Course>>(emptyList())
    /** Routine quotidiane disponibili. */
    val routines: StateFlow<List<Course>> = _routines
    /** La routine in corso (gli esercizi di oggi); null fuori da una routine. */
    private var routineQueue: List<ExerciseSlide>? = null
    private var runStartNanos = System.nanoTime()
    private val progressStore = ProgressStore(app)
    private val progress: Progress = progressStore.load()
    private var runner: ExerciseRunner? = null
    /** Ripasso lento di alcune battute dentro l'esercizio: l'esercizio resta aperto e si riprende da dove era. */
    private var reviewLoop: IntRange? = null
    private var runGeneration = 0 // cambia a ogni ripartenza del giro: il job ritardato di fine giro non deve azzerare un giro già ripartito
    private val struggle = com.gianlucacelante.midipiano.learn.StruggleTracker()
    private var currentExercise: ExerciseSlide? = null
    private var exerciseMessage: String? = null
    private var exerciseStars: Int? = null
    private var openedSongId: String? = null
    private val takeFiles = com.gianlucacelante.midipiano.practice.TakeFiles(java.io.File(app.filesDir, "takes"))
    private val _takes = MutableStateFlow<List<com.gianlucacelante.midipiano.practice.TakeMeta>>(emptyList())
    /** Registrazioni salvate, dalla più recente. */
    val takes: StateFlow<List<com.gianlucacelante.midipiano.practice.TakeMeta>> = _takes
    private var replayingTake: String? = null
    private var songExpressive = false
    private val take = com.gianlucacelante.midipiano.practice.TakeRecorder()
    private var pressCounter = 0L
    private val _press = MutableStateFlow<PressInfo?>(null)
    val press: StateFlow<PressInfo?> = _press
    private var openedEntry: SongEntry? = null
    private var openedStyle: com.gianlucacelante.midipiano.song.SongRepository.Style? = null
    private var transposeSemis = 0
    private var openedVersion = com.gianlucacelante.midipiano.learn.Simplifier.ORIGINAL
    private var songDoneRecorded = false
    private var lastTiming: com.gianlucacelante.midipiano.practice.TimingFeedback? = null
    private var timingCounter = 0L
    private var lastTimingNanos = 0L
    private var runHandled = false
    // forza dei tasti e pedale del giro in corso (esercizi con «touch» o «pedal»)
    private val runVel = ArrayList<Int>()
    private val runTarget = ArrayList<Int?>()
    private var runHits = 0
    private val pedalLog = ArrayList<Pair<Double, Boolean>>()
    @Volatile private var pedalDown = false
    private var lastPedalExpected: Boolean? = null
    private fun resetRunData() { runVel.clear(); runTarget.clear(); runHits = 0; pedalLog.clear(); runStartNanos = System.nanoTime() }
    private var practiceMark = 0L // ultimo istante di attività contato nel tempo di pratica
    private var practiceAccumNanos = 0L // tempo attivo accumulato dalla sessione in corso (non ancora salvato)
    private var partialRun = false // il giro è ripartito a metà (cursore trascinato): non vale come giro riuscito

    private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
    val pressed: StateFlow<Set<Int>> = _pressed
    private val _wrong = MutableStateFlow<Set<Int>>(emptySet())
    /** Tasti premuti che non erano quelli attesi: restano segnalati finché il tasto è premuto. */
    val wrong: StateFlow<Set<Int>> = _wrong

    private val _devices = MutableStateFlow<List<String>>(emptyList())
    val devices: StateFlow<List<String>> = _devices

    private val _songs = MutableStateFlow<List<SongEntry>>(emptyList())
    val songs: StateFlow<List<SongEntry>> = _songs

    private val _practice = MutableStateFlow<PracticeSnapshot?>(null)
    val practice: StateFlow<PracticeSnapshot?> = _practice

    /** Tempo corrente del brano, aggiornato a ogni fotogramma in modalità a tempo (letto solo in fase di disegno). */
    private val _clockTime = MutableStateFlow(0f)
    val clockTime: StateFlow<Float> = _clockTime

    private val _settings = MutableStateFlow(settings)
    val settingsState: StateFlow<Settings> = _settings

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses

    private val _songVariants = MutableStateFlow<Map<String, List<com.gianlucacelante.midipiano.song.Variant>>>(emptyMap())
    /** Brani che esistono in più arrangiamenti (id del brano mostrato -> arrangiamenti, dal più facile). */
    val songVariants: StateFlow<Map<String, List<com.gianlucacelante.midipiano.song.Variant>>> = _songVariants
    @Volatile private var variantToRep: Map<String, String> = emptyMap()
    /** L'id del brano mostrato negli elenchi per un arrangiamento (o l'id stesso). */
    fun repOf(id: String): String = variantToRep[id] ?: id
    fun repEntry(id: String): SongEntry? = _songs.value.firstOrNull { it.id == repOf(id) }

    private fun applyGroups(entries: List<SongEntry>, levels: Map<String, Int>, loaded: Map<String, Song>) {
        fun similar(a: Song, b: Song): Boolean = a.notes.size >= 20 && b.notes.size >= 20 &&
            kotlin.math.abs(a.notes.size - b.notes.size) <= 0.3 * maxOf(a.notes.size, b.notes.size) && kotlin.math.abs(a.durationSec - b.durationSec) <= 3.0
        val groups = com.gianlucacelante.midipiano.song.SongGroups.build(entries, { levels[it] ?: 3 }, { a, b -> val x = loaded[a]; val y = loaded[b]; x != null && y != null && similar(x, y) }, { loaded[it]?.notes?.size ?: 0 })
        variantToRep = groups.flatMap { g -> g.memberIds.map { it to g.rep.id } }.toMap() // anche le voci scartate perché doppie
        _songVariants.value = groups.filter { it.variants.size > 1 }.associate { it.rep.id to it.variants }
        _songs.value = groups.map { it.rep }
    }

    private val _songLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val songLevels: StateFlow<Map<String, Int>> = _songLevels

    /** Versioni disponibili di ogni brano (1 facile, 2 intermedia, 3 originale). */
    private val _songVersions = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val songVersions: StateFlow<Map<String, List<Int>>> = _songVersions

    /** Brani che hanno gli accordi (griglie di accordi, o sigle sopra lo spartito): si può scegliere come suonarli. */
    private val _chordSongs = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** id -> true se è una griglia di accordi (di default si suona per accordi), false se ha solo le sigle sopra lo spartito. */
    val chordSongs: StateFlow<Map<String, Boolean>> = _chordSongs

    private val _progress = MutableStateFlow(ProgressSnapshot())
    val progressView: StateFlow<ProgressSnapshot> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val kbCheck = com.gianlucacelante.midipiano.practice.KeyboardCheck()
    private val _kbState = MutableStateFlow(kbCheck.state)
    /** Stato della prova guidata della tastiera (si aggiorna con gli eventi MIDI veri, non con i tasti sullo schermo). */
    val keyboardCheck: StateFlow<com.gianlucacelante.midipiano.practice.KeyboardCheck.State> = _kbState
    /** La prova tastiera è aperta: solo allora i suoi passi finiscono nel registro (altrimenti sporcano le sessioni di studio). */
    @Volatile private var kbCheckOpen = false
    fun stopKeyboardCheck() { kbCheckOpen = false }

    private fun publishKbCheck(before: com.gianlucacelante.midipiano.practice.KeyboardCheck.State) {
        val now = kbCheck.state
        _kbState.value = now
        if (!kbCheckOpen) return
        for (step in now.done - before.done) AppLog.i("PROVA", "passo superato: ${step.title}")
        if (now.allDone && !before.allDone) AppLog.i("PROVA", "tutti i passi superati (note viste: ${now.rangeText ?: "-"}, ultima forza ${now.lastVelocity})")
    }
    fun startKeyboardCheck() { kbCheckOpen = true; synchronized(kbCheck) { val b = kbCheck.state; kbCheck.reset(_devices.value.isNotEmpty()); AppLog.i("PROVA", "prova tastiera avviata"); publishKbCheck(b.copy(done = emptySet())) } }

    private val input = MidiInput(
        context = app,
        onEvent = { event ->
            when (event) {
                is MidiEvent.NoteOn -> { AppLog.i("MIDI", "on nota=${event.note} vel=${event.velocity}"); synchronized(kbCheck) { val b = kbCheck.state; kbCheck.onNoteOn(event.note, event.velocity); publishKbCheck(b) }; noteOn(event.note, event.velocity, fromMidi = true) }
                is MidiEvent.NoteOff -> {
                    AppLog.i("MIDI", "off nota=${event.note}")
                    synchronized(kbCheck) { kbCheck.onNoteOff(event.note) }
                    take.noteOff(event.note, System.nanoTime())
                    noteReleased(event.note)
                    _pressed.update { it - event.note }
                    _wrong.update { it - event.note }
                    if (settings.midiSounds) audio.noteOff(event.note)
                }
                is MidiEvent.Control -> if (event.controller == 64) { // pedale di risonanza
                    AppLog.i("MIDI", "pedale ${if (event.value >= 64) "giù" else "su"}")
                    synchronized(kbCheck) { val b = kbCheck.state; kbCheck.onPedal(event.value); publishKbCheck(b) }
                    if (settings.midiSounds) audio.sustain(event.value >= 64)
                    onPedalChanged(event.value >= 64)
                }
            }
        },
        onDevicesChanged = { names ->
            AppLog.i("MIDI", "dispositivi: ${if (names.isEmpty()) "nessuno" else names.joinToString()}")
            _devices.value = names
            synchronized(kbCheck) { val b = kbCheck.state; kbCheck.onConnection(names.isNotEmpty()); publishKbCheck(b) }
            // Scollegando una tastiera (anche se ne resta un'altra) le sue note premute non arriveranno più a "off"
            if (names.size < deviceCount) _pressed.value = emptySet()
            deviceCount = names.size
        },
    )

    val midiSupported: Boolean get() = input.isSupported

    private val _bleDevices = MutableStateFlow<List<BleMidiDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleMidiDevice>> = _bleDevices

    private val _bleScanning = MutableStateFlow(false)
    val bleScanning: StateFlow<Boolean> = _bleScanning

    private val scanner = BleMidiScanner(
        context = app,
        onDevices = { _bleDevices.value = it },
        onScanning = { _bleScanning.value = it },
        onError = { _error.value = it },
    )

    fun bleHasPermissions(): Boolean = scanner.hasPermissions()
    fun startBleScan() = scanner.start()
    fun stopBleScan() = scanner.stop()

    fun onBlePermissionsResult(granted: Boolean) {
        if (granted) scanner.start() else run { AppLog.w("ERRORE", "Senza i permessi Bluetooth non posso cercare la tastiera."); _error.value = "Senza i permessi Bluetooth non posso cercare la tastiera." }
    }

    fun connectBle(device: BleMidiDevice) {
        scanner.stop()
        val remote = scanner.remoteDevice(device.address)
        if (remote == null) {
            run { AppLog.w("ERRORE", "Dispositivo Bluetooth non trovato."); _error.value = "Dispositivo Bluetooth non trovato." }
            return
        }
        input.connectBluetooth(remote) { ok ->
            if (!ok) run { AppLog.w("ERRORE", "Connessione a ${device.displayName} non riuscita."); _error.value = "Connessione a ${device.displayName} non riuscita." }
        }
    }

    private var previewJob: Job? = null
    private val _preview = MutableStateFlow(PreviewState())
    /** Stato dell'anteprima nella scheda del brano: in riproduzione e avanzamento 0..1. */
    val preview: StateFlow<PreviewState> = _preview

    fun togglePreview(entry: SongEntry, version: Int, style: com.gianlucacelante.midipiano.song.SongRepository.Style? = null) {
        if (_preview.value.playing) stopPreview() else startPreview(entry, version, style)
    }

    /** Anteprima: suona la versione scelta del brano (nella scheda del brano), per sentire se piace prima di studiarlo. */
    fun startPreview(entry: SongEntry, version: Int, style: com.gianlucacelante.midipiano.song.SongRepository.Style? = null) {
        previewJob?.cancel()
        if (session != null || !settings.sound) return // non durante lo studio; e con il suono spento niente anteprima
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                runPreview(com.gianlucacelante.midipiano.learn.Simplifier.version(repository.load(entry, style), version), Double.MAX_VALUE, entry.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Preview", "anteprima non riuscita: ${e.message}")
            } finally {
                Log.d("Preview", "fine")
                _preview.value = PreviewState()
                audio.allNotesOff()
            }
        }
    }

    private fun refreshTakes() {
        _takes.value = runCatching { takeFiles.list() }.getOrDefault(emptyList())
        publishProgress()
    }

    /** Riascolta (o ferma) una registrazione salvata. */
    fun toggleSavedTake(name: String) {
        if (_preview.value.playing) { val same = replayingTake == name; stopPreview(); replayingTake = null; if (same) return }
        val t = takeFiles.load(name) ?: return
        replayingTake = name
        val sg = Song("take", t.title, null, com.gianlucacelante.midipiano.song.SongSource("registrazione", "personale"), t.notes)
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                runPreview(sg, Double.MAX_VALUE, t.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Preview", "riascolto non riuscito: ${e.message}")
            } finally {
                _preview.value = PreviewState()
                audio.allNotesOff()
                replayingTake = null
            }
        }
    }

    fun deleteTake(name: String) {
        viewModelScope.launch(Dispatchers.IO) { takeFiles.delete(name); refreshTakes() }
    }

    fun replayingTakeName(): String? = replayingTake

    /** C'è un'esecuzione registrata da riascoltare. */
    fun hasTake(): Boolean = !take.isEmpty

    /** Riascolta (o ferma) la propria esecuzione: stesso lettore dell'anteprima, con il suo progresso. */
    fun toggleTakeReplay() {
        if (_preview.value.playing) { stopPreview(); return }
        val song = take.toSong() ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                runPreview(song, Double.MAX_VALUE, "la tua esecuzione")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Preview", "riascolto non riuscito: ${e.message}")
            } finally {
                _preview.value = PreviewState()
                audio.allNotesOff()
            }
        }
    }

    /** Anteprima del prossimo esercizio di un corso (le prime note), mentre si è nella schermata di completamento. */
    fun previewExercise(slide: ExerciseSlide) {
        previewJob?.cancel()
        if (!settings.sound) return
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                runPreview(slide.song, 12.0, slide.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Preview", "anteprima non riuscita: ${e.message}")
            } finally {
                _preview.value = PreviewState()
                audio.allNotesOff()
            }
        }
    }

    private suspend fun runPreview(sg: Song, maxSec: Double, name: String) {
        val sched = NoteScheduler(sg.notes, Hand.BOTH)
        audio.start()
        val end = minOf(sg.durationSec + 1.0, maxSec)
        Log.d("Preview", "avvio: $name, ${sg.notes.size} note")
        val t0 = System.nanoTime()
        var last = -1e-9
        var shown = -1f
        while (currentCoroutineContext().isActive) {
            val t = (System.nanoTime() - t0) / 1e9
            val p = (t / end.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
            if (kotlin.math.abs(p - shown) > 0.004f) { shown = p; _preview.value = PreviewState(true, p) }
            for (e in sched.due(last, t)) if (e.on) audio.noteOn(e.key, e.velocity) else audio.noteOff(e.key)
            last = t
            if (t > end) break
            delay(4)
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        _preview.value = PreviewState()
        audio.allNotesOff()
    }

    /** Importa un file MIDI/MusicXML scelto dall'utente fra i suoi brani. */
    fun importSong(uri: android.net.Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repository.importSong(uri, displayName)
                refreshSongs()
                _error.value = null
                _imported.value = entry
            } catch (e: Exception) {
                run { AppLog.w("ERRORE", "Impossibile importare «$displayName»: ${e.message}"); _error.value = "Impossibile importare «$displayName»: ${e.message}" }
            }
        }
    }

    private val _imported = MutableStateFlow<SongEntry?>(null)
    /** Ultimo brano importato (la schermata lo apre nella scheda del brano). */
    val imported: StateFlow<SongEntry?> = _imported
    /** Solo debug: importa in sequenza tutti i file di una cartella (uno alla volta, per non rovinare l'indice dei brani personali). */
    fun debugImportFolder(dir: java.io.File, replace: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (replace) repository.clearUserSongs()
            // meta.json (facoltativo): per ogni file titolo, autore, nome della foto (cartella covers/) e credito della foto
            val metaJson = runCatching { org.json.JSONObject(java.io.File(dir, "meta.json").readText()) }.getOrNull()
            val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in com.gianlucacelante.midipiano.song.SongRepository.IMPORT_EXTENSIONS }?.sortedBy { it.name }.orEmpty()
            var ok = 0
            for (f in files) {
                try {
                    val m = metaJson?.optJSONObject(f.name)?.let { o ->
                        val cover = o.optString("cover", "").takeIf { it.isNotBlank() }?.let { java.io.File(java.io.File(dir, "covers"), it) }?.takeIf { it.exists() }?.readBytes()
                        com.gianlucacelante.midipiano.song.ImportMeta(o.getString("title"), o.optString("composer", "").ifBlank { null }, o.optString("credit", "").ifBlank { null }, cover)
                    }
                    repository.importSong(android.net.Uri.fromFile(f), f.name, m); ok++; AppLog.i("IMPORT", "importato «${f.name}»")
                } catch (e: Exception) { AppLog.w("IMPORT", "non importato «${f.name}»: ${e.message}") }
            }
            AppLog.i("IMPORT", "cartella finita: $ok su ${files.size}")
            refreshSongs()
        }
    }

    fun consumeImported() { _imported.value = null }

    init {
        input.start()
        com.gianlucacelante.midipiano.Reminder.apply(app, settings.reminderHour) // gli allarmi si perdono con gli aggiornamenti: si riprogramma
        refreshSongs()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _courses.value = courseRepository.load()
                _routines.value = routineRepository.load()
                publishProgress() // con i corsi caricati si può scegliere la prova del giorno
            } catch (e: Exception) {
                run { AppLog.w("ERRORE", "Impossibile leggere i corsi: ${e.message}"); _error.value = "Impossibile leggere i corsi: ${e.message}" }
            }
        }
        publishProgress()
        viewModelScope.launch(Dispatchers.IO) { refreshTakes() }
        viewModelScope.launch(Dispatchers.IO) { runRecords = runStore.load(); publishStats() }
    }

    private fun publishStats() { val all = runRecords; _records.value = all; _stats.value = com.gianlucacelante.midipiano.learn.Analytics.compute(all); publishProgress() }

    /** Registra un giro (finito, fallito o lasciato a metà) con tutto ciò che si è potuto misurare. */
    private fun recordRun(s: PracticeSession, source: String, id: String, title: String, step: Int, outcome: String, stars: Int) {
        val st = s.runStats()
        val timed = s.mode == StudyMode.TEMPO
        val r = com.gianlucacelante.midipiano.learn.RunRecord(
            whenMillis = System.currentTimeMillis(), source = source, id = id, title = title, step = step, mode = s.mode.name, hand = s.hand.name, speed = s.speed,
            outcome = outcome, durationSec = ((System.nanoTime() - runStartNanos) / 1e9).coerceIn(0.0, 3600.0),
            hits = playedNotes(s), misses = if (timed) s.misses else 0, wrong = s.wrongNotes,
            accuracy = if (timed) s.accuracy else playedNotes(s).toDouble() / (playedNotes(s) + s.wrongNotes).coerceAtLeast(1), stars = stars,
            noteHits = st.noteHits, early = st.early, late = st.late, offBeat = st.offBeat, meanDevMs = st.meanDevMs, meanAbsDevMs = st.meanAbsDevMs,
            restEarly = st.restEarly, restLate = st.restLate, restInRest = st.restInRest, holdShort = st.holdShort, holdLong = st.holdLong,
            barErrors = st.barErrors, wrongKeys = st.wrongKeys, missedKeys = st.missedKeys, routine = routineQueue != null,
        )
        runRecords = runRecords + r
        viewModelScope.launch(Dispatchers.IO) { runStore.append(r); publishStats() }
    }

    private fun refreshSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.entries()
                applyGroups(entries, emptyMap(), emptyMap()) // subito l'elenco, poi si rifà col contenuto
                // difficoltà stimata di ogni brano incluso (si legge una volta sola, in secondo piano)
                val loadedAll = entries.mapNotNull { e -> runCatching { e to repository.load(e) }.getOrNull() }
                _songLevels.value = loadedAll.associate { (e, sg) -> e.id to Difficulty.of(sg) }
                _chordSongs.value = loadedAll.filter { (_, sg) -> sg.chordChart || sg.chordSymbols.isNotEmpty() }.associate { (e, sg) -> e.id to sg.chordChart }
                _songVersions.value = loadedAll.associate { (e, sg) -> e.id to com.gianlucacelante.midipiano.learn.Simplifier.availableVersions(sg) }
                applyGroups(entries, _songLevels.value, loadedAll.associate { (e, sg) -> e.id to sg })
                publishProgress() // con i brani caricati si può scegliere la canzone del giorno
            } catch (e: Exception) {
                run { AppLog.w("ERRORE", "Impossibile leggere l'elenco brani: ${e.message}"); _error.value = "Impossibile leggere l'elenco brani: ${e.message}" }
            }
        }
    }

    fun openSong(entry: SongEntry, mode: StudyMode? = null, speed: Double? = null, version: Int = com.gianlucacelante.midipiano.learn.Simplifier.ORIGINAL, style: com.gianlucacelante.midipiano.song.SongRepository.Style? = null, semitones: Int = 0) {
        stopPreview()
        transposeSemis = semitones
        openJob?.cancel() // un solo caricamento alla volta: l'ultimo tocco vince
        openJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = com.gianlucacelante.midipiano.song.Transposer.transpose(com.gianlucacelante.midipiano.learn.Simplifier.version(repository.load(entry, style), version), semitones)
                ensureActive() // se nel frattempo si è tornati indietro o scelto altro, non si applica
                synchronized(lock) {
                    if (!isActive) return@launch // nel frattempo si è usciti: non riaprire
                    startSessionLocked(loaded, null)
                    openedSongId = entry.id
                    progress.lastSongId = entry.id
                    progress.lastSongVersion = version
                    progressStore.save(progress)
                    publishProgress()
                    openedEntry = entry
                    openedStyle = style
                    openedVersion = version
                    if (mode != null) session?.setMode(mode, System.nanoTime())
                    if (speed != null) session?.setSpeed(speed, System.nanoTime())
                    publishLocked() // modalità e velocità appena applicate
                }
                audio.start()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                run { AppLog.w("ERRORE", "Impossibile aprire «${entry.title}»: ${e.message}"); _error.value = "Impossibile aprire «${entry.title}»: ${e.message}" }
            }
        }
    }

    /** Conta il tempo di pratica solo se si sta davvero facendo qualcosa: ogni intervallo tra due attività vale al massimo 20 s. */
    private fun touchPractice(now: Long) {
        if (practiceMark > 0L) practiceAccumNanos += (now - practiceMark).coerceIn(0L, 20_000_000_000L)
        practiceMark = now
    }

    /** Salva nel totale il tempo attivo accumulato (a ogni cambio di esercizio, uscita o app in secondo piano). */
    private fun commitPracticeLocked() {
        val secs = practiceAccumNanos / 1_000_000_000L
        practiceAccumNanos = 0L
        practiceMark = 0L
        if (secs >= 5) {
            progress.recordPractice(secs.coerceAtMost(1800L), LocalDate.now())
            progressStore.save(progress)
            publishProgress()
        }
    }

    private fun startSessionLocked(loaded: Song, exercise: ExerciseSlide?, startStep: Int = 0) {
        commitPracticeLocked() // il tempo dell'esercizio precedente non si perde
        partialRun = false
        AppLog.i("PLAYER", "apre «${loaded.title}» note=${loaded.notes.size} durata=${"%.1f".format(loaded.durationSec)}s battute=${loaded.barCount} esercizio=${exercise?.id ?: "-"}")
        song = loaded
        songExpressive = com.gianlucacelante.midipiano.practice.Dynamics.isExpressive(loaded.notes.map { it.velocity })
        openedSongId = null // lo imposta openSong per i brani liberi
        openedEntry = null
        if (exercise != null) transposeSemis = 0
        songDoneRecorded = false
        take.clear()
        val firstIdx = exercise?.let { ExerciseRunner.startStep(startStep, it.steps.size) } ?: 0
        val first = exercise?.steps?.getOrNull(firstIdx)
        reviewLoop = null
        val sess = PracticeSession(loaded, first?.hand ?: Hand.BOTH)
        sess.countInEnabled = settings.countIn
        sess.speedTrainer = settings.speedTrainer
        sess.autoFlow = exercise == null
        session = sess
        notes = loaded.notes
        sheet = SheetData.build(loaded)
        fingering = com.gianlucacelante.midipiano.notation.Fingering.assign(loaded.notes)
        keyRange = FallingNotes.keyboardRange(loaded.notes)
        meta = Triple(loaded.title, loaded.composer, loaded.source.license)
        scheduler = NoteScheduler(loaded.notes, sess.hand)
        runner = exercise?.let { ExerciseRunner(it.steps, firstIdx) }
        struggle.reset()
        currentExercise = exercise
        exerciseMessage = null
        exerciseStars = null
        runHandled = false
        resetRunData()
        if (first != null) applyStep(sess, first)
        practiceMark = System.nanoTime()
        lastTiming = null
        _clockTime.value = 0f
        if (settings.mic) mic.start()
        publishLocked()
    }

    private fun applyStep(s: PracticeSession, step: LearnStep) {
        val now = System.nanoTime()
        s.setHand(step.hand)
        scheduler = NoteScheduler(s.song.notes, step.hand)
        s.setMode(step.mode, now)
        s.setSpeed(step.speed, now)
        struggle.reset() // un passo nuovo (anche saltato) non eredita le difficoltà del precedente
        partialRun = false
        runGeneration++
        s.restart(now) // ogni passo riparte dall'inizio (con il conto alla rovescia): senza, il giro nuovo eredita la fine del precedente
        _clockTime.value = s.currentTimeSec.toFloat()
        runHandled = false
        resetRunData()
    }

    /** Apre un esercizio di un corso: parte dal primo passo, con mano, modalità e velocità del passo. */
    fun openExercise(slide: ExerciseSlide) {
        stopPreview()
        if (routineQueue?.none { it.id == slide.id } == true) routineQueue = null // un esercizio fuori dalla routine la interrompe
        if (!com.gianlucacelante.midipiano.learn.SightReading.isSight(slide.id) && !isRoutineSlide(slide.id)) progress.lastExerciseId = slide.id // anche lasciato a metà: da qui si riprende
        progressStore.save(progress)
        publishProgress()
        openJob?.cancel()
        // un esercizio lasciato a metà riparte dall'ultimo passo raggiunto (le melodie a prima vista sono sempre nuove)
        val resumeAt = if (com.gianlucacelante.midipiano.learn.SightReading.isSight(slide.id)) 0 else progress.stepOf(slide.id)
        synchronized(lock) { startSessionLocked(slide.song, slide, resumeAt) }
        if (resumeAt > 0) AppLog.i("PLAYER", "esercizio ripreso dal passo ${resumeAt + 1}")
        audio.start()
    }

    /**
     * Gli esercizi della routine di oggi: la routine base più, in coda, quelli dei corsi in cui si sbaglia di più. Il piano si fissa
     * quando si comincia (e non cambia più nella giornata); prima di allora è solo un'anteprima.
     */
    private fun routineQueueFor(course: Course, store: Boolean): List<ExerciseSlide> {
        val today = LocalDate.now()
        val index = (_courses.value + _routines.value).flatMap { it.exercises }.associateBy { it.id }
        progress.dailyPick(today, "routine")?.let { saved ->
            val ids = saved.split(",").filter { it.isNotEmpty() }
            val slides = ids.mapNotNull { index[it] }
            if (slides.isNotEmpty() && slides.size == ids.size) return slides
        }
        val ids = com.gianlucacelante.midipiano.learn.RoutinePlan.planIds(course, settings.routineSize, _stats.value.hardest.map { it.id }.filter { it in index && !com.gianlucacelante.midipiano.learn.SightReading.isSight(it) })
        if (store && ids.isNotEmpty()) { progress.setDailyPick(today, "routine", ids.joinToString(",")); progressStore.save(progress) }
        return ids.mapNotNull { index[it] }
    }

    private fun isRoutineSlide(id: String) = id.startsWith("routine-") || routineQueue?.any { it.id == id } == true

    /** Comincia la routine di oggi: parte dal primo esercizio non ancora fatto (o dal primo, se sono già tutti fatti). */
    fun startRoutine() {
        val course = _routines.value.firstOrNull { it.id == settings.routineId } ?: _routines.value.firstOrNull() ?: return
        val queue = routineQueueFor(course, store = true)
        if (queue.isEmpty()) return
        val next = com.gianlucacelante.midipiano.learn.RoutinePlan.nextTodo(queue, progress.routineExercisesDone(LocalDate.now())) ?: queue.first()
        routineQueue = queue
        AppLog.i("ROUTINE", "inizia «${course.title}»: ${queue.size} esercizi (${queue.count { e -> course.exercises.none { it.id == e.id } }} mirati sugli errori), parte da ${next.title}")
        openExercise(next)
    }

    /** Lettura a prima vista: una melodia nuova del livello raggiunto. */
    fun openSight() {
        val slide = com.gianlucacelante.midipiano.learn.SightReading.generate(progress.sightLevel, System.currentTimeMillis())
        openExercise(slide)
    }

    /** Ricomincia l'esercizio dal primo passo. */
    fun retryExercise() {
        stopPreview()
        val ex = currentExercise ?: return
        progress.clearStep(ex.id); progressStore.save(progress) // «Riprova» è davvero dal primo passo
        synchronized(lock) { startSessionLocked(ex.song, ex) }
    }

    private fun exerciseStatus(): ExerciseStatus? {
        val ex = currentExercise ?: return null
        val r = runner ?: return null
        val step = r.currentStep
        val ss = session
        return ExerciseStatus(
            ex.id, ex.title, ex.text, r.stepIndex, r.totalSteps, step?.text ?: "",
            r.runsInStep, step?.loops ?: 1, exerciseMessage, exerciseStars,
            canSkip = step?.optional == true && !r.isDone && exerciseStars == null,
            pedalExpected = if (ex.pedal != null && ss != null && ss.mode == StudyMode.TEMPO) ex.pedal.stateAt(ss.currentTimeSec) else null,
            pedalDown = pedalDown,
            reviewRange = if (exerciseStars == null && ss != null && reviewLoop == null) struggle.reviewRange(ss.song.barCount) else null,
            reviewing = reviewLoop,
            routinePos = routineQueue?.let { q -> q.indexOfFirst { it.id == ex.id }.takeIf { it >= 0 }?.let { it + 1 to q.size } },
            routineNext = routineQueue?.let { q ->
                val done = progress.routineExercisesDone(LocalDate.now())
                com.gianlucacelante.midipiano.learn.RoutinePlan.nextAfter(q, ex.id, done) ?: com.gianlucacelante.midipiano.learn.RoutinePlan.nextTodo(q, done + ex.id) // dopo l'ultimo, quelli saltati prima
            },
            routineComplete = routineQueue?.let { com.gianlucacelante.midipiano.learn.RoutinePlan.isComplete(it, progress.routineExercisesDone(LocalDate.now())) } == true,
        )
    }

    /** Un brano libero (non un esercizio) suonato fino in fondo sblocca la versione successiva. */
    private fun recordSongDoneLocked(s: PracticeSession) {
        val id = openedSongId ?: return
        if (currentExercise != null || songDoneRecorded || s.mode == StudyMode.LISTEN || !s.isFinished) return
        songDoneRecorded = true
        progress.recordSongDone(id, openedVersion)
        recordRun(s, "song", id, song?.title ?: id, -1, "finished", 0)
        if (s.mode == StudyMode.TEMPO) { // un giro a tempo finito: precisione, battute più sbagliate e registrazione
            val worst = s.barReport().filter { it.notes > 0 && it.errors > 0 }.sortedByDescending { it.errors }.take(3).map { it.bar }.sorted()
            progress.recordRun(id, s.accuracy, worst)
            val title = song?.title ?: id
            val acc = s.accuracy
            val notes = take.toSong()?.notes.orEmpty()
            if (notes.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) {
                runCatching { takeFiles.save(com.gianlucacelante.midipiano.practice.TakeData(title, System.currentTimeMillis(), acc, notes)) }
                refreshTakes()
            }
        }
        if (repOf(id) == progress.dailyPick(LocalDate.now(), "song")?.let(::repOf)) progress.markDailyDone(LocalDate.now(), "song")
        progressStore.save(progress)
        publishProgress()
        AppLog.i("PLAYER", "brano finito: $id versione=$openedVersion")
    }

    /** L'esercizio è finito: stelle, progressi e prova del giorno. */
    private fun finishExerciseLocked(ex: ExerciseSlide, stars: Int) {
        exerciseStars = stars
        exerciseMessage = null
        if (com.gianlucacelante.midipiano.learn.SightReading.isSight(ex.id)) { // melodia generata: conta solo il livello
            progress.recordSight(stars)
            AppLog.i("LETTURA", "melodia finita: stelle=$stars livello ora=${progress.sightLevel}")
        } else {
            progress.recordExercise(ex.id, stars)
            progress.clearStep(ex.id) // finito: la prossima volta si ricomincia dal primo passo
            routineQueue?.let { q ->
                if (q.any { it.id == ex.id }) { // esercizio della routine di oggi
                    val today = LocalDate.now()
                    progress.markRoutineExercise(today, ex.id)
                    if (com.gianlucacelante.midipiano.learn.RoutinePlan.isComplete(q, progress.routineExercisesDone(today))) progress.markRoutineComplete(today)
                }
            }
            if (stars > 0 && ex.id == progress.dailyPick(LocalDate.now(), "trial")) progress.markDailyDone(LocalDate.now(), "trial")
            if (!isRoutineSlide(ex.id)) progress.lastExerciseId = ex.id // la routine non sposta il punto di ripresa dei corsi
        }
        progressStore.save(progress)
        publishProgress()
    }

    /** Salva il passo raggiunto: uscendo e riaprendo l'esercizio si riparte da lì. */
    private fun saveStepLocked(ex: ExerciseSlide, step: Int) {
        if (com.gianlucacelante.midipiano.learn.SightReading.isSight(ex.id)) return
        progress.setStep(ex.id, step)
        if (!isRoutineSlide(ex.id)) progress.lastExerciseId = ex.id
        progressStore.save(progress)
    }

    /** Salta la prova rallentata (facoltativa): si passa al passo dopo o l'esercizio si chiude con le stelle già prese. */
    fun skipOptionalStep() = synchronized(lock) {
        val r = runner ?: return@synchronized
        val ex = currentExercise ?: return@synchronized
        val sess = session ?: return@synchronized
        when (val outcome = r.skipOptionalStep()) {
            is RunOutcome.Done -> { runHandled = true; sess.pause(System.nanoTime()); finishExerciseLocked(ex, outcome.stars) }
            is RunOutcome.StepAdvanced -> {
                exerciseMessage = null
                saveStepLocked(ex, outcome.newIndex)
                r.currentStep?.let { applyStep(sess, it) }
                if (sess.mode == StudyMode.TEMPO) sess.play(System.nanoTime())
            }
            else -> return@synchronized
        }
        publishLocked()
    }

    /** A fine giro dice se è riuscito, assegna le stelle e fa avanzare l'esercizio. */
    private fun checkRunFinishedLocked() {
        val s = session ?: return
        val r = runner ?: return
        val ex = currentExercise ?: return
        if (runHandled || !s.isFinished || r.isDone || s.mode == StudyMode.LISTEN || reviewLoop != null) return
        runHandled = true
        if (partialRun) { // giro ripartito a metà: non vale, si rifà dall'inizio
            partialRun = false
            exerciseMessage = "Eri ripartito a metà: ora il giro dall'inizio."
            scheduleRunRestart(ex, r, runGeneration, advanced = false)
            return
        }
        val stepBefore = r.stepIndex
        val errorsByBar = barErrors(s) // prima di qualunque ripartenza
        val allowed = max(2, s.totalSteps / 8)
        var passed: Boolean
        val stars: Int
        if (s.mode == StudyMode.WAIT) {
            passed = s.mistakes <= allowed
            stars = Awards.stars(StudyMode.WAIT, 1.0, 1.0, s.mistakes)
        } else {
            passed = s.hits > 0 && s.accuracy >= 0.8 && s.wrongNotes <= allowed
            // la velocità la prescrive il passo (l'utente non può cambiarla): il tetto delle 2 stelle sotto il 99 % non vale negli esercizi
            stars = Awards.stars(StudyMode.TEMPO, 1.0, s.accuracy, s.mistakes)
        }
        // tocco e pedale: contano solo se la tastiera dà la forza vera / si è usato il pedale
        val notes = ArrayList<String>()
        var extraFail = false
        ex.touch?.let { goal ->
            com.gianlucacelante.midipiano.practice.Touch.evaluate(goal, runVel, runTarget, runHits)?.let { t -> notes += t.text; if (!t.passed) extraFail = true }
        }
        if (ex.pedal != null && s.mode == StudyMode.TEMPO) {
            val tol = (ex.song.secPerBeat * 0.5).coerceIn(0.35, 0.7)
            val pr = ex.pedal.judge(pedalLog.toList(), tol)
            notes += pr.text
            if (!pr.passed) extraFail = true
            AppLog.i("PEDALE", "giro: cambi giusti=${pr.matched}/${pr.expected} di troppo=${pr.extras} fatti=${pedalLog.size}")
        }
        if (notes.isNotEmpty()) AppLog.i("TOCCO", "giro: ${notes.joinToString(" ")} forze=${runVel.take(24)}")
        if (extraFail) passed = false
        val noteText = notes.joinToString(" ")
        val generation = runGeneration
        val outcome = r.onRunFinished(passed, stars)
        AppLog.i("GIRO", com.gianlucacelante.midipiano.learn.RunLog.finished(
            ex.id, stepBefore, s.mode.name, s.speed, playedNotes(s), if (s.mode == StudyMode.TEMPO) s.mistakes - s.wrongNotes else 0, s.wrongNotes, s.accuracy, stars, passed, outcome::class.simpleName ?: "?"))
        recordRun(s, "exercise", ex.id, ex.title, stepBefore, if (passed) "passed" else "failed", stars)
        if (outcome is RunOutcome.Retry) struggle.onFailedRun(errorsByBar) else struggle.onGoodRun()
        if (outcome is RunOutcome.StepAdvanced) saveStepLocked(ex, outcome.newIndex) // i passi superati restano salvati
        when (outcome) {
            is RunOutcome.NextRun -> exerciseMessage = "Bene! Ancora ${outcome.runsLeft} ${if (outcome.runsLeft == 1) "volta" else "volte"}." + if (noteText.isNotEmpty()) " $noteText" else ""
            is RunOutcome.Retry -> exerciseMessage = (if (extraFail) "Riprova. " else "Riprova: non è ancora un giro pulito. ") + noteText
            is RunOutcome.StepAdvanced -> exerciseMessage = "Passo superato! Ora il successivo." + if (noteText.isNotEmpty()) " $noteText" else ""
            is RunOutcome.Done -> finishExerciseLocked(ex, outcome.stars)
        }
        if (outcome !is RunOutcome.Done) scheduleRunRestart(ex, r, generation, advanced = outcome is RunOutcome.StepAdvanced)
    }

    /** Dopo un attimo (si legge il messaggio) il giro ricomincia, o parte il passo nuovo, se nel frattempo non è già ripartito. */
    private fun scheduleRunRestart(ex: ExerciseSlide, r: ExerciseRunner, generation: Int, advanced: Boolean) {
        viewModelScope.launch {
            delay(1600)
            synchronized(lock) {
                val sess = session ?: return@launch
                if (currentExercise !== ex || runner !== r || generation != runGeneration) return@launch // nel frattempo il giro è già ripartito
                exerciseMessage = null
                val step = r.currentStep
                if (advanced && step != null) applyStep(sess, step) else {
                    sess.restart(System.nanoTime())
                    runHandled = false
                    resetRunData()
                    _clockTime.value = sess.currentTimeSec.toFloat()
                }
                if (sess.mode == StudyMode.TEMPO) sess.play(System.nanoTime())
                publishLocked()
            }
        }
    }

    fun toggleFavorite(songId: String) = synchronized(lock) {
        progress.toggleFavorite(songId)
        progressStore.save(progress)
        publishProgress()
    }

    /** Sceglie (una volta per giorno) la canzone e la prova del giorno, appena brani e corsi sono caricati. */
    private fun ensureDailyPicks(today: LocalDate) {
        val songIds = _songs.value.map { it.id }.filterNot { it.startsWith("esempio") }
        if (progress.dailyPick(today, "song") == null) DailyChallenge.songOfDay(today, songIds)?.let { progress.setDailyPick(today, "song", it) }
        if (progress.dailyPick(today, "trial") == null) {
            val ids = _courses.value.flatMap { c -> c.exercises.map { it.id } }
            DailyChallenge.trialOfDay(today, ids) { progress.starsOf(it) }?.let { progress.setDailyPick(today, "trial", it) }
        }
    }

    private fun publishProgress() = synchronized(lock) { // il lock è rientrante: va bene anche da dentro altre sezioni protette
        val today = LocalDate.now()
        ensureDailyPicks(today)
        val stars = progress.allStars()
        val coursesDone = _courses.value.count { c -> c.exercises.isNotEmpty() && c.exercises.all { (stars[it.id] ?: 0) > 0 } }
        val achievements = com.gianlucacelante.midipiano.learn.Achievements.compute(
            com.gianlucacelante.midipiano.learn.Achievements.Input(
                exercises = progress.exercisesDone(), threeStars = stars.values.count { it >= 3 },
                songs = progress.songsDoneIds().map { it.substringBefore(':') }.toSet().size, streak = progress.streak(today),
                minutes = (progress.totalPracticeSeconds / 60).toInt(), courses = coursesDone, dailyDays = progress.dailyDaysDone(),
                takes = _takes.value.size, runs = progress.runCount(),
            ),
        )
        val calendar = (34 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to when { progress.isDailyDone(d, "song") && progress.isDailyDone(d, "trial") -> 2; progress.practicedOn(d) || progress.isDailyDone(d, "song") || progress.isDailyDone(d, "trial") -> 1; else -> 0 }
        }
        val routineBase = _routines.value.let { rs -> rs.firstOrNull { it.id == settings.routineId } ?: rs.firstOrNull() }
        val routineQueueNow = routineBase
            ?.let { routineQueueFor(it, store = false) }?.takeIf { it.isNotEmpty() } // dimensione e routine di adesso: cambiarle a metà giornata non lascia stati incoerenti
        val runs = progress.songsWithRuns().associateWith { progress.runsOf(it) }
        _progress.value = ProgressSnapshot(
            progress.allStars(), progress.streak(LocalDate.now()), progress.totalPracticeSeconds,
            progress.favoriteIds(), progress.lastExerciseId, progress.exercisesDone(), progress.songsDoneIds(),
            progress.secondsOn(today), progress.lastSongId?.let(::repOf), progress.lastSongVersion,
            progress.dailyPick(today, "song")?.let(::repOf), progress.dailyPick(today, "trial"), progress.isDailyDone(today, "song"), progress.isDailyDone(today, "trial"),
            com.gianlucacelante.midipiano.learn.Stats.minutesByDay(progress, today, 14), calendar, runs,
            runs.keys.associateWith { progress.weakBarsOf(it) }, achievements,
            progress.sightLevel, progress.sightDone, progress.chordLevel, progress.chordCorrectTotal,
            progress.ear.getValue("interval").level, progress.ear.getValue("interval").total, progress.ear.getValue("melody").level, progress.ear.getValue("melody").total,
            routineDoneToday = progress.routineExercisesDone(today), routineStreak = progress.routineStreak(today),
            routineCompletedToday = routineQueueNow?.let { com.gianlucacelante.midipiano.learn.RoutinePlan.isComplete(it, progress.routineExercisesDone(today)) } ?: progress.routineCompleted(today), routineTotalToday = routineQueueNow?.size ?: 0, routineQueueIds = routineQueueNow?.map { it.id }.orEmpty(), routineTargeted = routineQueueNow?.count { e -> routineBase?.exercises?.none { it.id == e.id } == true } ?: 0, routineDays = progress.routineDaysDone(),
            dayFlags = (0L..179L).associate { back ->
                val d = today.minusDays(back)
                d to DayFlags(progress.secondsOn(d), progress.isDailyDone(d, "song"), progress.isDailyDone(d, "trial"), progress.routineCompleted(d), progress.practicedOn(d))
            }.filterValues { it.any },
        )
    }

    fun closeSong() {
        stopPreview()
        AppLog.i("PLAYER", "chiude il brano")
        openJob?.cancel()
        autoplayJob?.cancel()
        audio.allNotesOff()
        mic.stop()
        _pressed.value = emptySet() // niente tasti "rimasti accesi" uscendo dalla schermata
        _wrong.value = emptySet()
        synchronized(lock) {
            val sOut = session
            val exOut = currentExercise
            if (sOut != null && exOut != null && exerciseStars == null && !sOut.isFinished && reviewLoop == null && playedNotes(sOut) + sOut.wrongNotes >= 3) {
                recordRun(sOut, "exercise", exOut.id, exOut.title, runner?.stepIndex ?: 0, "abandoned", 0) // uscito a metà giro
            }
            val freeId = openedSongId
            if (sOut != null && exOut == null && freeId != null && sOut.mode == StudyMode.TEMPO && !sOut.isFinished && playedNotes(sOut) + sOut.wrongNotes >= 3) {
                recordRun(sOut, "song", freeId, song?.title ?: freeId, -1, "abandoned", 0) // brano libero a tempo lasciato a metà
            }
            routineQueue = null
            sOut?.pause(System.nanoTime())
            commitPracticeLocked()
            session = null
            song = null
            runner = null
            currentExercise = null
            _practice.value = null
        }
    }

    // ---- trasporto e opzioni di studio ----

    fun setHand(hand: Hand) = withSession {
        it.setHand(hand)
        scheduler = NoteScheduler(it.song.notes, hand)
    }

    fun restart() = withSession { take.clear(); it.restart(System.nanoTime()); _clockTime.value = it.currentTimeSec.toFloat() }

    /** Salta a una posizione del brano (0..1), dalla barra di avanzamento. Negli esercizi non si salta. */
    fun seek(fraction: Float) = withSession { s ->
        AppLog.i("PLAYER", "salto a ${"%.0f".format(fraction * 100)}%")
        if (currentExercise != null && reviewLoop == null) {
            // saltando avanti si otterrebbero le stelle senza suonare; tornando indietro si ricomincia il giro, ma dal punto in cui si lascia il cursore
            val target = fraction.coerceIn(0f, 1f) * s.song.durationSec
            if (com.gianlucacelante.midipiano.learn.seekRestartsExerciseRun(target, s.currentTimeSec, exerciseStars != null)) restartRunLocked(s, fromSec = target, reason = "indietro")
            return@withSession
        }
        val now = System.nanoTime()
        val t = fraction.coerceIn(0f, 1f) * s.song.durationSec
        s.seekTo(t, now) // per tempo: vale per tutte le modalità (in "aspetta" si ferma sul passo corrispondente)
        if (s.mode != StudyMode.WAIT) {
            audio.allNotesOff() // un salto in avanti non deve far suonare in raffica le note saltate
            lastListenTime = t - 1e-9
        }
        _clockTime.value = s.currentTimeSec.toFloat()
    }

    /** Ricomincia il giro in corso dell'esercizio (stesso passo) da [fromSec] (0 = dall'inizio); i giri già riusciti restano. */
    private fun restartRunLocked(s: PracticeSession, fromSec: Double = 0.0, reason: String = "da capo") {
        val now = System.nanoTime()
        // un giro cominciato davvero e lasciato a metà conta come tentativo non riuscito (serve a capire dove si fatica)
        val ex = currentExercise
        if (ex != null && !s.isFinished && playedNotes(s) + s.wrongNotes >= 3) {
            AppLog.i("GIRO", com.gianlucacelante.midipiano.learn.RunLog.abandoned(ex.id, runner?.stepIndex ?: 0, s.mode.name, s.currentTimeSec, playedNotes(s), s.wrongNotes, reason))
            recordRun(s, "exercise", ex.id, ex.title, runner?.stepIndex ?: 0, "abandoned", 0)
            struggle.onFailedRun(barErrors(s))
        }
        AppLog.i("PLAYER", "esercizio: giro ricominciato da ${"%.1f".format(fromSec)} s")
        audio.allNotesOff()
        runGeneration++
        s.restart(now)
        partialRun = fromSec > 0.05
        if (fromSec > 0.05) { s.seekTo(fromSec, now); lastListenTime = fromSec - 1e-9 }
        runHandled = false
        resetRunData()
        exerciseMessage = null
        _clockTime.value = s.currentTimeSec.toFloat()
        if (s.mode == StudyMode.TEMPO) s.play(now)
    }

    /** Note/passi suonati bene in questo giro: a tempo le note colpite, in «Impara» i passi completati. */
    private fun playedNotes(s: PracticeSession): Int = if (s.mode == StudyMode.WAIT) s.stepsPlayedThisRun else s.hits

    private fun barErrors(s: PracticeSession): Map<Int, Int> = s.barReport().filter { it.errors > 0 }.associate { it.bar to it.errors }

    /** Il tasto «Da capo» della barra dell'esercizio. */
    fun restartExerciseRun() = withSession { s -> if (currentExercise != null && exerciseStars == null) restartRunLocked(s) }

    fun seekToBar(bar: Int) = withSession { s ->
        if (currentExercise != null && reviewLoop == null) return@withSession
        s.seekToBar(bar, System.nanoTime())
        audio.allNotesOff()
        lastListenTime = s.currentTimeSec - 1e-9
        _clockTime.value = s.currentTimeSec.toFloat()
    }

    fun setMode(mode: StudyMode) = withSession {
        AppLog.i("PLAYER", "modo=$mode")
        it.setMode(mode, System.nanoTime())
        _clockTime.value = it.currentTimeSec.toFloat()
    }

    fun togglePlay() = withSession {
        AppLog.i("PLAYER", if (it.isPlaying) "pausa t=${"%.2f".format(it.currentTimeSec)}" else "play modo=${it.mode} t=${"%.2f".format(it.currentTimeSec)}")
        val now = System.nanoTime()
        if (it.isPlaying) {
            it.pause(now)
            audio.allNotesOff()
        } else {
            it.play(now)
            _clockTime.value = it.songTimeAt(now).toFloat() // subito -3 s (conto alla rovescia), non 0
        }
        lastCountInBeat = null
        lastBeatIdx = Int.MIN_VALUE
        lastListenTime = it.songTimeAt(now) - 1e-9
        lastBackingTime = it.songTimeAt(now) - 1e-9
    }

    /** Trasporta il brano aperto di [semitones] (−6…+6): si riapre alla stessa modalità e velocità, dall'inizio. */
    fun setTranspose(semitones: Int) {
        val entry = openedEntry ?: return
        val s = session ?: return
        val target = semitones.coerceIn(-6, 6)
        if (target == transposeSemis) return
        AppLog.i("PLAYER", "trasposizione $target semitoni")
        openSong(entry, s.mode, s.speed, openedVersion, openedStyle, target)
    }

    /** Anteprima saltabile: si sente il brano (o l'esercizio) a tempo giusto prima di cominciare a suonare. */
    fun listenFirst() = withSession { s ->
        if (s.isPreviewing || s.mode == StudyMode.LISTEN) return@withSession
        AppLog.i("PLAYER", "anteprima di ascolto")
        runGeneration++ // un job di fine giro già in attesa non deve toccare l'anteprima
        take.clear()
        s.beginPreview(System.nanoTime())
        scheduler = NoteScheduler(s.song.notes, Hand.BOTH)
        _clockTime.value = s.currentTimeSec.toFloat()
    }

    fun skipListenFirst() = withSession { endListenFirstLocked(it) }

    private fun endListenFirstLocked(s: PracticeSession) {
        if (!s.isPreviewing) return
        s.endPreview(System.nanoTime())
        scheduler = NoteScheduler(s.song.notes, s.hand)
        audio.allNotesOff()
        _clockTime.value = s.currentTimeSec.toFloat()
    }

    fun setSpeed(speed: Double) = withSession {
        AppLog.i("PLAYER", "velocità=$speed"); it.setSpeed(speed, System.nanoTime()) }

    fun setCountIn(enabled: Boolean) = withSession { it.countInEnabled = enabled }

    /**
     * Ripasso mirato di un esercizio: lo stesso brano come studio libero, in loop sulle battute indicate, a 75 % e in "Aspetta"
     * (si lavora il punto difficile senza ansia). Si esce con il tasto indietro.
     */
    fun reviewExerciseBars(first: Int, last: Int) {
        val ex = currentExercise ?: return
        AppLog.i("PLAYER", "ripasso mirato dell'esercizio ${ex.id}: battute $first-$last")
        stopPreview()
        synchronized(lock) {
            val s = session ?: return
            val now = System.nanoTime()
            audio.allNotesOff()
            runGeneration++ // un job di fine giro già in attesa non deve interferire col ripasso
            reviewLoop = first..last // l'esercizio resta aperto (passo, giri, stelle): il ripasso non conta niente
            // lo stesso tipo di prova del passo in cui si è (mano, modalità, velocità): a tempo se si stava suonando a tempo
            val step = runner?.currentStep
            s.setHand(step?.hand ?: s.hand)
            scheduler = NoteScheduler(s.song.notes, s.hand)
            s.setMode(step?.mode ?: StudyMode.WAIT, now)
            s.setSpeed(step?.speed ?: 0.75, now)
            s.setLoopBars(first, last)
            s.restart(now)
            if (s.mode == StudyMode.TEMPO) s.play(now)
            exerciseMessage = null
            _clockTime.value = s.currentTimeSec.toFloat()
            publishLocked()
        }
        audio.start()
    }

    /** Fine del ripasso: si torna all'esercizio dal passo in cui si era (o al riepilogo se era già finito). */
    fun endExerciseReview() = synchronized(lock) {
        val s = session ?: return@synchronized
        if (reviewLoop == null) return@synchronized
        AppLog.i("PLAYER", "fine ripasso: si torna all'esercizio")
        reviewLoop = null
        audio.allNotesOff()
        s.setLoopBars(null, null)
        val step = runner?.currentStep
        if (exerciseStars == null && step != null) {
            applyStep(s, step)
            if (s.mode == StudyMode.TEMPO) s.play(System.nanoTime())
        } else {
            s.restart(System.nanoTime()) // esercizio finito: si rivede il riepilogo
        }
        publishLocked()
    }

    /** Indietro: durante un ripasso si torna all'esercizio, altrimenti si esce. */
    fun onPracticeBack() { if (reviewLoop != null) endExerciseReview() else closeSong() }

    /** Ripasso: loop sulle battute indicate, ricominciando da lì. */
    fun reviewBars(first: Int, last: Int) = withSession {
        AppLog.i("PLAYER", "ripasso battute $first-$last")
        take.clear()
        it.setLoopBars(first, last)
        it.restart(System.nanoTime())
        _clockTime.value = it.currentTimeSec.toFloat()
    }

    fun setLoop(startBar: Int?, endBar: Int?) = withSession {
        AppLog.i("PLAYER", "loop $startBar-$endBar"); it.setLoopBars(startBar, endBar) }

    /** Applica e salva le impostazioni dell'utente. */
    fun updateSettings(new: Settings) = synchronized(lock) {
        val old = settings
        settings = new
        AppLog.i("IMPOSTAZIONI", "suono=${new.sound} suoniMidi=${new.midiSounds} metronomo=${new.metronome} contoAlla=${new.countIn} dita=${new.fingers}")
        _settings.value = new
        appSettings.save(new)
        session?.countInEnabled = new.countIn
        session?.speedTrainer = new.speedTrainer
        if (new.mic != old.mic) if (new.mic && session != null) mic.start() else mic.stop()
        if (!new.sound) audio.allNotesOff()
        if (new.metronome != old.metronome) lastBeatIdx = Int.MIN_VALUE
        if (new.reminderHour != old.reminderHour) com.gianlucacelante.midipiano.Reminder.apply(getApplication(), new.reminderHour)
        if (new.routineId != old.routineId || new.routineSize != old.routineSize) { progress.clearDailyPick(LocalDate.now(), "routine"); progressStore.save(progress); publishProgress() } // un'altra routine o dimensione: il piano di oggi si rifà
        publishLocked() // senza sessione non fa nulla: le impostazioni sono comunque salvate
    }

    /** Da chiamare a ogni fotogramma mentre l'orologio corre. */
    fun onFrame(frameNanos: Long) {
        synchronized(lock) {
            val s = session ?: return
            if (!s.isPlaying) return
            touchPractice(System.nanoTime())
            val t = s.tick(frameNanos)
            _clockTime.value = t.songTimeSec.toFloat()
            clickOnBeats(s, t.songTimeSec)
            if (s.mode == StudyMode.LISTEN) playListened(t.songTimeSec)
            playBacking(s, t.songTimeSec)
            val beat = s.countInBeat
            // Si ripubblica quando cambia qualcosa di visibile: eventi, battuta, tasti da suonare, conto alla rovescia
            val bar = song?.barAt(t.songTimeSec.coerceAtLeast(0.0)) ?: 0
            val expected = s.expectedKeys
            val beatIdx = if (settings.visualBeat && s.mode != StudyMode.WAIT) (song?.beatIndexAt(t.songTimeSec) ?: -1) else -1
            val beatChanged = beatIdx != lastBeatIdxShown
            val pedalNow = currentExercise?.pedal?.let { if (s.mode == StudyMode.TEMPO) it.stateAt(t.songTimeSec) else null }
            val pedalChanged = pedalNow != lastPedalExpected
            lastPedalExpected = pedalNow
            if (t.finished && s.isPreviewing) endListenFirstLocked(s) // l'anteprima è finita: si torna pronti a suonare
            else if (t.finished) { checkRunFinishedLocked(); recordSongDoneLocked(s) }
            if (t.looped || t.finished || t.newMisses > 0 || beat != lastCountInBeat || bar != lastBar || expected != lastExpected || beatChanged || pedalChanged) {
                lastBeatIdxShown = beatIdx
                lastCountInBeat = beat
                lastBar = bar
                lastExpected = expected
                publishLocked()
            }
        }
    }

    /** Accompagnamento: l'app suona l'altra mano mentre si studia una mano sola in "A tempo". */
    private fun playBacking(s: PracticeSession, t: Double) {
        val sg = song ?: return
        val other = Backing.handToPlay(s.hand)
        if (other == null || !Backing.active(settings.backing, s.mode, s.hand, settings.sound, s.isPreviewing, s.isCountingIn)) {
            if (backingSched != null) { backingSched = null; backingFor = null }
            return
        }
        if (backingFor != sg to other) { backingSched = NoteScheduler(sg.notes, other); backingFor = sg to other; lastBackingTime = t - 1e-9 }
        val sched = backingSched ?: return
        if (t < lastBackingTime - 1e-6 || t - lastBackingTime > 0.5) { // salto (giro del loop o spostamento): si ricomincia da qui, senza raffica di note
            audio.allNotesOff()
            lastBackingTime = t - 1e-9
        }
        for (e in sched.due(lastBackingTime, t)) if (e.on) audio.noteOn(e.key, (e.velocity * 0.8).toInt().coerceAtLeast(1)) else audio.noteOff(e.key)
        lastBackingTime = t
    }

    /** Modalità Ascolta: fa suonare le note del brano man mano che il tempo avanza. */
    private fun playListened(t: Double) {
        val sched = scheduler ?: return
        if (t < lastListenTime - 1e-6) { // salto indietro (loop o spostamento): si azzera
            audio.allNotesOff()
            lastListenTime = t - 1e-9
        }
        if (settings.sound) {
            for (e in sched.due(lastListenTime, t)) if (e.on) audio.noteOn(e.key, e.velocity) else audio.noteOff(e.key)
        }
        lastListenTime = t
    }

    /** "Tic" a ogni movimento: sempre durante il conto alla rovescia, poi solo con il metronomo attivo. */
    private fun clickOnBeats(s: PracticeSession, songTime: Double) {
        if (!settings.sound) return
        val sg = song ?: return
        val region = s.regionStartSec
        val counting = songTime < region
        // conto alla rovescia: movimenti regolari prima della regione; poi la griglia del brano (segue cambi di tempo e di metro)
        val gridIdx = if (counting) -1 else sg.beatIndexAt(songTime)
        val idx = if (counting) floor((songTime - region) / sg.beatSecAt(region)).toInt() else 1_000_000 + gridIdx
        if (idx == lastBeatIdx) return
        lastBeatIdx = idx
        if (counting) audio.click(accent = Math.floorMod(idx, sg.beatsPerBarAt(region)) == 0)
        else if (settings.metronome && gridIdx >= 0) audio.click(accent = sg.beatGrid[gridIdx].accent)
    }

    /** Il pedale è cambiato (dalla tastiera o dal pulsante): negli esercizi con pedale si segna a che punto del brano. */
    private fun onPedalChanged(down: Boolean) = synchronized(lock) {
        if (pedalDown == down) return@synchronized
        pedalDown = down
        val s = session
        if (s != null && currentExercise?.pedal != null && s.mode == StudyMode.TEMPO && s.isPlaying && !s.isFinished) pedalLog.add(s.currentTimeSec to down)
        publishLocked()
    }

    /** Pedale sullo schermo, per chi non ha il pedale (o per provare). */
    fun togglePedal() {
        val down = !pedalDown
        if (settings.sound) audio.sustain(down)
        onPedalChanged(down)
    }

    /** Fa sentire un accordo (per l'allenamento «ascolta e riconosci»). */
    fun playChord(keys: List<Int>) {
        audio.start()
        viewModelScope.launch {
            for (k in keys) audio.noteOn(k, 80)
            delay(1400)
            for (k in keys) audio.noteOff(k)
        }
    }

    private var sequenceJob: Job? = null

    /** Fa sentire delle note una dopo l'altra (intervalli e dettato melodico). */
    fun playSequence(keys: List<Int>, gapMs: Long = 650L) {
        audio.start()
        sequenceJob?.cancel()
        sequenceJob = viewModelScope.launch {
            for (k in keys) {
                audio.noteOn(k, 85)
                delay((gapMs * 0.8).toLong())
                audio.noteOff(k)
                delay((gapMs * 0.2).toLong())
            }
        }
    }

    fun recordEarAnswer(kind: String, correct: Boolean) = synchronized(lock) {
        progress.recordEarAnswer(kind, correct)
        AppLog.i("ORECCHIO", "$kind: risposta ${if (correct) "giusta" else "sbagliata"}, livello=${progress.ear.getValue(kind).level}")
        progressStore.save(progress)
        publishProgress()
    }

    fun recordChordAnswer(correct: Boolean) = synchronized(lock) {
        progress.recordChordAnswer(correct)
        AppLog.i("ACCORDI", "risposta ${if (correct) "giusta" else "sbagliata"}: livello=${progress.chordLevel} giuste in tutto=${progress.chordCorrectTotal}")
        progressStore.save(progress)
        publishProgress()
    }

    fun micPermitted(): Boolean = mic.hasPermission()

    /** L'app esce dallo schermo: si ferma il brano e si libera l'audio (l'orologio non deve andare avanti da solo). */
    fun onBackground() = synchronized(lock) {
        val s = session
        if (s != null && s.isPlaying) {
            s.pause(System.nanoTime())
            publishLocked()
            Log.d("Lifecycle", "in background: brano fermato a ${"%.2f".format(s.songTimeAt(System.nanoTime()))} s")
        }
        audio.allNotesOff()
        audio.stop()
        mic.stop()
        commitPracticeLocked()
    }

    fun onForeground() {
        if (session != null) {
            audio.start()
            if (settings.mic) mic.start()
        }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Tasto toccato sullo schermo: utile per provare senza tastiera MIDI. */
    fun pressKey(note: Int, velocity: Int = 90) {
        _pressed.update { it + note }
        noteOn(note, velocity, fromMidi = false)
    }

    fun releaseKey(note: Int) {
        noteReleased(note)
        _pressed.update { it - note }
        _wrong.update { it - note }
        audio.noteOff(note)
    }

    private fun noteOn(note: Int, velocity: Int, fromMidi: Boolean) {
        val now = System.nanoTime()
        _pressed.update { it + note }
        if (session?.let { it.mode != StudyMode.LISTEN && !it.isFinished } == true) take.noteOn(note, velocity, now)
        if (settings.sound && (!fromMidi || settings.midiSounds)) audio.noteOn(note, velocity)
        synchronized(lock) {
            val s = session ?: return
            touchPractice(now)
            val expectedBefore = s.expectedKeys
            val targetVel = if (currentExercise?.touch != null) s.expectedNotes().firstOrNull { it.key == note }?.velocity else null
            if (fromMidi) _press.value = PressInfo(velocity, if (songExpressive) s.expectedNotes().firstOrNull { it.key == note }?.velocity else null, ++pressCounter)
            s.checkHolds = currentExercise != null // la durata delle note si controlla negli esercizi
            val result = s.onNoteOn(note, now)
            consumeTimingLocked(s)
            AppLog.i("NOTA", "nota=$note ${if (fromMidi) "midi" else "schermo"} esito=$result modo=${s.mode} attese=$expectedBefore t=${"%.2f".format(s.currentTimeSec)} passo=${s.stepIndex}/${s.totalSteps} errori=${s.mistakes}")
            if (currentExercise?.touch != null && (result == com.gianlucacelante.midipiano.practice.NoteResult.CORRECT || result == com.gianlucacelante.midipiano.practice.NoteResult.STEP_COMPLETED || result == com.gianlucacelante.midipiano.practice.NoteResult.FINISHED)) {
                runHits++
                runVel.add(if (fromMidi) velocity else 0) // 0 = tasto sullo schermo: senza forza vera
                runTarget.add(targetVel)
            }
            if (result == com.gianlucacelante.midipiano.practice.NoteResult.WRONG) _wrong.update { it + note }
            if (s.mode == StudyMode.WAIT) _clockTime.value = s.currentTimeSec.toFloat()
            checkRunFinishedLocked()
            recordSongDoneLocked(s) // un brano libero finito con l'ultima nota (es. «Impara» a orologio fermo)
            publishLocked()
        }
    }

    /** Prende il commento di ritmo/durata appena formulato dalla sessione e lo mette a disposizione dell'interfaccia. */
    private fun consumeTimingLocked(s: PracticeSession) {
        val fb = s.takeTimingFeedback() ?: return
        lastTiming = fb.copy(id = ++timingCounter)
        lastTimingNanos = System.nanoTime()
        AppLog.i("RITMO", "${fb.kind} ${"%.1f".format(fb.seconds)} s, ${"%.2f".format(fb.beats)} battiti (${s.mode})")
    }

    /** Un tasto è stato rilasciato: serve a giudicare per quanto a lungo è stato tenuto. */
    private fun noteReleased(note: Int) {
        val now = System.nanoTime()
        synchronized(lock) {
            val s = session ?: return
            s.onNoteOff(note, now)
            if (s.takeTimingFeedbackPeek()) { consumeTimingLocked(s); publishLocked() }
        }
    }

    private inline fun withSession(block: (PracticeSession) -> Unit) = synchronized(lock) {
        val s = session ?: return
        block(s)
        publishLocked()
    }

    private fun publishLocked() {
        val s = session ?: return
        val sg = song ?: return
        val expectedNow = s.expectedNotes()
        val upcoming = s.upcomingNotes()
        _practice.value = PracticeSnapshot(
            title = meta.first, composer = meta.second, license = meta.third,
            hand = s.hand, mode = s.mode, isPlaying = s.isPlaying, countInBeat = s.countInBeat,
            speed = s.speed, settings = settings, loopBars = s.loopBars,
            loopSec = s.loopBars?.let { sg.barStartSec(it.first)..sg.barEndSec(it.last) }, loopsCompleted = s.loopsCompleted,
            stepIndex = s.stepIndex, totalSteps = s.totalSteps,
            barCount = sg.barCount, currentBar = sg.barAt(s.currentTimeSec.coerceAtLeast(0.0)),
            expectedKeys = s.expectedKeys, mistakes = s.mistakes, hits = s.hits, accuracy = s.accuracy,
            isFinished = s.isFinished, currentTimeSec = s.currentTimeSec, durationSec = sg.durationSec, progress = s.progress,
            beatInBar = if (s.mode != StudyMode.WAIT && s.isPlaying && s.countInBeat == null) sg.beatPositionAt(s.currentTimeSec)?.first else null,
            beatsInBar = sg.beatPositionAt(s.currentTimeSec.coerceAtLeast(0.0))?.second ?: sg.beatsPerBar,
            exerciseReview = if (currentExercise != null && reviewLoop == null && (exerciseStars ?: 3) < 3) com.gianlucacelante.midipiano.practice.BarReport.reviewRange(s.barReport()) else null,
            barStats = if (s.isFinished && currentExercise == null && s.mode != StudyMode.LISTEN) s.barReport() else emptyList(),
            listeningFirst = s.isPreviewing,
            timing = if (s.isFinished && s.mode == StudyMode.TEMPO) s.timingSummary() else null,
            transpose = transposeSemis, canTranspose = openedEntry != null && currentExercise == null && !sg.chordChart,
            notes = notes, sheet = sheet, keyRange = keyRange, exercise = exerciseStatus(),
            noteState = noteStateFn(s, s.mode, s.expectedKeys, s.currentTimeSec),
            upcomingHands = upcoming.associate { it.key to it.hand },
            upcomingWeights = s.upcomingWeights(upcoming),
            upcomingTimes = if (s.mode == StudyMode.TEMPO) upcoming.associate { it.key to it.startSec } else emptyMap(),
            timingFeedback = lastTiming?.takeIf { System.nanoTime() - lastTimingNanos < 2_200_000_000L }, // dopo 2 s non si ripropone (rotazione, ricomposizione)
            upcomingFingers = upcoming.mapNotNull { n -> fingering[com.gianlucacelante.midipiano.notation.Fingering.id(n.startSec, n.key)]?.let { n.key to it } }.toMap(),
            expectedHands = expectedNow.associate { it.key to it.hand },
            fingers = expectedNow.mapNotNull { n -> fingering[com.gianlucacelante.midipiano.notation.Fingering.id(n.startSec, n.key)]?.let { n.key to it } }.toMap(),
        )
    }

    /**
     * Stato di una nota per il disegno. Tasti attesi e tempo sono copiati al momento della pubblicazione:
     * il disegno gira sul thread principale mentre le note MIDI arrivano da un altro thread.
     */
    private fun noteStateFn(s: PracticeSession, mode: StudyMode, expected: Set<Int>, time: Double): (StaffNote) -> NoteState = { n ->
        if (mode == StudyMode.TEMPO) {
            when (s.hitStateOf(n.key, n.startSec)) {
                HitState.HIT -> if (s.isOffBeat(n.key, n.startSec) == true) NoteState.PLAYED_OFF else NoteState.PLAYED
                HitState.SKIPPED -> NoteState.PLAYED
                HitState.MISSED -> NoteState.MISSED
                else -> if (abs(n.startSec - _clockTime.value) < 0.2) NoteState.CURRENT else NoteState.UPCOMING
            }
        } else {
            NotationLayout.stateOf(n, time, expected)
        }
    }

    // ---- simulatore di esecuzione (solo debug): suona come farebbe una persona ----

    /** Suona un giro dei passi dati: a tempo (all'istante previsto ± errore) o aspettando; le note restano premute per la loro durata. */
    private suspend fun playRun(steps: List<com.gianlucacelante.midipiano.song.Step>, mode: StudyMode, speed: Double, jitterMs: Int, rnd: Random, holdFactor: Double = 0.95) {
        coroutineScope {
            if (mode == StudyMode.TEMPO) {
                for (step in steps) {
                    val target = step.startSec + rnd.nextInt(-jitterMs, jitterMs + 1) / 1000.0 * speed
                    while (isActive && (session?.songTimeAt(System.nanoTime()) ?: return@coroutineScope) < target) delay(1)
                    if (!isActive) return@coroutineScope
                    pressStep(step, speed, holdFactor = holdFactor)
                }
            } else {
                for ((i, step) in steps.withIndex()) {
                    pressStep(step, 1.0, cap = 900L)
                    val gap = ((steps.getOrNull(i + 1)?.startSec ?: step.startSec) - step.startSec) * 1000 / speed
                    delay(gap.toLong().coerceIn(80L, 1200L)) // ritmo vicino a quello del brano, ma sempre ascoltabile
                }
            }
        }
    }

    private fun CoroutineScope.pressStep(step: com.gianlucacelante.midipiano.song.Step, speed: Double, cap: Long = Long.MAX_VALUE, holdFactor: Double = 0.95) {
        for (note in step.notes) {
            pressKey(note.key, note.velocity)
            launch {
                delay(maxOf(60L, ((note.durationSec / speed * 1000 * holdFactor).toLong()).coerceAtMost(cap)))
                releaseKey(note.key)
            }
        }
    }

    /**
     * Suona il brano aperto. In modalità a tempo ogni nota parte all'istante previsto più un errore
     * casuale (±[jitterMs]). Scrive il risultato nel log (tag "Autoplay").
     */
    fun debugAutoplay(mode: StudyMode, speed: Double, jitterMs: Int, hand: Hand, countIn: Boolean) {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch(Dispatchers.Default) {
            val sg = song ?: return@launch
            synchronized(lock) {
                val s = session ?: return@launch
                s.setHand(hand)
                s.countInEnabled = countIn
                s.setMode(mode, System.nanoTime())
                s.setSpeed(speed, System.nanoTime())
                publishLocked()
            }
            val rnd = Random(42)
            val startReal = System.nanoTime()
            if (mode == StudyMode.TEMPO) withSession { it.play(System.nanoTime()) }
            playRun(sg.steps(hand), mode, speed, jitterMs, rnd)
            delay(600)
            val s = session
            if (mode == StudyMode.TEMPO) {
                Log.d("Autoplay", "TEMPO speed=$speed jitter=±${jitterMs}ms hits=${s?.hits} misses=${s?.misses} wrong=${s?.wrongNotes} " +
                    "accuracy=${"%.3f".format(s?.accuracy ?: 0.0)} meanDev=${"%.1f".format((s?.meanDeviationSec ?: 0.0) * 1000)}ms " +
                    "realSec=${"%.2f".format((System.nanoTime() - startReal) / 1e9)} songSec=${"%.2f".format(sg.durationSec)}")
                s?.missedNotes()?.take(10)?.forEach { (k, t) -> Log.d("Autoplay", "MISSED key=$k at=${"%.3f".format(t)}") }
            } else {
                Log.d("Autoplay", "WAIT steps=${sg.steps(hand).size} finished=${s?.isFinished} mistakes=${s?.mistakes}")
            }
        }
    }

    /** Completa da solo l'esercizio aperto, passo dopo passo, finché ottiene le stelle. */
    fun debugAutoplayExercise(jitterMs: Int, holdFactor: Double = 0.95) {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch(Dispatchers.Default) {
            val rnd = Random(7)
            var waited = 0
            val startReal = System.nanoTime()
            var runs = 0
            while (isActive && exerciseStars == null && waited < 600) {
                val s = session ?: break
                if (s.isFinished || (s.mode != StudyMode.WAIT && !s.isPlaying)) { // in attesa che l'esercizio riparta
                    delay(100)
                    waited++
                    continue
                }
                val stepIdx = runner?.stepIndex
                Log.d("Autoplay", "RUN ${++runs} step=$stepIdx mode=${s.mode} hand=${s.hand} speed=${s.speed}")
                playRun(s.song.steps(s.hand), s.mode, s.speed, jitterMs, rnd, holdFactor)
                // a tempo: il giro dura ancora fino alla fine del brano; si aspetta che finisca prima di ripartire
                // (altrimenti le note del giro dopo cadono in quello in corso e contano come sbagliate)
                var guard = 0
                while (isActive && s.mode != StudyMode.WAIT && s.isPlaying && !s.isFinished && guard++ < 400) delay(50)
                delay(300)
            }
            Log.d("Autoplay", "EXERCISE stars=$exerciseStars runs=$runs sec=${"%.1f".format((System.nanoTime() - startReal) / 1e9)} " +
                "saved=${progress.starsOf(currentExercise?.id ?: "")}")
        }
    }

    override fun onCleared() {
        scanner.stop()
        input.stop()
        audio.stop()
        mic.stop()
    }
}
