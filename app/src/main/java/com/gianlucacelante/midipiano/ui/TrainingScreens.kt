package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gianlucacelante.midipiano.ProgressSnapshot
import com.gianlucacelante.midipiano.notation.NoteNames
import com.gianlucacelante.midipiano.practice.Chord
import com.gianlucacelante.midipiano.practice.EarQuiz
import com.gianlucacelante.midipiano.practice.ChordDetector
import com.gianlucacelante.midipiano.practice.ChordQuiz
import com.gianlucacelante.midipiano.practice.KeyboardCheck
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Accent = FlowColors.Accent

/** Le schede degli allenamenti: lettura a prima vista, accordi e orecchio. */
@Composable
fun TrainingCards(progress: ProgressSnapshot, onSight: () -> Unit, onChords: () -> Unit, onEar: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TrainingCard(
            "Lettura a prima vista", "Una melodia nuova ogni volta, senza nomi sulle note. Livello ${progress.sightLevel} di 5 · ${progress.sightDone} ${if (progress.sightDone == 1) "fatta" else "fatte"}.",
            "Leggi e suona", onSight, Modifier.weight(1f),
        )
        TrainingCard(
            "Allenamento accordi", "Suona l'accordo che ti viene chiesto, oppure ascoltalo e riconosci il nome. Livello ${progress.chordLevel} di ${ChordQuiz.MAX_LEVEL} · ${progress.chordCorrect} giusti.",
            "Allenati", onChords, Modifier.weight(1f),
        )
        TrainingCard(
            "Allenamento orecchio", "Riconosci o suona gli intervalli e ripeti piccole melodie. Intervalli: livello ${progress.intervalLevel} di ${com.gianlucacelante.midipiano.practice.EarQuiz.MAX_LEVEL} · melodie: livello ${progress.melodyLevel}.",
            "Ascolta", onEar, Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrainingCard(title: String, text: String, action: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).clickable { onClick() }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text, color = FlowColors.Muted, fontSize = 15.sp)
        Text(
            action, color = Color(0xFF0A0F1E), fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/** Allenamento sugli accordi: «Suona…» (tastiera) oppure «Ascolta» (riconosci il nome). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChordTrainerScreen(
    progress: ProgressSnapshot, pressed: Set<Int>,
    onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit, onPlayChord: (List<Int>) -> Unit, onAnswer: (Boolean) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = progress.chordLevel
    val rnd = remember { Random(System.nanoTime()) }
    var listen by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf(ChordQuiz.next(level, rnd, null)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf<Boolean?>(null) }
    var hint by remember { mutableStateOf(false) }
    var waitRelease by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(ChordQuiz.options(q, level, rnd)) }
    var turn by remember { mutableIntStateOf(0) }

    fun nextQuestion() {
        q = ChordQuiz.next(level, rnd, q)
        options = ChordQuiz.options(q, level, rnd)
        feedback = null; good = null; hint = false; turn++
    }
    val chordKeys = q.keys()

    // ascolto: a ogni domanda nuova si suona l'accordo
    LaunchedEffect(turn, listen) { if (listen) { delay(400); onPlayChord(chordKeys) } }

    // suona: appena ci sono abbastanza tasti premuti si valuta una volta sola, poi si aspetta che li lasci
    LaunchedEffect(pressed) {
        if (listen) return@LaunchedEffect
        if (pressed.isEmpty()) { waitRelease = false; return@LaunchedEffect }
        if (waitRelease || pressed.size < q.size) return@LaunchedEffect
        waitRelease = true
        val c = ChordDetector.identify(pressed)
        if (q.matches(c)) {
            feedback = "Giusto!"; good = true
            if (!hint) onAnswer(true)
            delay(1000)
            nextQuestion()
        } else {
            feedback = "Hai suonato ${c?.fullName ?: "note che non formano un accordo noto"}"; good = false
            if (!hint) onAnswer(false)
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("‹ Corsi", color = Accent, fontSize = 18.sp) }
        Text("Allenamento accordi", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Livello $level di ${ChordQuiz.MAX_LEVEL}: " + when (level) { 1 -> "accordi maggiori e minori sui tasti bianchi"; 2 -> "tutte le tonalità, con diminuiti e aumentati"; 3 -> "accordi di settima e sospesi"; else -> "i rivolti: stato fondamentale, primo e secondo rivolto" } + ". Con 8 risposte giuste su 10 si sale.", color = FlowColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((l, label) in listOf(false to "Suona l'accordo", true to "Ascolta e riconosci")) {
                Text(
                    label, color = if (listen == l) Color(0xFF0A0F1E) else Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (listen == l) Accent else Color.Transparent).border(2.dp, Accent, RoundedCornerShape(50))
                        .clickable { if (listen != l) { listen = l; waitRelease = false; nextQuestion() } }.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!listen) {
                Text("Suona", color = FlowColors.Muted, fontSize = 16.sp)
                Text(q.name, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(if (q.inversion >= 0) "Qualunque ottava va bene, ma il rivolto conta: la nota più bassa cambia." else "Qualunque ottava e qualunque rivolto vanno bene.", color = FlowColors.Muted, fontSize = 14.sp)
                if (hint) Text("Note: " + chordKeys.joinToString(" · ") { com.gianlucacelante.midipiano.notation.NoteNames.italian(it, flats = q.root in setOf(1, 3, 8, 10)) }, color = Accent, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Che accordo è?", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onPlayChord(chordKeys) }, modifier = Modifier.padding(top = 8.dp)) { Text("Riascolta") }
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (o in options) {
                        val answered = good != null
                        val isAnswer = o == q
                        val bg = when { answered && isAnswer -> FlowColors.Green.copy(alpha = 0.85f); else -> FlowColors.SurfaceHigh }
                        Text(
                            o.name, color = if (answered && isAnswer) Color(0xFF0A0F1E) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(2.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .clickable(enabled = !answered) {
                                    val ok = o == q
                                    good = ok; feedback = if (ok) "Giusto!" else "Era ${q.name}"
                                    onAnswer(ok)
                                }.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
                if (good != null) TextButton(onClick = { nextQuestion() }) { Text("Prossimo", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            feedback?.let { Text(it, color = if (good == true) FlowColors.Green else FlowColors.Red, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
        }

        if (!listen) {
            val c = ChordDetector.identify(pressed)
            Text(
                if (c != null) "Stai suonando: ${c.fullName}" else if (pressed.isEmpty()) "Tieni premuti i tasti dell'accordo, anche sulla tastiera a schermo." else "…",
                color = Color(0xFFC4CEDA), fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { hint = true }) { Text("Aiuto: mostra le note", color = Accent) }
                TextButton(onClick = { nextQuestion(); waitRelease = pressed.isNotEmpty() }) { Text("Salta", color = Accent) }
            }
        }
        Spacer(Modifier.height(8.dp))
        PianoKeyboard(
            pressed = pressed, range = 48..83, height = 170.dp, expected = if (hint && !listen) chordKeys.toSet() else emptySet(),
            onKeyDown = onKeyDown, onKeyUp = onKeyUp, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Allenamento dell'orecchio: intervalli (suona o riconosci) e dettato melodico (ascolta e ripeti). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EarTrainerScreen(
    progress: ProgressSnapshot, pressed: Set<Int>,
    onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit, onPlay: (List<Int>) -> Unit, onAnswer: (String, Boolean) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rnd = remember { Random(System.nanoTime()) }
    var mode by remember { mutableStateOf("interval-listen") } // interval-listen, interval-play, melody
    val kind = if (mode == "melody") "melody" else "interval"
    val level = if (kind == "melody") progress.melodyLevel else progress.intervalLevel
    var iq by remember { mutableStateOf(EarQuiz.nextInterval(level, rnd, null)) }
    var options by remember { mutableStateOf(EarQuiz.options(iq, level, rnd)) }
    var melody by remember { mutableStateOf(EarQuiz.nextMelody(level, rnd)) }
    val played = remember { mutableStateListOf<Int>() }
    var feedback by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf<Boolean?>(null) }
    var turn by remember { mutableIntStateOf(0) }
    var prevPressed by remember { mutableStateOf(emptySet<Int>()) }

    fun next() {
        iq = EarQuiz.nextInterval(level, rnd, iq)
        options = EarQuiz.options(iq, level, rnd)
        melody = EarQuiz.nextMelody(level, rnd, melody)
        played.clear(); feedback = null; good = null; turn++
    }

    // a ogni domanda nuova si ascolta: l'intervallo (due note) o la melodia
    LaunchedEffect(turn, mode) {
        if (mode == "interval-listen") { delay(400); onPlay(iq.keys()) }
        else if (mode == "melody") { delay(400); onPlay(melody) }
        else if (mode == "interval-play") { onPlay(listOf(iq.base)) }
    }

    // i tasti nuovi premuti, nell'ordine: servono a controllare cosa è stato suonato
    LaunchedEffect(pressed) {
        val added = (pressed - prevPressed).sorted()
        prevPressed = pressed
        if (good != null || added.isEmpty()) return@LaunchedEffect
        if (mode == "interval-play") {
            val k = added.last()
            if (k == iq.base) return@LaunchedEffect
            if (k == iq.top) { feedback = "Giusto!"; good = true; onAnswer(kind, true); delay(1000); next() }
            else { feedback = "Hai suonato ${NoteNames.italian(k)}: era ${NoteNames.italian(iq.top)}"; good = false; onAnswer(kind, false) }
        } else if (mode == "melody") {
            played.addAll(added)
            val ok = EarQuiz.correctPrefix(melody, played.toList())
            if (ok < played.size) { feedback = "Non è questa: la melodia era " + melody.joinToString(" · ") { NoteNames.italian(it) }; good = false; onAnswer(kind, false) }
            else if (played.size >= melody.size) { feedback = "Giusto!"; good = true; onAnswer(kind, true); delay(1000); next() }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("‹ Esercizi", color = Accent, fontSize = 18.sp) }
        Text("Allenamento orecchio", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Livello $level di ${EarQuiz.MAX_LEVEL}. Con 8 risposte giuste su 10 si sale; con troppi errori si scende.", color = FlowColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((m, label) in listOf("interval-listen" to "Riconosci l'intervallo", "interval-play" to "Suona l'intervallo", "melody" to "Ripeti la melodia")) {
                Text(
                    label, color = if (mode == m) Color(0xFF0A0F1E) else Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (mode == m) Accent else Color.Transparent).border(2.dp, Accent, RoundedCornerShape(50))
                        .clickable { if (mode != m) { mode = m; prevPressed = pressed; next() } }.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(20.dp)).background(FlowColors.Surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (mode) {
                "interval-listen" -> {
                    Text("Che intervallo è?", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { onPlay(iq.keys()) }, modifier = Modifier.padding(top = 8.dp)) { Text("Riascolta") }
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (o in options) {
                            val right = o == iq.name
                            Text(
                                o, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (good != null && right) FlowColors.Green.copy(alpha = 0.5f) else FlowColors.SurfaceHigh)
                                    .clickable(enabled = good == null) {
                                        if (right) { feedback = "Giusto!"; good = true; onAnswer(kind, true) }
                                        else { feedback = "Era: ${iq.name}"; good = false; onAnswer(kind, false) }
                                    }.padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                    if (good != null) TextButton(onClick = { next() }) { Text("Prossimo", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                "interval-play" -> {
                    Text("Parti da ${NoteNames.italian(iq.base)}", color = FlowColors.Muted, fontSize = 16.sp)
                    Text(iq.name + if (iq.up) " in su" else " in giù", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("Suona la nota di arrivo, in qualunque posizione: conta l'altezza esatta.", color = FlowColors.Muted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onPlay(listOf(iq.base)) }) { Text("Ascolta la nota") }
                        if (good == false) OutlinedButton(onClick = { onPlay(iq.keys()) }) { Text("Ascolta l'intervallo") }
                    }
                }
                else -> {
                    Text("Ascolta e ripeti", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("${melody.size} note: suonale nello stesso ordine.", color = FlowColors.Muted, fontSize = 15.sp)
                    OutlinedButton(onClick = { onPlay(melody) }, modifier = Modifier.padding(top = 8.dp)) { Text("Riascolta") }
                    Text("Suonate: ${played.size}/${melody.size}", color = Color(0xFFC4CEDA), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    if (good == false) OutlinedButton(onClick = { onPlay(melody) }) { Text("Ascolta quella giusta") }
                }
            }
            feedback?.let { Text(it, color = if (good == true) FlowColors.Green else FlowColors.Red, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            if (mode != "interval-listen") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (good == false) TextButton(onClick = { next() }) { Text("Prossimo", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                else TextButton(onClick = { next() }) { Text("Salta", color = Accent) }
            }
        }
        Spacer(Modifier.height(12.dp))
        PianoKeyboard(
            pressed = pressed, range = 48..83, height = 170.dp, expected = emptySet(),
            onKeyDown = onKeyDown, onKeyUp = onKeyUp, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Prova guidata della tastiera: ogni passo si spunta da solo. */
@Composable
fun KeyboardCheckScreen(
    state: KeyboardCheck.State, devices: List<String>, onStart: () -> Unit, onShareLog: () -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onStart() }
    Column(modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, Modifier.padding(top = 12.dp)) { Text("‹ Impostazioni", color = Accent, fontSize = 18.sp) }
        Text("Prova della tastiera", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            if (devices.isEmpty()) "Nessuna tastiera collegata. Collegala (USB/OTG o Bluetooth): si spunta da sola." else "Collegato: ${devices.joinToString()}",
            color = FlowColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        val next = state.next
        for (step in KeyboardCheck.Step.entries) {
            val done = step in state.done
            val current = step == next
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(if (current) FlowColors.SurfaceHigh else FlowColors.Surface)
                    .border(2.dp, if (current) Accent else Color.Transparent, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(if (done) FlowColors.Green else FlowColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else "", color = Color(0xFF0A0F1E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(step.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (done) "Fatto" else step.hint, color = if (current) Accent else FlowColors.Muted, fontSize = 14.sp)
                }
                if (step == KeyboardCheck.Step.NOTES) Text("${state.distinctNotes.coerceAtMost(5)}/5", color = FlowColors.Muted, fontSize = 15.sp)
                if ((step == KeyboardCheck.Step.SOFT || step == KeyboardCheck.Step.LOUD) && state.lastVelocity != null) Text("forza ${state.lastVelocity}", color = FlowColors.Muted, fontSize = 15.sp)
            }
        }
        state.rangeText?.let { Text("Note viste: $it", color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (state.allDone) {
            Text("Tutto funziona: tastiera, forza dei tasti e pedale.", color = FlowColors.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        } else {
            Text("Se un passo non si spunta, condividi il registro: dentro c'è tutto quello che l'app ha ricevuto.", color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
        }
        Row(Modifier.padding(top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onStart) { Text("Ricomincia") }
            OutlinedButton(onClick = onShareLog) { Text("Condividi il registro") }
        }
    }
}
