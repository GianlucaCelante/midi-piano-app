package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gianlucacelante.midipiano.Settings
import com.gianlucacelante.midipiano.practice.Sections

/** Scelta delle battute da ripetere: due contatori e "Attiva"/"Disattiva". */
@Composable
fun LoopDialog(
    barCount: Int,
    current: IntRange?,
    currentBar: Int,
    onApply: (start: Int, end: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableIntStateOf(current?.first ?: currentBar.coerceIn(1, barCount)) }
    var end by remember { mutableIntStateOf(current?.last ?: minOf(currentBar + 3, barCount).coerceAtLeast(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ripeti alcune battute") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("Da battuta", start, 1, end) { start = it }
                Stepper("A battuta", end, start, barCount) { end = it }
                Text("Il brano ripartirà da capo ogni volta che arriva alla fine del tratto.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onApply(start, end); onDismiss() }) { Text("Attiva") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onClear(); onDismiss() }) { Text("Disattiva") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text(value.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}

/** Menu delle parti del brano: sezioni di battute da cui ripartire. */
@Composable
fun PartsDialog(barCount: Int, currentBar: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val sections = remember(barCount) { Sections.of(barCount, 4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parti del brano") },
        text = {
            LazyColumn {
                items(sections) { range ->
                    val here = currentBar in range
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(range.first); onDismiss() }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(Sections.label(range))
                        if (here) Text("● qui", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/** Controlli del player raccolti nel dialogo delle impostazioni (modalità, velocità, parti, ricomincia). */
class PlayerControls(
    val mode: com.gianlucacelante.midipiano.practice.StudyMode,
    val speed: Double,
    val onMode: (com.gianlucacelante.midipiano.practice.StudyMode) -> Unit,
    val onSpeed: (Double) -> Unit,
    val onParts: () -> Unit,
    val onRestart: () -> Unit,
    val stats: String,
    /** Trasposizione: semitoni correnti, se si può cambiare, e il cambio (solo brani liberi con note). */
    val transpose: Int = 0,
    val canTranspose: Boolean = false,
    val onTranspose: (Int) -> Unit = {},
)

/** Impostazioni del player. */
@Composable
fun SettingsDialog(settings: Settings, onChange: (Settings) -> Unit, onDismiss: () -> Unit, player: PlayerControls? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (player != null) {
                    Text(player.stats, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(com.gianlucacelante.midipiano.practice.StudyMode.WAIT to "Aspetta", com.gianlucacelante.midipiano.practice.StudyMode.TEMPO to "A tempo", com.gianlucacelante.midipiano.practice.StudyMode.LISTEN to "Ascolta").forEach { (m, label) ->
                            androidx.compose.material3.FilterChip(selected = player.mode == m, onClick = { player.onMode(m) }, label = { Text(label) })
                        }
                    }
                    if (player.mode != com.gianlucacelante.midipiano.practice.StudyMode.WAIT) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.25 to "25%", 0.5 to "50%", 0.75 to "75%", 1.0 to "100%").forEach { (v, label) ->
                            androidx.compose.material3.FilterChip(selected = player.speed == v, onClick = { player.onSpeed(v) }, label = { Text(label) })
                        }
                    }
                    if (player.canTranspose) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Tonalità", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { player.onTranspose(player.transpose - 1) }, enabled = player.transpose > -6) { Text("−") }
                        Text(if (player.transpose == 0) "originale" else "${if (player.transpose > 0) "+" else ""}${player.transpose}", modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        OutlinedButton(onClick = { player.onTranspose(player.transpose + 1) }, enabled = player.transpose < 6) { Text("+") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = player.onParts) { Text("Parti del brano") }
                        OutlinedButton(onClick = player.onRestart) { Text("Ricomincia") }
                    }
                }
                SwitchRow("Suono del piano", settings.sound) { onChange(settings.copy(sound = it)) }
                SwitchRow("Suoni anche dalla tastiera MIDI", settings.midiSounds) { onChange(settings.copy(midiSounds = it)) }
                SwitchRow("Accompagnamento: l'app suona l'altra mano (A tempo)", settings.backing) { onChange(settings.copy(backing = it)) }
                SwitchRow("Puntini dei movimenti (battito visivo)", settings.visualBeat) { onChange(settings.copy(visualBeat = it)) }
                SwitchRow("Metronomo", settings.metronome) { onChange(settings.copy(metronome = it)) }
                SwitchRow("Conto alla rovescia", settings.countIn) { onChange(settings.copy(countIn = it)) }
                SwitchRow("Numeri delle dita sui tasti", settings.fingers) { onChange(settings.copy(fingers = it)) }
                SwitchRow("Nomi delle note sullo spartito", settings.noteNames) { onChange(settings.copy(noteNames = it)) }
                SwitchRow("Allenamento progressivo (sul loop: giro pulito = più veloce, giro sporco = più lento)", settings.speedTrainer) { onChange(settings.copy(speedTrainer = it)) }
                SwitchRow("Note in caduta", settings.fallingNotes) { onChange(settings.copy(fallingNotes = it)) }
                SwitchRow("Ascolta il pianoforte (microfono, una nota alla volta)", settings.mic) { onChange(settings.copy(mic = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Guida della app (prima volta, dalla Home). */
val APP_GUIDE: List<Pair<String, String>> = listOf(
    "Benvenuto!" to "Qui studi pianoforte con la tua tastiera: lo spartito scorre mentre suoni e l'app ti dice se le note sono giuste. Non serve saper leggere la musica per cominciare.",
    "Collega la tastiera" to "Con un cavo USB (adattatore OTG) o via Bluetooth. Il pulsante per cercare la tastiera Bluetooth e lo stato del collegamento sono nel Profilo. Se non hai una tastiera, puoi toccare i tasti sullo schermo.",
    "Tre modi di studiare" to "Aspetta: il brano si ferma finché non suoni la nota giusta. A tempo: scorre da solo e ti dice quanto sei preciso. Ascolta: suona lui, tu guardi e impari. Nei Corsi gli esercizi salgono di difficoltà e ti danno le stelle.",
)

/** Guida del player (la prima volta che si apre un brano, e da «Scopri come suonare»). */
val PLAYER_GUIDE: List<Pair<String, String>> = listOf(
    "Aspetta o a tempo" to "In Aspetta lo spartito si ferma finché non premi i tasti giusti: ideale per cominciare. Dal menu dell'ingranaggio passi ad A tempo, dove scorre da solo e conta la precisione.",
    "Una mano alla volta" to "I due cerchi ai lati del tasto play scelgono la mano: viola per la sinistra, arancio per la destra. Impara una mano per volta, poi suona con entrambe.",
    "Rallenta e ripeti" to "Il tasto con la percentuale (25-50-75-100%) rallenta il brano. Il tasto con le frecce ripete due battute: tocca di nuovo per toglierlo. Con «Allenamento progressivo» nelle impostazioni la velocità sale da sola quando il giro è pulito.",
    "Come è andata" to "A fine brano vedi una cella per battuta: verde pulita, arancio qualche errore, rossa da rifare. «Ripassa» mette in loop le due battute peggiori; toccando una cella ripeti quella sola.",
    "Tonalità e pedale" to "Dalle impostazioni puoi trasportare il brano di qualche semitono per portarlo in una tonalità comoda. Se la tua tastiera ha il pedale di risonanza, l'app lo segue.",
)

/** Guida iniziale in più pagine. */
@Composable
fun OnboardingDialog(onFinish: () -> Unit, pages: List<Pair<String, String>> = APP_GUIDE) {
    var page by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(pages[page].first) },
        text = { Text(pages[page].second) },
        confirmButton = {
            Button(onClick = { if (page == pages.lastIndex) onFinish() else page++ }) { Text(if (page == pages.lastIndex) "Inizia" else "Avanti") }
        },
        dismissButton = {
            Row {
                if (page > 0) TextButton(onClick = { page-- }) { Text("Indietro") }
                TextButton(onClick = onFinish) { Text("Salta") }
            }
        },
    )
}

/** Menu dell'ingranaggio del player: elenco di voci come in Flowkey, bianco con testo scuro. */
@Composable
fun PlayerMenuDialog(
    waitMode: Boolean,
    canChangeMode: Boolean,
    onMidi: () -> Unit,
    onToggleWait: () -> Unit,
    soundOn: Boolean = true,
    onToggleSound: () -> Unit = {},
    onHowTo: () -> Unit,
    onMore: () -> Unit,
    onCredits: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().androidx_clip(8).background(androidx.compose.ui.graphics.Color.White).padding(vertical = 20.dp),
        ) {
            Text("Impostazioni", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = androidx.compose.ui.graphics.Color.Black,
                fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            val items = buildList {
                add("Configurazione MIDI" to onMidi)
                if (canChangeMode) add((if (waitMode) "Disattiva modalità Aspetta" else "Attiva modalità Aspetta") to onToggleWait)
                add((if (soundOn) "Disattiva il suono" else "Attiva il suono (anche dalla tastiera)") to onToggleSound)
                add("Scopri come suonare" to onHowTo)
                add("Altre impostazioni" to onMore)
                add("Crediti" to onCredits)
                add("Annulla" to onDismiss)
            }
            items.forEach { (label, action) ->
                Text(label, Modifier.fillMaxWidth().clickable { action() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    color = androidx.compose.ui.graphics.Color(0xFF555B66), fontSize = 17.sp)
            }
        }
    }
}

@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } })
}

private fun Modifier.androidx_clip(radiusDp: Int): Modifier = this.clip(androidx.compose.foundation.shape.RoundedCornerShape(radiusDp.dp))
