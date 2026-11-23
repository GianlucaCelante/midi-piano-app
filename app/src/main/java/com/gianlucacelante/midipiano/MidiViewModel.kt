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
)

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
        onNoteOff = { note -> _pressed.update { it - note }; _wrong.update { it - note } },
    )
    @Volatile private var settings = appSettings.load()
    private var lastBeatIdx = Int.MIN_VALUE
    private var lastBar = 0
    private var lastExpected: Set<Int> = emptySet()
    private var scheduler: NoteScheduler? = null
    private var lastListenTime = 0.0

    // Corsi, esercizi e progressi
    private val courseRepository = CourseRepository(app)
    private val progressStore = ProgressStore(app)
    private val progress: Progress = progressStore.load()
    private var runner: ExerciseRunner? = null
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
    private var runHandled = false
    private var practiceStartNanos = 0L

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
    fun startKeyboardCheck() { synchronized(kbCheck) { kbCheck.reset(_devices.value.isNotEmpty()); _kbState.value = kbCheck.state } }

    private val input = MidiInput(
        context = app,
        onEvent = { event ->
            when (event) {
                is MidiEvent.NoteOn -> { AppLog.i("MIDI", "on nota=${event.note} vel=${event.velocity}"); synchronized(kbCheck) { kbCheck.onNoteOn(event.note, event.velocity); _kbState.value = kbCheck.state }; noteOn(event.note, event.velocity, fromMidi = true) }
                is MidiEvent.NoteOff -> {
                    AppLog.i("MIDI", "off nota=${event.note}")
                    synchronized(kbCheck) { kbCheck.onNoteOff(event.note) }
                    take.noteOff(event.note, System.nanoTime())
                    _pressed.update { it - event.note }
                    _wrong.update { it - event.note }
                    if (settings.midiSounds) audio.noteOff(event.note)
                }
                is MidiEvent.Control -> if (event.controller == 64) { // pedale di risonanza
                    AppLog.i("MIDI", "pedale ${if (event.value >= 64) "giù" else "su"}")
                    synchronized(kbCheck) { kbCheck.onPedal(event.value); _kbState.value = kbCheck.state }
                    if (settings.midiSounds) audio.sustain(event.value >= 64)
                }
            }
        },
        onDevicesChanged = { names ->
            AppLog.i("MIDI", "dispositivi: ${if (names.isEmpty()) "nessuno" else names.joinToString()}")
            _devices.value = names
            synchronized(kbCheck) { kbCheck.onConnection(names.isNotEmpty()); _kbState.value = kbCheck.state }
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
    fun consumeImported() { _imported.value = null }

    init {
        input.start()
        com.gianlucacelante.midipiano.Reminder.apply(app, settings.reminderHour) // gli allarmi si perdono con gli aggiornamenti: si riprogramma
        refreshSongs()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _courses.value = courseRepository.load()
                publishProgress() // con i corsi caricati si può scegliere la prova del giorno
            } catch (e: Exception) {
                run { AppLog.w("ERRORE", "Impossibile leggere i corsi: ${e.message}"); _error.value = "Impossibile leggere i corsi: ${e.message}" }
            }
        }
        publishProgress()
        viewModelScope.launch(Dispatchers.IO) { refreshTakes() }
    }

    private fun refreshSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.entries()
                _songs.value = entries
                // difficoltà stimata di ogni brano incluso (si legge una volta sola, in secondo piano)
                val loadedAll = entries.mapNotNull { e -> runCatching { e to repository.load(e) }.getOrNull() }
                _songLevels.value = loadedAll.associate { (e, sg) -> e.id to Difficulty.of(sg) }
                _chordSongs.value = loadedAll.filter { (_, sg) -> sg.chordChart || sg.chordSymbols.isNotEmpty() }.associate { (e, sg) -> e.id to sg.chordChart }
                _songVersions.value = loadedAll.associate { (e, sg) -> e.id to com.gianlucacelante.midipiano.learn.Simplifier.availableVersions(sg) }
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

    private fun startSessionLocked(loaded: Song, exercise: ExerciseSlide?) {
        AppLog.i("PLAYER", "apre «${loaded.title}» note=${loaded.notes.size} durata=${"%.1f".format(loaded.durationSec)}s battute=${loaded.barCount} esercizio=${exercise?.id ?: "-"}")
        song = loaded
        songExpressive = com.gianlucacelante.midipiano.practice.Dynamics.isExpressive(loaded.notes.map { it.velocity })
        openedSongId = null // lo imposta openSong per i brani liberi
        openedEntry = null
        if (exercise != null) transposeSemis = 0
        songDoneRecorded = false
        take.clear()
        val first = exercise?.steps?.firstOrNull()
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
        runner = exercise?.let { ExerciseRunner(it.steps) }
        currentExercise = exercise
        exerciseMessage = null
        exerciseStars = null
        runHandled = false
        if (first != null) applyStep(sess, first)
        practiceStartNanos = System.nanoTime()
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
        s.restart(now) // ogni passo riparte dall'inizio (con il conto alla rovescia): senza, il giro nuovo eredita la fine del precedente
        _clockTime.value = s.currentTimeSec.toFloat()
        runHandled = false
    }

    /** Apre un esercizio di un corso: parte dal primo passo, con mano, modalità e velocità del passo. */
    fun openExercise(slide: ExerciseSlide) {
        stopPreview()
        if (!com.gianlucacelante.midipiano.learn.SightReading.isSight(slide.id)) progress.lastExerciseId = slide.id // anche lasciato a metà: da qui si riprende
        progressStore.save(progress)
        publishProgress()
        openJob?.cancel()
        synchronized(lock) { startSessionLocked(slide.song, slide) }
        audio.start()
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
        synchronized(lock) { startSessionLocked(ex.song, ex) }
    }

    private fun exerciseStatus(): ExerciseStatus? {
        val ex = currentExercise ?: return null
        val r = runner ?: return null
        val step = r.currentStep
        return ExerciseStatus(
            ex.id, ex.title, ex.text, r.stepIndex, r.totalSteps, step?.text ?: "",
            r.runsInStep, step?.loops ?: 1, exerciseMessage, exerciseStars,
        )
    }

    /** Un brano libero (non un esercizio) suonato fino in fondo sblocca la versione successiva. */
    private fun recordSongDoneLocked(s: PracticeSession) {
        val id = openedSongId ?: return
        if (currentExercise != null || songDoneRecorded || s.mode == StudyMode.LISTEN || !s.isFinished) return
        songDoneRecorded = true
        progress.recordSongDone(id, openedVersion)
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
        if (id == progress.dailyPick(LocalDate.now(), "song")) progress.markDailyDone(LocalDate.now(), "song")
        progressStore.save(progress)
        publishProgress()
        AppLog.i("PLAYER", "brano finito: $id versione=$openedVersion")
    }

    /** A fine giro dice se è riuscito, assegna le stelle e fa avanzare l'esercizio. */
    private fun checkRunFinishedLocked() {
        val s = session ?: return
        val r = runner ?: return
        val ex = currentExercise ?: return
        if (runHandled || !s.isFinished || r.isDone || s.mode == StudyMode.LISTEN) return
        runHandled = true
        val allowed = max(2, s.totalSteps / 8)
        val passed: Boolean
        val stars: Int
        if (s.mode == StudyMode.WAIT) {
            passed = s.mistakes <= allowed
            stars = Awards.stars(StudyMode.WAIT, 1.0, 1.0, s.mistakes)
        } else {
            passed = s.hits > 0 && s.accuracy >= 0.8 && s.wrongNotes <= allowed
            // la velocità la prescrive il passo (l'utente non può cambiarla): il tetto delle 2 stelle sotto il 99 % non vale negli esercizi
            stars = Awards.stars(StudyMode.TEMPO, 1.0, s.accuracy, s.mistakes)
        }
        val outcome = r.onRunFinished(passed, stars)
        when (outcome) {
            is RunOutcome.NextRun -> exerciseMessage = "Bene! Ancora ${outcome.runsLeft} ${if (outcome.runsLeft == 1) "volta" else "volte"}."
            is RunOutcome.Retry -> exerciseMessage = "Riprova: non è ancora un giro pulito."
            is RunOutcome.StepAdvanced -> exerciseMessage = "Passo superato! Ora il successivo."
            is RunOutcome.Done -> {
                exerciseStars = outcome.stars
                exerciseMessage = null
                if (com.gianlucacelante.midipiano.learn.SightReading.isSight(ex.id)) progress.recordSight(outcome.stars) // melodia generata: conta solo il livello
                else {
                    progress.recordExercise(ex.id, outcome.stars)
                    if (outcome.stars > 0 && ex.id == progress.dailyPick(LocalDate.now(), "trial")) progress.markDailyDone(LocalDate.now(), "trial")
                    progress.lastExerciseId = ex.id
                }
                progressStore.save(progress)
                publishProgress()
            }
        }
        if (outcome !is RunOutcome.Done) {
            viewModelScope.launch { // si ricomincia dopo un attimo, così si legge il messaggio
                delay(1600)
                synchronized(lock) {
                    val sess = session ?: return@launch
                    if (currentExercise !== ex || runner !== r) return@launch
                    exerciseMessage = null
                    val step = r.currentStep
                    if (outcome is RunOutcome.StepAdvanced && step != null) applyStep(sess, step) else {
                        sess.restart(System.nanoTime())
                        runHandled = false
                        _clockTime.value = sess.currentTimeSec.toFloat()
                    }
                    if (sess.mode == StudyMode.TEMPO) sess.play(System.nanoTime())
                    publishLocked()
                }
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
        val runs = progress.songsWithRuns().associateWith { progress.runsOf(it) }
        _progress.value = ProgressSnapshot(
            progress.allStars(), progress.streak(LocalDate.now()), progress.totalPracticeSeconds,
            progress.favoriteIds(), progress.lastExerciseId, progress.exercisesDone(), progress.songsDoneIds(),
            progress.secondsOn(today), progress.lastSongId, progress.lastSongVersion,
            progress.dailyPick(today, "song"), progress.dailyPick(today, "trial"), progress.isDailyDone(today, "song"), progress.isDailyDone(today, "trial"),
            com.gianlucacelante.midipiano.learn.Stats.minutesByDay(progress, today, 14), calendar, runs,
            runs.keys.associateWith { progress.weakBarsOf(it) }, achievements,
            progress.sightLevel, progress.sightDone, progress.chordLevel, progress.chordCorrectTotal,
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
            session?.pause(System.nanoTime())
            if (practiceStartNanos > 0L) {
                val secs = ((System.nanoTime() - practiceStartNanos) / 1_000_000_000L).coerceAtMost(1800L)
                if (secs >= 10) {
                    progress.recordPractice(secs, LocalDate.now())
                    progressStore.save(progress)
                    publishProgress()
                }
                practiceStartNanos = 0L
            }
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
        if (currentExercise != null) return@withSession // saltando alla fine si otterrebbero le stelle senza suonare
        val now = System.nanoTime()
        val t = fraction.coerceIn(0f, 1f) * s.song.durationSec
        s.seekTo(t, now) // per tempo: vale per tutte le modalità (in "aspetta" si ferma sul passo corrispondente)
        if (s.mode != StudyMode.WAIT) {
            audio.allNotesOff() // un salto in avanti non deve far suonare in raffica le note saltate
            lastListenTime = t - 1e-9
        }
        _clockTime.value = s.currentTimeSec.toFloat()
    }

    fun seekToBar(bar: Int) = withSession { s ->
        if (currentExercise != null) return@withSession
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
            startSessionLocked(ex.song, null)
            val s = session ?: return
            val now = System.nanoTime()
            s.setMode(StudyMode.WAIT, now)
            s.setSpeed(0.75, now)
            s.setLoopBars(first, last)
            s.restart(now)
            _clockTime.value = s.currentTimeSec.toFloat()
            publishLocked()
        }
        audio.start()
    }

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
        publishLocked() // senza sessione non fa nulla: le impostazioni sono comunque salvate
    }

    /** Da chiamare a ogni fotogramma mentre l'orologio corre. */
    fun onFrame(frameNanos: Long) {
        synchronized(lock) {
            val s = session ?: return
            if (!s.isPlaying) return
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
            if (t.finished && s.isPreviewing) endListenFirstLocked(s) // l'anteprima è finita: si torna pronti a suonare
            else if (t.finished) { checkRunFinishedLocked(); recordSongDoneLocked(s) }
            if (t.looped || t.finished || t.newMisses > 0 || beat != lastCountInBeat || bar != lastBar || expected != lastExpected || beatChanged) {
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

    /** Fa sentire un accordo (per l'allenamento «ascolta e riconosci»). */
    fun playChord(keys: List<Int>) {
        audio.start()
        viewModelScope.launch {
            for (k in keys) audio.noteOn(k, 80)
            delay(1400)
            for (k in keys) audio.noteOff(k)
        }
    }

    fun recordChordAnswer(correct: Boolean) = synchronized(lock) {
        progress.recordChordAnswer(correct)
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
    }

    fun onForeground() {
        if (session != null) audio.start()
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
            val expectedBefore = s.expectedKeys
            if (fromMidi) _press.value = PressInfo(velocity, if (songExpressive) s.expectedNotes().firstOrNull { it.key == note }?.velocity else null, ++pressCounter)
            val result = s.onNoteOn(note, now)
            AppLog.i("NOTA", "nota=$note ${if (fromMidi) "midi" else "schermo"} esito=$result modo=${s.mode} attese=$expectedBefore t=${"%.2f".format(s.currentTimeSec)} passo=${s.stepIndex}/${s.totalSteps} errori=${s.mistakes}")
            if (result == com.gianlucacelante.midipiano.practice.NoteResult.WRONG) _wrong.update { it + note }
            if (s.mode == StudyMode.WAIT) _clockTime.value = s.currentTimeSec.toFloat()
            checkRunFinishedLocked()
            publishLocked()
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
            exerciseReview = if (currentExercise != null && (exerciseStars ?: 3) < 3) com.gianlucacelante.midipiano.practice.BarReport.reviewRange(s.barReport()) else null,
            barStats = if (s.isFinished && currentExercise == null && s.mode != StudyMode.LISTEN) s.barReport() else emptyList(),
            listeningFirst = s.isPreviewing,
            transpose = transposeSemis, canTranspose = openedEntry != null && currentExercise == null && !sg.chordChart,
            notes = notes, sheet = sheet, keyRange = keyRange, exercise = exerciseStatus(),
            noteState = noteStateFn(s, s.mode, s.expectedKeys, s.currentTimeSec),
            upcomingHands = upcoming.associate { it.key to it.hand },
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
                HitState.HIT, HitState.SKIPPED -> NoteState.PLAYED
                HitState.MISSED -> NoteState.MISSED
                else -> if (abs(n.startSec - _clockTime.value) < 0.2) NoteState.CURRENT else NoteState.UPCOMING
            }
        } else {
            NotationLayout.stateOf(n, time, expected)
        }
    }

    // ---- simulatore di esecuzione (solo debug): suona come farebbe una persona ----

    /** Suona un giro dei passi dati: a tempo (all'istante previsto ± errore) o aspettando; le note restano premute per la loro durata. */
    private suspend fun playRun(steps: List<com.gianlucacelante.midipiano.song.Step>, mode: StudyMode, speed: Double, jitterMs: Int, rnd: Random) {
        coroutineScope {
            if (mode == StudyMode.TEMPO) {
                for (step in steps) {
                    val target = step.startSec + rnd.nextInt(-jitterMs, jitterMs + 1) / 1000.0 * speed
                    while (isActive && (session?.songTimeAt(System.nanoTime()) ?: return@coroutineScope) < target) delay(1)
                    if (!isActive) return@coroutineScope
                    pressStep(step, speed)
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

    private fun CoroutineScope.pressStep(step: com.gianlucacelante.midipiano.song.Step, speed: Double, cap: Long = Long.MAX_VALUE) {
        for (note in step.notes) {
            pressKey(note.key, note.velocity)
            launch {
                delay(maxOf(60L, ((note.durationSec / speed * 1000 * 0.95).toLong()).coerceAtMost(cap)))
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
    fun debugAutoplayExercise(jitterMs: Int) {
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
                playRun(s.song.steps(s.hand), s.mode, s.speed, jitterMs, rnd)
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
