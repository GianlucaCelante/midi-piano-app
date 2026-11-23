package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gianlucacelante.midipiano.practice.ModeLabels
import com.gianlucacelante.midipiano.practice.StudyMode
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gianlucacelante.midipiano.ExerciseStatus
import com.gianlucacelante.midipiano.PracticeSnapshot
import com.gianlucacelante.midipiano.Settings
import com.gianlucacelante.midipiano.midi.BleMidiDevice
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.SongEntry

@Composable
fun BluetoothSection(
    scanning: Boolean,
    found: List<BleMidiDevice>,
    onScan: () -> Unit,
    onConnect: (BleMidiDevice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScan, enabled = !scanning) {
                Text(if (scanning) "Ricerca in corso…" else "Cerca tastiera Bluetooth")
            }
            if (found.isEmpty() && !scanning) {
                Text("Accendi la tastiera e attiva il Bluetooth MIDI.", style = MaterialTheme.typography.labelSmall)
            }
        }
        found.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.displayName, Modifier.weight(1f))
                Button(onClick = { onConnect(d) }) { Text("Connetti") }
            }
        }
    }
}

private val StudioBackground = Color(0xFF111111)
private val OrangeBar = Color(0xFFF28C0F)

/**
 * Schermata di studio: tastiera in alto con i nomi delle note attese, barra di trasporto, spartito a
 * due righi (opzionalmente con le note in caduta) e barra arancione con la posizione.
 */
@Composable
fun PracticeScreen(
    snapshot: PracticeSnapshot,
    pressed: Set<Int>,
    clockTime: State<Float>,
    wrongKeys: Set<Int> = emptySet(),
    onBack: () -> Unit,
    onHand: (Hand) -> Unit,
    onMode: (StudyMode) -> Unit,
    onPlayPause: () -> Unit,
    onSpeed: (Double) -> Unit,
    onTranspose: (Int) -> Unit = {},
    onReview: (Int, Int) -> Unit = { _, _ -> },
    onReviewExercise: (Int, Int) -> Unit = { _, _ -> },
    onListenFirst: () -> Unit = {}, onSkipListen: () -> Unit = {},
    hasTake: Boolean = false, replaying: Boolean = false, onReplay: () -> Unit = {},
    press: com.gianlucacelante.midipiano.PressInfo? = null,
    onSettings: (Settings) -> Unit,
    onLoop: (Int?, Int?) -> Unit,
    onSeekBar: (Int) -> Unit,
    onRestart: () -> Unit,
    onExerciseNext: () -> Unit,
    onExerciseRetry: () -> Unit,
    onExit: () -> Unit,
    onSeek: (Float) -> Unit,
    onFrame: (Long) -> Unit,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
    midiStatus: String = "",
    nextExercise: com.gianlucacelante.midipiano.learn.ExerciseSlide? = null,
    nextLessonTitle: String? = null,
    nextIsNewLesson: Boolean = false,
    previewing: Boolean = false,
    onPreviewNext: () -> Unit = {},
    onStopPreview: () -> Unit = {},
) {
    // Anteprima saltabile prima di suonare: si offre finché non si è cominciato (o saltata)
    var listenOffered by remember(snapshot.title, snapshot.exercise?.slideId) { mutableStateOf(true) }
    val atStart = snapshot.currentTimeSec <= 0.05 && !snapshot.isPlaying && snapshot.hits == 0 && snapshot.mistakes == 0 && !snapshot.isFinished
    // fine del brano: mappa degli errori battuta per battuta
    var reportDismissed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(snapshot.isFinished) { if (!snapshot.isFinished) reportDismissed = false }
    if (snapshot.barStats.isNotEmpty() && !reportDismissed) {
        SongReportDialog(
            stats = snapshot.barStats, accuracy = if (snapshot.mode == StudyMode.TEMPO) snapshot.accuracy else null, mistakes = snapshot.mistakes,
            onReview = { a, b2 -> reportDismissed = true; onReview(a, b2) },
            onRetry = { reportDismissed = true; onRestart() }, onClose = { reportDismissed = true; onRestart() },
            hasTake = hasTake, replaying = replaying, onReplay = onReplay,
        )
    }
    // la prima volta che si apre il player parte la guida; poi solo da «Scopri come suonare»
    var dialog by remember { mutableStateOf<String?>(if (!snapshot.settings.playerGuideSeen && snapshot.exercise == null) "howto" else null) } // "loop", "parts", "settings"
    val showFalling = snapshot.settings.fallingNotes
    val tempo = snapshot.mode == StudyMode.TEMPO
    val clocked = snapshot.mode != StudyMode.WAIT || snapshot.isPlaying // in "aspetta" l'orologio corre solo dopo il play

    // Come in Flowkey: finito il brano il player torna all'inizio, in pausa (negli esercizi decide il percorso)
    // Con la mappa degli errori da mostrare, il ritorno all'inizio avviene quando la si chiude.
    LaunchedEffect(snapshot.isFinished, snapshot.exercise == null, snapshot.barStats.isEmpty()) {
        if (snapshot.isFinished && snapshot.exercise == null && snapshot.barStats.isEmpty()) {
            kotlinx.coroutines.delay(1500)
            onRestart()
        }
    }
    // Aiuto "Ascolta prima": dentro il Box dello spartito più sotto
    // Orologio: a ogni fotogramma mentre si suona (modalità a tempo)
    LaunchedEffect(snapshot.isPlaying) {
        while (snapshot.isPlaying && isActive) withFrameNanos { onFrame(it) }
    }
    // Modalità aspetta: lo spartito scorre con una breve animazione fra un passo e l'altro
    val waitAnim = remember { androidx.compose.animation.core.Animatable(snapshot.currentTimeSec.toFloat()) }
    LaunchedEffect(snapshot.currentTimeSec) { waitAnim.animateTo(snapshot.currentTimeSec.toFloat(), tween(250)) }
    val waitTime = waitAnim.value
    // Scorrimento a mano: durante il trascinamento lo spartito segue il dito (tempo continuo), al rilascio ci si aggancia
    val scrub = remember { ScrubTime() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val flingJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val sheetTime: () -> Float = { scrub.value ?: if (clocked) clockTime.value else waitAnim.value }
    // Da "in corsa" a fermo in modalità aspetta lo spartito resta dov'è: nessun salto
    LaunchedEffect(snapshot.isPlaying) { if (!snapshot.isPlaying) waitAnim.snapTo(snapshot.currentTimeSec.toFloat()) }

    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
    Column(Modifier.fillMaxSize()) {
        // Testata nera: indietro, autore (maiuscolo) + titolo al centro, ingranaggio
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                NavGlyph(NavIcon.BACK, Color(0xFFE8E8E8), 26.dp)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                snapshot.composer?.let { Text(it.uppercase(), color = Color(0xFF8A8A8A), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                Text(snapshot.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(44.dp).clickable { dialog = "settings" }, contentAlignment = Alignment.Center) {
                NavGlyph(NavIcon.GEAR, Color(0xFFE8E8E8), 26.dp)
            }
        }

        PianoKeyboard(
            pressed = pressed,
            wrong = wrongKeys,
            expected = snapshot.expectedKeys,
            expectedHands = snapshot.expectedHands,
            upcomingHands = snapshot.upcomingHands,
            upcomingFingers = if (snapshot.settings.fingers) snapshot.upcomingFingers else emptyMap(),
            fingers = if (snapshot.settings.fingers) snapshot.fingers else emptyMap(),
            range = snapshot.keyRange,
            modifier = Modifier,
            height = 176.dp, // senza il video delle mani non serve molto spazio: il resto va allo spartito
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp,
            preferFlats = snapshot.sheet.keyFifths < 0,
        )

        // Fascia chiara: mano sinistra, play, mano destra
        Box(Modifier.fillMaxWidth().background(Color(0xFFF8F9FB)).height(84.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                val canPickHand = snapshot.exercise == null
                HandCircle(Hand.LEFT, snapshot.hand == Hand.LEFT, canPickHand) { onHand(if (snapshot.hand == Hand.LEFT) Hand.BOTH else Hand.LEFT) }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF07F00)).clickable {
                        // negli esercizi in modalità aspetta si suona e basta; altrimenti play/pausa, anche in "aspetta" (scorre e aspetta le note)
                        if (!(snapshot.exercise != null && snapshot.mode == StudyMode.WAIT)) onPlayPause()
                    },
                    contentAlignment = Alignment.Center,
                ) { PlayGlyph(playing = snapshot.isPlaying) }
                HandCircle(Hand.RIGHT, snapshot.hand == Hand.RIGHT, canPickHand) { onHand(if (snapshot.hand == Hand.RIGHT) Hand.BOTH else Hand.RIGHT) }
            }
            // Forza dell'ultimo tasto premuto (pp…ff), con quella scritta nel brano se il brano ha una dinamica: verde = giusta, arancio = vicina, rosso = lontana
            var pressVisible by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(press?.id) { if (press != null) { pressVisible = true; kotlinx.coroutines.delay(2500); pressVisible = false } }
            if (press != null && pressVisible) {
                val exp = press.expected
                val match = exp?.let { com.gianlucacelante.midipiano.practice.Dynamics.compare(press.velocity, it) }
                val tone = when (match) { com.gianlucacelante.midipiano.practice.Dynamics.Match.OK -> Color(0xFF3FB950); com.gianlucacelante.midipiano.practice.Dynamics.Match.NEAR -> Color(0xFFF2A33A); com.gianlucacelante.midipiano.practice.Dynamics.Match.OFF -> Color(0xFFE5484D); null -> Color(0xFF6A6A6A) }
                Text(
                    com.gianlucacelante.midipiano.practice.Dynamics.label(press.velocity) + (exp?.let { "  →  " + com.gianlucacelante.midipiano.practice.Dynamics.label(it) } ?: ""),
                    color = tone, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 160.dp).clip(RoundedCornerShape(50)).background(Color.White).border(2.dp, tone, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            // Battito visivo: un puntino per movimento della battuta, quello corrente si accende (il primo più grande)
            val beat = snapshot.beatInBar
            if (beat != null && snapshot.settings.visualBeat) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 1..snapshot.beatsInBar.coerceIn(1, 12)) {
                        val on = i == beat
                        Box(Modifier.size(if (i == 1) 12.dp else 9.dp).clip(CircleShape).background(if (on) Color(0xFFF07F00) else Color(0xFFD0D0D0)))
                    }
                }
            }
            // A sinistra: pulsante modalità (Impara / A tempo / Ascolta · mano) e, dove conta, la velocità 25 → 50 → 75 → 100 %
            if (snapshot.exercise == null && !snapshot.listeningFirst) Row(Modifier.align(Alignment.CenterStart).padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    ModeLabels.pill(snapshot.mode, snapshot.hand) + "  ▾", color = Color(0xFF1B1B1B), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).border(2.dp, Color(0xFFF07F00), RoundedCornerShape(50))
                        .clickable { dialog = "mode" }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                if (snapshot.mode != com.gianlucacelante.midipiano.practice.StudyMode.WAIT) {
                    val pct = (snapshot.speed * 100).toInt()
                    Text(
                        "$pct%", color = if (snapshot.speed < 1.0) Color(0xFFF07F00) else Color(0xFF6A6A6A), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                            .border(2.dp, if (snapshot.speed < 1.0) Color(0xFFF07F00) else Color(0xFFD0D0D0), RoundedCornerShape(50))
                            .clickable { onSpeed(when { snapshot.speed < 0.3 -> 0.5; snapshot.speed < 0.6 -> 0.75; snapshot.speed < 0.9 -> 1.0; else -> 0.25 }) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            // Metronomo: tic a ogni movimento mentre l'orologio corre (in "Impara" quando il brano scorre)
            run {
                val on = snapshot.settings.metronome
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = if (snapshot.exercise == null) 90.dp else 28.dp).size(50.dp).clip(CircleShape)
                        .background(if (on) Color(0xFFF07F00) else Color.White).border(2.dp, if (on) Color(0xFFF07F00) else Color(0xFFD0D0D0), CircleShape)
                        .clickable { onSettings(snapshot.settings.copy(metronome = !on)) },
                    contentAlignment = Alignment.Center,
                ) { MetronomeGlyph(if (on) Color.White else Color(0xFFF07F00)) }
            }
            // Ripeti battute: nella fascia dei comandi, così non copre le note dello spartito (negli esercizi le ripetizioni le decide l'esercizio)
            if (snapshot.exercise == null) Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).size(50.dp).clip(CircleShape).background(Color.White)
                    .border(2.dp, if (snapshot.loopBars != null) Color(0xFFF07F00) else Color(0xFFD0D0D0), CircleShape).clickable {
                        // come in Flowkey: un tocco crea il loop di 2 battute dal punto attuale, un altro lo toglie
                        if (snapshot.loopBars != null) onLoop(null, null)
                        else onLoop(snapshot.currentBar, minOf(snapshot.currentBar + 1, snapshot.barCount))
                    },
                contentAlignment = Alignment.Center,
            ) { if (snapshot.loopBars != null) CloseGlyph(Color(0xFFF07F00)) else LoopGlyph(Color(0xFFF07F00)) }
        }

        snapshot.exercise?.let { ex -> ExerciseBanner(ex, snapshot.mode, snapshot.isPlaying, onPlayPause) }

        if (showFalling) {
            FallingNotesView(snapshot, Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 8.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF8F9FB))) {
            val sheetModifier = Modifier.fillMaxSize()
            SheetMusicView(
                sheet = snapshot.sheet, timeProvider = sheetTime, noteState = snapshot.noteState, hand = snapshot.hand,
                loopSec = snapshot.loopSec, showNoteNames = snapshot.settings.noteNames && snapshot.exercise?.slideId?.let { com.gianlucacelante.midipiano.learn.SightReading.isSight(it) } != true, modifier = sheetModifier,
                loopBars = snapshot.loopBars,
                onScrubStart = { flingJob[0]?.cancel(); if (snapshot.isPlaying) onPlayPause() }, // trascinando lo spartito il brano si ferma
                onScrub = { t -> scrub.value = t.toFloat() },
                glowByTime = clocked,
                smoothScroll = { snapshot.isPlaying && scrub.value == null },
                onScrubEnd = { vForward ->
                    val t0 = scrub.value
                    if (t0 != null) {
                        flingJob[0]?.cancel()
                        flingJob[0] = scope.launch {
                            val sp = snapshot.sheet.spacing
                            val dur = snapshot.durationSec
                            if (kotlin.math.abs(vForward) > 4.0) { // scorrimento con inerzia: prosegue e si ferma da solo
                                val anim = androidx.compose.animation.core.AnimationState(sp.scrollAt(t0.toDouble()).toFloat(), vForward.toFloat())
                                anim.animateDecay(androidx.compose.animation.core.exponentialDecay(frictionMultiplier = 1.1f)) {
                                    val t = sp.timeAt(value.toDouble()).coerceIn(0.0, dur)
                                    scrub.value = t.toFloat()
                                    if ((t <= 0.0 && velocity < 0f) || (t >= dur && velocity > 0f)) cancelAnimation()
                                }
                            }
                            val t = scrub.value ?: t0
                            waitAnim.snapTo(t) // dal punto raggiunto verso la nota più vicina, senza salti
                            onSeek((t / dur.coerceAtLeast(0.001)).toFloat())
                            scrub.value = null
                        }
                    }
                },
                onLoopEdit = { a, b -> onLoop(a, b) },
            )
            snapshot.countInBeat?.let { beat ->
                Text(beat.toString(), Modifier.align(Alignment.Center), style = MaterialTheme.typography.displayLarge, color = Color(0xFFE65100))
            }
            // anteprima di ascolto: offerta (saltabile) prima di cominciare, e controllo mentre si ascolta
            val showOffer = listenOffered && atStart && dialog == null && snapshot.mode != StudyMode.LISTEN && snapshot.exercise?.stars == null && snapshot.barStats.isEmpty()
            if (snapshot.listeningFirst || showOffer) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(RoundedCornerShape(50)).background(Color(0xF2101828)).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(if (snapshot.listeningFirst) "In ascolto, a tempo giusto…" else "Vuoi sentirlo prima?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (!snapshot.listeningFirst) {
                        Button(onClick = { listenOffered = false; onListenFirst() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF07F00))) {
                            Text("▶ Ascolta", color = Color(0xFF0A0F1E), fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { listenOffered = false; if (snapshot.listeningFirst) onSkipListen() }) { Text(if (snapshot.listeningFirst) "Salta" else "No, grazie", color = Color(0xFFC4CEDA)) }
                }
            }
            val ex = snapshot.exercise
            if (ex != null) {
                if (ex.stars == null && ex.message != null) {
                    Text(
                        ex.message, Modifier.align(Alignment.Center).background(Color(0xE6FFFFFF)).padding(20.dp),
                        style = MaterialTheme.typography.titleLarge, color = Color(0xFF1B1B1B),
                    )
                }
            }
        }

        // Barra arancione con il cursore di posizione: trascinandolo si salta nel brano
        Box(Modifier.fillMaxWidth().background(OrangeBar)) {
            PositionBar(
                progressProvider = { sheetTime() / snapshot.durationSec.toFloat().coerceAtLeast(0.001f) }, // stessa posizione dello spartito
                onScrubStart = { flingJob[0]?.cancel(); if (snapshot.isPlaying) onPlayPause() }, // toccando la barra il brano si ferma
                onScrub = { f -> scrub.value = f * snapshot.durationSec.toFloat() }, // lo spartito segue il dito subito
                onSeek = { f ->
                    scope.launch {
                        waitAnim.snapTo(f * snapshot.durationSec.toFloat())
                        onSeek(f)
                        scrub.value = null
                    }
                },
            )
        }
    }

    // Modale di fine esercizio: a tutto schermo (nell'area dello spartito non c'è abbastanza altezza)
    val doneEx = snapshot.exercise
    if (doneEx?.stars != null) {
        CompletionOverlay(
            title = doneEx.title, stars = doneEx.stars, accuracy = if (snapshot.mode == StudyMode.TEMPO) snapshot.accuracy else null,
            mistakes = snapshot.mistakes, next = nextExercise, nextLessonTitle = nextLessonTitle, nextIsNewLesson = nextIsNewLesson,
            previewing = previewing, onPreview = onPreviewNext, review = snapshot.exerciseReview, onReview = { a, b2 -> onStopPreview(); onReviewExercise(a, b2) },
            onRetry = { onStopPreview(); onExerciseRetry() }, onExit = { onStopPreview(); onExit() }, onNext = { onStopPreview(); onExerciseNext() },
        )
    }
    }

    when (dialog) {
        "loop" -> LoopDialog(
            barCount = snapshot.barCount, current = snapshot.loopBars, currentBar = snapshot.currentBar,
            onApply = { a, b -> onLoop(a, b) }, onClear = { onLoop(null, null) }, onDismiss = { dialog = null },
        )
        "parts" -> PartsDialog(snapshot.barCount, snapshot.currentBar, onPick = onSeekBar, onDismiss = { dialog = null })
        "settings" -> PlayerMenuDialog(
            waitMode = snapshot.mode == StudyMode.WAIT, canChangeMode = snapshot.exercise == null,
            onMidi = { dialog = "midi" },
            soundOn = snapshot.settings.sound && snapshot.settings.midiSounds,
            onToggleSound = { val on = !(snapshot.settings.sound && snapshot.settings.midiSounds); onSettings(snapshot.settings.copy(sound = on, midiSounds = on)); dialog = null },
            onToggleWait = { onMode(if (snapshot.mode == StudyMode.WAIT) StudyMode.TEMPO else StudyMode.WAIT); dialog = null },
            onHowTo = { dialog = "howto" }, onMore = { dialog = "more" }, onCredits = { dialog = "credits" },
            onDismiss = { dialog = null },
        )
        "midi" -> InfoDialog("Configurazione MIDI", midiStatus.ifBlank { "Collega la tastiera con un cavo USB (adattatore OTG) o via Bluetooth dalle Impostazioni dell'app." }, { dialog = null })
        "credits" -> InfoDialog("Crediti", listOfNotNull(snapshot.title, snapshot.composer, "Licenza: ${snapshot.license}").joinToString("\n"), { dialog = null })
        "mode" -> ModeDialog(
            mode = snapshot.mode, hand = snapshot.hand, backing = snapshot.settings.backing,
            onMode = onMode, onBacking = { onSettings(snapshot.settings.copy(backing = it)) }, onDismiss = { dialog = null },
        )
        "howto" -> OnboardingDialog(pages = PLAYER_GUIDE, onFinish = { dialog = null; if (!snapshot.settings.playerGuideSeen) onSettings(snapshot.settings.copy(playerGuideSeen = true)) })
        "more" -> SettingsDialog(
            snapshot.settings, onChange = onSettings, onDismiss = { dialog = null },
            player = if (snapshot.exercise == null) PlayerControls(
                snapshot.mode, snapshot.speed, onMode, onSpeed, onParts = { dialog = "parts" }, onRestart = { dialog = null; onRestart() },
                stats = "Battuta ${snapshot.currentBar}/${snapshot.barCount} · Colpite ${snapshot.hits} · Errori ${snapshot.mistakes}",
                transpose = snapshot.transpose, canTranspose = snapshot.canTranspose, onTranspose = onTranspose,
            ) else null,
        )
    }
}

/** Istruzioni del passo corrente di un esercizio, con i giri da fare. */
@Composable
private fun ExerciseBanner(ex: ExerciseStatus, mode: StudyMode, playing: Boolean, onPlayPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2A2218)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("${ex.title} · passo ${ex.stepIndex + 1}/${ex.totalSteps}", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFB74D))
            Text(ex.stepText, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            (0 until ex.runsRequired).joinToString(" ") { if (it < ex.runsDone) "●" else "○" },
            color = Color(0xFFF28C0F), style = MaterialTheme.typography.titleMedium,
        )
        if (mode == StudyMode.TEMPO) Button(onClick = onPlayPause) { Text(if (playing) "Pausa" else "Suona") }
    }
}

@Composable
private fun HandCircle(hand: Hand, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val handColor = HandColors.main(hand)
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFF8F9FB))
            .border(1.5.dp, if (active) handColor else Color(0xFFB8BCC4), CircleShape).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { HandIconFor(hand, if (active) handColor else handColor.copy(alpha = 0.55f)) }
}

@Composable
private fun PlayGlyph(playing: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        if (playing) {
            drawRect(Color.White, Offset(w * 0.2f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.76f))
            drawRect(Color.White, Offset(w * 0.6f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.76f))
        } else {
            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.28f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.28f, h * 0.9f); close() }, Color.White)
        }
    }
}

@Composable
private fun LoopGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(color, 200f, 150f, false, Offset(w * 0.12f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.76f), style = st)
        drawArc(color, 20f, 150f, false, Offset(w * 0.12f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.76f), style = st)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.9f, w * 0.22f); lineTo(w * 0.9f, w * 0.48f); lineTo(w * 0.64f, w * 0.38f); close() }, color)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.1f, w * 0.78f); lineTo(w * 0.1f, w * 0.52f); lineTo(w * 0.36f, w * 0.62f); close() }, color)
    }
}

@Composable
private fun MetronomeGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
        val w = size.width
        // corpo a trapezio, asta inclinata e peso
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.36f, w * 0.1f); lineTo(w * 0.64f, w * 0.1f); lineTo(w * 0.86f, w * 0.9f); lineTo(w * 0.14f, w * 0.9f); close() }, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color, Offset(w * 0.5f, w * 0.72f), Offset(w * 0.68f, w * 0.26f), strokeWidth = w * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(color, w * 0.08f, Offset(w * 0.62f, w * 0.4f))
    }
}

@Composable
private fun CloseGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, w * 0.15f), Offset(w * 0.85f, w * 0.85f), strokeWidth = st.width, cap = st.cap)
        drawLine(color, Offset(w * 0.85f, w * 0.15f), Offset(w * 0.15f, w * 0.85f), strokeWidth = st.width, cap = st.cap)
    }
}

/** Posizione scelta trascinando: float primitivo osservabile (NaN = nessun trascinamento), così ogni movimento ridisegna lo spartito. */
class ScrubTime {
    private val state = androidx.compose.runtime.mutableFloatStateOf(Float.NaN)
    var value: Float?
        get() = state.floatValue.takeUnless { it.isNaN() }
        set(v) { state.floatValue = v ?: Float.NaN }
}

/** Modale di fine esercizio: stelle animate, risultati, e il prossimo passo con anteprima. */
@Composable
private fun BoxScope.CompletionOverlay(
    title: String, stars: Int, accuracy: Double?, mistakes: Int,
    next: com.gianlucacelante.midipiano.learn.ExerciseSlide?, nextLessonTitle: String?, nextIsNewLesson: Boolean,
    previewing: Boolean, onPreview: () -> Unit, onRetry: () -> Unit, onExit: () -> Unit, onNext: () -> Unit,
    review: IntRange? = null, onReview: (Int, Int) -> Unit = { _, _ -> },
) {
    val headline = when (stars) { 3 -> "Perfetto!"; 2 -> "Bravo!"; 1 -> "Ce l'hai fatta!"; else -> "Completato" }
    Box(Modifier.matchParentSize().background(Color(0xCC0A0F1E)).clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {})
    Column(
        Modifier.align(Alignment.Center).width(640.dp).clip(RoundedCornerShape(28.dp)).background(FlowColors.Surface).padding(horizontal = 32.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..3) {
                val on = i <= stars
                val scale = remember { androidx.compose.animation.core.Animatable(if (on) 0f else 1f) }
                LaunchedEffect(on) { if (on) { kotlinx.coroutines.delay(180L * i); scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f)) } }
                Text("★", fontSize = if (i == 2) 76.sp else 60.sp, color = if (on) Color(0xFFFFC107) else Color(0xFF3A4458),
                    modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationY = if (i == 2) -6f else 0f })
            }
        }
        Text(headline, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(title, color = FlowColors.Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (accuracy != null) StatChip("Precisione", "${(accuracy * 100).toInt()}%")
            StatChip("Errori", mistakes.toString())
        }
        // con 1 o 2 stelle: come arrivare alle 3 (si può comunque proseguire)
        if (stars in 1..2) {
            val tip = when {
                mistakes > 3 -> "Rallenta e ripeti: con meno di 4 errori le stelle salgono."
                mistakes > 0 -> "Quasi: un giro senza errori vale 3 stelle."
                else -> "Il ritmo non era regolare: riprova ascoltando il tic."
            }
            Text(tip, color = Color(0xFFF07F00), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            if (review != null) OutlinedButton(onClick = { onReview(review.first, review.last) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ripassa le battute ${review.first}–${review.last}", color = Color(0xFFF07F00), fontWeight = FontWeight.SemiBold)
            }
        }
        if (next != null) {
            Row(
                Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FlowColors.SurfaceHigh).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CoverBox(next.id, next.title, 64.dp, radius = 12.dp, hint = nextLessonTitle ?: "")
                Column(Modifier.weight(1f)) {
                    Text(if (nextIsNewLesson) "PROSSIMA LEZIONE" else "PROSSIMO ESERCIZIO", color = Color(0xFFF07F00), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (nextIsNewLesson && nextLessonTitle != null) nextLessonTitle else next.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (nextIsNewLesson && nextLessonTitle != null) next.title else next.text, color = FlowColors.Muted, fontSize = 14.sp, maxLines = 2)
                }
                OutlinedButton(onClick = onPreview, border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF07F00))) {
                    Text(if (previewing) "■ Ferma" else "▶ Anteprima", color = Color(0xFFF07F00), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Riprova", color = if (stars in 1..2) Color(0xFFF07F00) else Color(0xFFC4CEDA), fontSize = 16.sp, fontWeight = if (stars in 1..2) FontWeight.Bold else FontWeight.Normal) }
            TextButton(onClick = onExit) { Text("Esci", color = Color(0xFFC4CEDA), fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF07F00)),
                modifier = Modifier.height(52.dp),
            ) {
                Text(if (next == null) "Fine" else if (nextIsNewLesson) "Prossima lezione  ›" else "Continua  ›", color = Color(0xFF0A0F1E), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(FlowColors.SurfaceHigh).padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FlowColors.Muted, fontSize = 12.sp)
    }
}

/** Fine del brano: una cella per battuta (verde = pulita, arancio = qualche errore, rosso = da rifare) e il ripasso delle peggiori. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SongReportDialog(
    stats: List<com.gianlucacelante.midipiano.practice.BarStat>, accuracy: Double?, mistakes: Int,
    onReview: (Int, Int) -> Unit, onRetry: () -> Unit, onClose: () -> Unit,
    hasTake: Boolean = false, replaying: Boolean = false, onReplay: () -> Unit = {},
) {
    val review = com.gianlucacelante.midipiano.practice.BarReport.reviewRange(stats)
    val clean = stats.count { it.notes > 0 && it.errors == 0 }
    val withNotes = stats.count { it.notes > 0 }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(620.dp).clip(RoundedCornerShape(24.dp)).background(FlowColors.Surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (review == null) "Brano pulito!" else "Fine del brano", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (accuracy != null) StatChip("Precisione", "${(accuracy * 100).toInt()}%")
                StatChip("Errori", mistakes.toString())
                StatChip("Battute pulite", "$clean/$withNotes")
            }
            Text("Come è andata, battuta per battuta", color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (st in stats) {
                    val color = when {
                        st.notes == 0 -> Color(0xFF3A4458)
                        st.errors == 0 -> Color(0xFF3FB950)
                        st.errors <= 2 -> Color(0xFFF2A33A)
                        else -> Color(0xFFE5484D)
                    }
                    val inReview = review != null && st.bar in review
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(color)
                            .then(if (inReview) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { onReview(st.bar, st.bar) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${st.bar}", color = Color(0xFF0A0F1E), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Tocca una battuta per ripeterla da sola", color = FlowColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (hasTake) OutlinedButton(onClick = onReplay, modifier = Modifier.padding(top = 12.dp)) { Text(if (replaying) "■ Ferma" else "▶ Riascolta la tua esecuzione", color = Color(0xFFF07F00), fontWeight = FontWeight.SemiBold) }
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Chiudi", color = Color(0xFFC4CEDA), fontSize = 16.sp) }
                TextButton(onClick = onRetry) { Text("Da capo", color = Color(0xFFC4CEDA), fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                if (review != null) Button(
                    onClick = { onReview(review.first, review.last) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF07F00)), modifier = Modifier.height(50.dp),
                ) { Text("Ripassa le battute ${review.first}–${review.last}", color = Color(0xFF0A0F1E), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
