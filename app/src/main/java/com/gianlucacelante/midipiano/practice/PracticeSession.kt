package com.gianlucacelante.midipiano.practice

import kotlin.math.abs
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song

/** Come si studia: aspettando le note, a tempo con un orologio che scorre, oppure ascoltando il brano. */
enum class StudyMode { WAIT, TEMPO, LISTEN }

/** Esito di un aggiornamento a tempo: istante del brano, giro di loop, fine, nuove note mancate. */
data class SessionTick(val songTimeSec: Double, val looped: Boolean, val finished: Boolean, val newMisses: Int)

/**
 * Una sessione di studio di un brano, per una mano o per entrambe, in modalità "aspetta" o "a
 * tempo" (con velocità, conto alla rovescia e loop per battute). Gli istanti reali sono passati da
 * fuori (nanosecondi monotoni) così la logica resta testabile.
 */
class PracticeSession(val song: Song, hand: Hand = Hand.BOTH) {
    var hand: Hand = hand
        private set
    var mode: StudyMode = StudyMode.WAIT
        private set
    var countInEnabled: Boolean = true
    /** Modalità aspetta: dopo un passo giusto lo spartito scorre a tempo verso il successivo (invece di saltare). */
    var autoFlow: Boolean = false
    var speed: Double = 1.0
        private set
    var loopsCompleted: Int = 0
        private set
    var loopBars: IntRange? = null
        private set

    /** Da dove si giudica: salti e ripartenze lasciano fuori ciò che precede. */
    private var judgedFromSec = 0.0

    /** Tasti sbagliati per battuta in questa esecuzione (per la mappa degli errori). */
    private val wrongByBar = HashMap<Int, Int>()

    /** Mappa degli errori battuta per battuta: note mancate (a tempo) e tasti sbagliati. */
    fun barReport(): List<BarStat> {
        val notes = HashMap<Int, Int>()
        for (st in steps) if (st.startSec >= judgedFromSec - 1e-6) notes.merge(song.barAt(st.startSec), 1, Int::plus) // un passo (accordo compreso) conta uno; le battute saltate non si giudicano
        val errors = HashMap(wrongByBar)
        if (mode == StudyMode.TEMPO) for (t in tempo.missedStepTimes()) if (t >= judgedFromSec - 1e-6) errors.merge(song.barAt(t), 1, Int::plus)
        return BarReport.fromCounts(song.barCount, notes, errors)
    }

    /** Come hai tenuto il tempo in questo giro (solo "A tempo"). */
    fun timingSummary(): TimingSummary? = if (mode == StudyMode.TEMPO) Timing.summarize(tempo.meanDeviationSec, tempo.noteHits, tempo.chordSpreadSec()) else null

    private var savedForPreview: Triple<StudyMode, Double, Hand>? = null
    val isPreviewing: Boolean get() = savedForPreview != null

    /**
     * Anteprima prima di suonare: si ascolta tutto il brano (entrambe le mani) a tempo giusto, con spartito e tastiera che si muovono.
     * Si ricorda modalità, velocità e mano per restituirle a fine anteprima ([endPreview]).
     */
    fun beginPreview(nowNanos: Long) {
        if (savedForPreview != null) return
        savedForPreview = Triple(mode, speed, hand)
        setHand(Hand.BOTH)
        setMode(StudyMode.LISTEN, nowNanos)
        setSpeed(1.0, nowNanos)
        restart(nowNanos)
        play(nowNanos)
    }

    /** Chiude l'anteprima (finita o saltata): impostazioni di studio di prima, dall'inizio, in pausa. */
    fun endPreview(nowNanos: Long) {
        val saved = savedForPreview ?: return
        savedForPreview = null
        pause(nowNanos)
        setHand(saved.third)
        setMode(saved.first, nowNanos)
        setSpeed(saved.second, nowNanos)
        restart(nowNanos)
    }

    /** Allenamento progressivo: sul loop in "A tempo", un giro pulito sale di un gradino di velocità, uno sbagliato scende. */
    var speedTrainer: Boolean = false

    /** Risultato dell'ultimo giro di loop concluso (note colpite / mancate), per mostrare i progressi. */
    var lastLapHits: Int = 0
        private set
    var lastLapMisses: Int = 0
        private set

    private var steps = song.steps(hand)
    private var wait = WaitModeEngine(steps)
    // battito medio del brano: da lui dipende la finestra di tolleranza (nei brani lenti è più larga)
    private val avgSecPerBeat: Double = run {
        val beats = song.beatsBetween(0.0, song.durationSec)
        if (beats > 0.0 && song.durationSec > 0.0) song.durationSec / beats else 0.5
    }
    private fun toleranceFor(sp: Double) = TimingWindow.songToleranceSec(avgSecPerBeat, sp)
    private var tempo = TempoModeEngine(steps, toleranceFor(1.0))
    private val clock = PlaybackClock(song.durationSec + END_PADDING_SEC)
    private var lastTime = 0.0
    private var lastNow = 0L
    /** Modalità aspetta: il cursore sta in un punto scelto (anche una pausa), non sul passo atteso. */
    private var waitFree = false
    private var finished = false

    /** Inizio del tratto che si sta studiando: la prima battuta del loop, o l'inizio del brano. */
    val regionStartSec: Double get() = loopBars?.let { song.barStartSec(it.first) } ?: 0.0

    val totalSteps: Int get() = steps.size
    val isPlaying: Boolean get() = clock.running
    /** Durata del conto alla rovescia: battute intere, per almeno [LEAD_IN_SEC] secondi. */
    val countInSec: Double get() {
        val bar = song.barDurationAt(regionStartSec)
        return kotlin.math.ceil(LEAD_IN_SEC / bar).coerceAtLeast(1.0) * bar
    }
    val isCountingIn: Boolean get() = mode != StudyMode.WAIT && clock.running && lastTime < regionStartSec
    val isFinished: Boolean get() = if (mode == StudyMode.WAIT) wait.isFinished else finished

    val stepIndex: Int
        get() = if (mode == StudyMode.WAIT) wait.index else steps.count { it.startSec <= lastTime + 1e-9 }

    // passo da cui è cominciato il giro in corso (dopo un salto non è lo zero): serve a contare quanti passi si sono suonati davvero
    private var waitRunStart = 0
    val stepsPlayedThisRun: Int get() = if (mode == StudyMode.WAIT) (wait.index - waitRunStart).coerceAtLeast(0) else 0

    val mistakes: Int
        get() = if (mode == StudyMode.WAIT) wait.mistakes else tempo.wrong + tempo.misses

    val expectedKeys: Set<Int>
        get() = if (mode == StudyMode.WAIT) {
            val step = wait.currentStep
            // con l'orologio in corsa si accendono quando il cursore sta per arrivarci, da fermi subito
            if (clock.running && step != null && lastTime < step.startSec - WAIT_LOOKAHEAD_SEC) emptySet() else wait.expectedKeys
        } else if (mode == StudyMode.LISTEN) { // i tasti che il brano sta suonando adesso
            song.notes
                .filter { (hand == Hand.BOTH || it.hand == hand) && it.startSec <= lastTime && lastTime < it.startSec + it.durationSec }
                .map { it.key }.toSet()
        } else {
            val tol = tempo.toleranceSec
            val pending = tempo.pendingBetween(lastTime - tol, lastTime + LOOKAHEAD_SEC)
            if (pending.isEmpty()) emptySet() else pending.mapTo(HashSet(pending.size * 2)) { it.key }
        }

    /** Le note che si aspettano adesso (con mano e tempo), per colorare i tasti per mano e mostrare le dita. */
    fun expectedNotes(): List<com.gianlucacelante.midipiano.song.SongNote> {
        val keys = expectedKeys
        if (keys.isEmpty()) return emptyList()
        val ref = if (mode == StudyMode.WAIT) wait.currentStep?.startSec ?: lastTime else lastTime
        return keys.mapNotNull { k ->
            notesByKey[k]?.filter { hand == Hand.BOTH || it.hand == hand }?.minByOrNull { kotlin.math.abs(it.startSec - ref) }
        }
    }

    /** Le note del brano raggruppate per tasto (indice costruito una volta): evita di scorrere tutto il brano a ogni richiesta. */
    private val notesByKey: Map<Int, List<com.gianlucacelante.midipiano.song.SongNote>> by lazy(LazyThreadSafetyMode.NONE) { song.notes.groupBy { it.key } }

    /** Le note subito dopo quelle attese (i due passi successivi): sulla tastiera si vedono in chiaro con il loro dito. */
    fun upcomingNotes(): List<com.gianlucacelante.midipiano.song.SongNote> {
        if (mode == StudyMode.LISTEN) return emptyList()
        val now = expectedKeys
        val out: List<com.gianlucacelante.midipiano.song.SongNote> = if (mode == StudyMode.WAIT) {
            val from = wait.index + (if (wait.currentStep != null) 1 else 0)
            steps.drop(from).take(2).flatMap { it.notes }
        } else {
            val tol = tempo.toleranceSec
            tempo.pendingBetween(lastTime + LOOKAHEAD_SEC, lastTime + 2.0 + tol).filter { it.timeSec > lastTime + LOOKAHEAD_SEC }
                .map { e -> notesByKey[e.key]?.firstOrNull { kotlin.math.abs(it.startSec - e.noteStartSec) < 0.002 } }.filterNotNull()
                .let { list -> val times = list.map { it.startSec }.distinct().sorted().take(2); list.filter { it.startSec in times } }
        }
        return out.filter { it.key !in now }.distinctBy { it.key }
    }

    /**
     * Quanto mostrare ogni nota in arrivo (tasto -> 0..1): più è vicina, più si vede; lontana quasi sparisce, così l'occhio
     * non si confonde. A tempo conta il tempo che manca; in «Impara» il passo subito dopo è più chiaro di quello ancora dopo.
     */
    fun upcomingWeights(upcoming: List<com.gianlucacelante.midipiano.song.SongNote> = upcomingNotes()): Map<Int, Float> {
        if (mode == StudyMode.LISTEN) return emptyMap()
        val now = expectedKeys
        val out = HashMap<Int, Float>()
        if (mode == StudyMode.WAIT) {
            val from = wait.index + (if (wait.currentStep != null) 1 else 0)
            steps.drop(from).take(2).forEachIndexed { i, st ->
                val w = if (i == 0) UPCOMING_NEAR else UPCOMING_FAR
                for (n in st.notes) if (n.key !in now) out.merge(n.key, w, ::maxOf)
            }
        } else {
            for (n in upcoming) {
                val dt = (n.startSec - lastTime).coerceAtLeast(0.0)
                out[n.key] = upcomingWeightAt(dt)
            }
        }
        return out
    }

    val progress: Float
        get() = when {
            song.durationSec <= 0.0 -> 1f
            mode == StudyMode.WAIT -> (currentTimeSec / song.durationSec).coerceIn(0.0, 1.0).toFloat()
            else -> (lastTime / song.durationSec).coerceIn(0.0, 1.0).toFloat()
        }

    /** Istante (secondi, tempo originale del brano) a cui ci si trova ora. */
    val currentTimeSec: Double
        get() = if (mode == StudyMode.WAIT) { if (clock.running || waitFree) lastTime else wait.currentStep?.startSec ?: song.durationSec } else lastTime

    /** Stato (colpita/mancata/in attesa) di una nota in modalità a tempo; null in modalità aspetta. */
    fun hitStateOf(key: Int, startSec: Double): HitState? =
        if (mode != StudyMode.TEMPO) null else tempo.stateAt(key, startSec)

    // Statistiche della modalità a tempo (in modalità aspetta valgono 0 / 1)
    val hits: Int get() = if (mode == StudyMode.TEMPO) tempo.hits else 0
    val misses: Int get() = if (mode == StudyMode.TEMPO) tempo.misses else 0
    val wrongNotes: Int get() = if (mode == StudyMode.TEMPO) tempo.wrong else wait.mistakes
    val accuracy: Double get() = if (mode == StudyMode.TEMPO) tempo.accuracy else 1.0
    val meanDeviationSec: Double get() = tempo.meanDeviationSec

    /** (nota, istante) delle note mancate: serve a capire gli errori nelle prove di simulazione. */
    fun missedNotes(): List<Pair<Int, Double>> =
        tempo.expected.filter { it.state == HitState.MISSED }.map { it.key to it.timeSec }

    /** Battito del conto alla rovescia (1, 2, 3...) o null se non si sta contando. */
    val countInBeat: Int?
        // il numero sparisce mezzo movimento prima della prima nota: così non la copre mentre arriva (il "tic" dell'ultimo movimento resta)
        get() = if (!isCountingIn || lastTime >= regionStartSec - 0.5 * song.beatSecAt(regionStartSec)) null else {
            val sinceStart = lastTime - (regionStartSec - countInSec) // tempo dall'inizio del conto alla rovescia
            ((sinceStart / song.beatSecAt(regionStartSec)).toInt().coerceAtLeast(0) % song.beatsPerBarAt(regionStartSec)) + 1
        }

    // ---- modalità e trasporto ----

    fun setMode(newMode: StudyMode, nowNanos: Long) {
        resetWaitTiming()
        val keep = currentTimeSec.takeIf { it > regionStartSec + 1e-6 } // cambiando modo non si torna all'inizio
        clock.pause(nowNanos)
        mode = newMode
        resetProgress(nowNanos)
        if (keep != null) seekTo(keep, nowNanos)
    }

    fun play(nowNanos: Long) {
        if (clock.running) return
        if (mode == StudyMode.WAIT) { // lo spartito scorre e si ferma ad aspettare le note
            if (wait.isFinished) resetProgress(nowNanos)
            val firstStep = steps.firstOrNull()?.startSec ?: 0.0
            val at = if (waitFree) lastTime else wait.currentStep?.startSec ?: 0.0
            val atStart = wait.index == 0 && at <= firstStep + 1e-6
            val from = if (atStart && countInEnabled) at - countInSec else at // dall'inizio: qualche secondo per leggere le note
            waitFree = false
            clock.seek(from, nowNanos)
            lastTime = from
            clock.start(nowNanos)
            return
        }
        if (finished) resetProgress(nowNanos)
        val regionStart = loopBars?.let { song.barStartSec(it.first) } ?: 0.0
        if (lastTime <= regionStart + 1e-6) { // si parte dall'inizio del tratto: col conto alla rovescia se è attivo, altrimenti subito lì
            val lead = if (countInEnabled) countInSec else 0.0
            clock.seek(regionStart - lead, nowNanos) // conto alla rovescia: qualche secondo per prepararsi
            lastTime = regionStart - lead
            tempo.skipBefore(regionStart) // le note di prima del tratto scelto non si giudicano
        }
        clock.start(nowNanos)
    }

    fun pause(nowNanos: Long) {
        if (mode == StudyMode.WAIT && clock.running) {
            lastTime = waitTick(nowNanos).songTimeSec // il tempo si ferma sul passo atteso, se ci si era già fermati lì
            clock.pause(nowNanos)
            waitFree = true // si resta dove si era arrivati
            return
        }
        clock.pause(nowNanos)
        holds.clear() // un tasto tenuto durante la pausa non si giudica
        lastTime = clock.tick(nowNanos).songTimeSec
    }

    fun setSpeed(newSpeed: Double, nowNanos: Long) {
        speed = newSpeed
        clock.setSpeed(newSpeed, nowNanos)
        tempo.toleranceSec = toleranceFor(newSpeed) // la finestra è costante in tempo reale (per un dato battito reale)
    }

    /** Gradini dell'allenamento progressivo. */
    private val speedSteps = doubleArrayOf(0.25, 0.5, 0.75, 1.0)

    private fun adjustSpeedAfterLap(hits: Int, misses: Int, wrong: Int, nowNanos: Long) {
        val total = hits + misses
        if (total == 0) return
        val i = speedSteps.indexOfFirst { kotlin.math.abs(it - speed) < 1e-6 }.takeIf { it >= 0 } ?: return
        val clean = misses == 0 && wrong == 0
        val messy = hits.toDouble() / total < 0.7 || wrong > maxOf(2, total / 4)
        val next = when {
            clean -> speedSteps.getOrElse(i + 1) { speed }
            messy -> speedSteps.getOrElse(i - 1) { speed }
            else -> speed
        }
        if (next != speed) setSpeed(next, nowNanos)
    }

    fun setLoopBars(startBar: Int?, endBar: Int?) {
        loopsCompleted = 0
        if (startBar != null && endBar != null && endBar >= startBar) {
            loopBars = startBar..endBar
            clock.setLoop(song.barStartSec(startBar), song.barEndSec(endBar))
        } else {
            loopBars = null
            clock.setLoop(null, null)
        }
    }

    /** Tempo del brano adesso, subito e senza effetti collaterali (anche prima del primo fotogramma). */
    fun songTimeAt(nowNanos: Long): Double = if (mode == StudyMode.TEMPO) clock.peek(nowNanos) else currentTimeSec

    /** Aggiorna l'orologio: da chiamare a ogni fotogramma mentre si suona. */
    fun tick(nowNanos: Long): SessionTick {
        if (mode == StudyMode.WAIT) return waitTick(nowNanos)
        val c = clock.tick(nowNanos)
        lastTime = c.songTimeSec
        var misses = 0
        if (mode == StudyMode.LISTEN) { // si ascolta soltanto: nessuna valutazione
            if (c.finished) finished = true
            if (c.looped) loopsCompleted++
            return SessionTick(lastTime, c.looped, c.finished, 0)
        }
        if (c.looped) {
            val loop = clock.loop!!
            // Chiude il giro giudicando solo le note del loop (la nota che cade sul punto di fine è della battuta dopo)
            misses += tempo.onTick(loop.endInclusive + tempo.toleranceSec + 1e-9, onlyBefore = loop.endInclusive)
            val (lapHits, lapMisses) = tempo.lapCounts(loop.start, loop.endInclusive) // in passi: un accordo conta uno
            lastLapHits = lapHits
            lastLapMisses = lapMisses
            if (speedTrainer) adjustSpeedAfterLap(lastLapHits, lastLapMisses, tempo.wrong, nowNanos)
            loopsCompleted++
            judgedChords.clear() // nuovo giro: gli accordi si commentano di nuovo
            tempo.resetRange(loop.start, loop.endInclusive)
        } else if (lastTime >= regionStartSec) {
            misses += tempo.onTick(lastTime)
        }
        if (c.finished) {
            finished = true
            misses += tempo.onTick(song.durationSec + tempo.toleranceSec + 1.0)
        }
        return SessionTick(lastTime, c.looped, c.finished, misses)
    }

    /** Orologio della modalità aspetta: scorre fino al passo da suonare e lì si ferma finché le note non arrivano. */
    private fun waitTick(nowNanos: Long): SessionTick {
        lastNow = nowNanos
        wait.expire(nowNanos)
        if (!clock.running) return SessionTick(currentTimeSec, looped = false, finished = wait.isFinished, newMisses = 0)
        var t = clock.tick(nowNanos).songTimeSec
        val next = wait.currentStep
        if (next == null) { // tutti i passi suonati: finito
            clock.pause(nowNanos)
            lastTime = t.coerceAtMost(song.durationSec)
            return SessionTick(lastTime, looped = false, finished = true, newMisses = 0)
        }
        if (t >= next.startSec) { // il cursore è arrivato a un passo non ancora suonato: aspetta qui
            clock.seek(next.startSec, nowNanos)
            t = next.startSec
        }
        lastTime = t
        return SessionTick(t, looped = false, finished = false, newMisses = 0)
    }

    // ---- ingresso delle note ----

    // ---- pause: tasto troppo presto, troppo tardi o suonato mentre si deve aspettare ----

    private var pendingTiming: TimingFeedback? = null
    private val timingCounts = java.util.EnumMap<TimingKind, Int>(TimingKind::class.java) // commenti formulati in questo giro, per tipo
    private val wrongKeyCounts = HashMap<Int, Int>()
    private fun noteTiming(fb: TimingFeedback?) { if (fb != null) timingCounts.merge(fb.kind, 1, Int::plus); pendingTiming = fb }

    /** Tutto ciò che si può misurare del giro in corso (per le statistiche). */
    data class RunStats(
        val noteHits: Int, val early: Int, val late: Int, val offBeat: Int, val meanDevMs: Int, val meanAbsDevMs: Int,
        val restEarly: Int, val restLate: Int, val restInRest: Int, val holdShort: Int, val holdLong: Int,
        val wrongKeys: Map<Int, Int>, val missedKeys: Map<Int, Int>, val barErrors: Map<Int, Int>,
    )

    fun runStats(): RunStats {
        val devs = if (mode == StudyMode.TEMPO) tempo.hitDeviations() else emptyList()
        val perfect = TimingWindow.perfectSongSec(avgSecPerBeat, speed)
        val sp = speed.coerceAtLeast(0.05)
        return RunStats(
            noteHits = devs.size, early = devs.count { it < -perfect }, late = devs.count { it > perfect }, offBeat = devs.count { Math.abs(it) > perfect },
            meanDevMs = if (devs.isEmpty()) 0 else Math.round(devs.average() / sp * 1000).toInt(),
            meanAbsDevMs = if (devs.isEmpty()) 0 else Math.round(devs.map { Math.abs(it) }.average() / sp * 1000).toInt(),
            restEarly = timingCounts[TimingKind.EARLY] ?: 0, restLate = timingCounts[TimingKind.LATE] ?: 0, restInRest = timingCounts[TimingKind.IN_REST] ?: 0,
            holdShort = timingCounts[TimingKind.TOO_SHORT] ?: 0, holdLong = timingCounts[TimingKind.TOO_LONG] ?: 0,
            wrongKeys = wrongKeyCounts.toMap(), missedKeys = if (mode == StudyMode.TEMPO) tempo.missedKeyCounts() else emptyMap(),
            barErrors = barReport().filter { it.errors > 0 }.associate { it.bar to it.errors },
        )
    }

    /** Negli esercizi, in «A tempo», si controlla anche per quanto si tiene premuto ogni tasto (fuori dagli esercizi la durata dei brani importati è troppo approssimata). */
    var checkHolds: Boolean = false

    private class Hold(val pressNanos: Long, val noteStartSec: Double, val durationSec: Double, val secPerBeat: Double, val restFollows: Boolean, val restEndSec: Double)
    private val holds = HashMap<Int, Hold>()
    private val judgedChords = HashSet<Long>() // accordi già commentati in questo giro: un commento solo, non uno per tasto
    private fun chordKey(startSec: Double) = Math.round(startSec * 1000)

    /** Un tasto è stato rilasciato: se era una nota colpita giusta, si giudica la durata. */
    fun onNoteOff(key: Int, nowNanos: Long = 0L) {
        val h = holds.remove(key) ?: return
        if (mode != StudyMode.TEMPO || !clock.running || finished) return
        val sp = speed.coerceAtLeast(0.05)
        val fb = HoldTiming.judge((nowNanos - h.pressNanos) / 1e9, h.durationSec / sp, h.secPerBeat / sp, h.restFollows, h.noteStartSec, h.noteStartSec + h.durationSec, h.restEndSec)
        if (fb != null && judgedChords.add(chordKey(h.noteStartSec))) noteTiming(fb)
    }

    /** Dopo una nota che finisce a [endSec] c'è davvero una pausa (e poi altre note)? Se sì, dice anche dove finisce. */
    private fun restAfter(endSec: Double, secPerBeat: Double): Pair<Boolean, Double> {
        val studied = song.notes.filter { hand == Hand.BOTH || it.hand == hand }
        if (hasNoteBetween(endSec, endSec + MIN_REST_BEATS * secPerBeat)) return false to endSec
        val next = studied.filter { it.startSec >= endSec - 1e-6 }.minOfOrNull { it.startSec } ?: return false to endSec
        return true to next
    }

    /** Il commento sull'ultima pressione (se ce n'è uno); si consuma una volta sola. */
    fun takeTimingFeedback(): TimingFeedback? { val f = pendingTiming; pendingTiming = null; return f }
    fun takeTimingFeedbackPeek(): Boolean = pendingTiming != null

    // «Impara»: l'ultimo passo suonato giusto (indice, istante nel brano, istante reale), per giudicare il ritmo col successivo
    private var prevWaitIdx = -1
    private var prevWaitStart = 0.0
    private var prevWaitPressNanos = 0L
    private fun resetWaitTiming() { prevWaitIdx = -1; pendingTiming = null; holds.clear(); judgedChords.clear(); timingCounts.clear(); wrongKeyCounts.clear() }

    /** A tempo: un tasto sbagliato è un tasto giusto fuori tempo, oppure suonato mentre non c'è nulla da suonare. */
    private fun timingForWrong(out: HitOutcome, songTimeSec: Double): TimingFeedback? {
        val near = out.nearMissSec
        val raw = if (near != null) {
            val noteSec = out.nearNoteSec ?: songTimeSec
            // si parla di pausa solo se prima di quella nota c'è davvero un vuoto (non una melodia che continua o una nota tenuta)
            val restStart = restStartBefore(noteSec)
            val restBeats = song.beatsBetween(restStart, noteSec)
            val between = if (near < 0) hasNoteBetween(songTimeSec, noteSec) else false // un tasto «presto» con altre note in mezzo non è una pausa saltata
            if (restBeats < MIN_REST_BEATS || between) {
                // nessuna pausa di mezzo: il tasto è quello giusto ma fuori tempo, e lo si dice lo stesso se è «quasi giusto» (senza colorare pause)
                if (abs(near) <= NEAR_HINT_BEATS * song.beatSecAt(noteSec)) RestTiming.nearMiss(near, noteSec, song.beatSecAt(noteSec))?.copy(fromSec = noteSec, toSec = noteSec) else null
            } else RestTiming.nearMiss(near, noteSec, song.beatSecAt(noteSec), restStart, restBeats)
        } else {
            val tol = tempo.toleranceSec
            val silent = song.notes.none { (hand == Hand.BOTH || it.hand == hand) && it.startSec - tol <= songTimeSec && songTimeSec < it.startSec + it.durationSec + tol }
            val next = steps.firstOrNull { it.startSec > songTimeSec + tol }
            val restLen = if (next != null) song.beatsBetween(restStartBefore(next.startSec), next.startSec) else 0.0
            if (silent && next != null && restLen >= MIN_REST_BEATS) RestTiming.inRest(songTimeSec, next.startSec, song.beatSecAt(next.startSec), restLen) else null
        }
        return raw?.copy(seconds = raw.seconds / speed.coerceAtLeast(0.05)) // i secondi si dicono in tempo reale, non del brano
    }

    /** Una nota colpita è stata suonata giusta ma non perfettamente a tempo? Null se la nota non è stata colpita. */
    fun isOffBeat(key: Int, startSec: Double): Boolean? {
        val dev = tempo.deviationAt(key, startSec) ?: return null
        return abs(dev) > TimingWindow.perfectSongSec(avgSecPerBeat, speed)
    }

    /** Dove finisce l'ultima nota (della mano studiata) che comincia prima di [noteSec]; 0 se non ce ne sono. Se supera [noteSec] la nota è tenuta: nessuna pausa. */
    private fun restStartBefore(noteSec: Double): Double =
        song.notes.filter { (hand == Hand.BOTH || it.hand == hand) && it.startSec < noteSec - 1e-6 }.maxOfOrNull { it.startSec + it.durationSec } ?: 0.0

    /** C'è una nota della mano studiata che suona (o comincia) fra [fromSec] e [toSec] (estremi esclusi)? */
    private fun hasNoteBetween(fromSec: Double, toSec: Double): Boolean =
        song.notes.any { (hand == Hand.BOTH || it.hand == hand) && it.startSec < toSec - 1e-6 && it.startSec + it.durationSec > fromSec + 1e-6 }

    /** «Impara»: lo scarto fra il ritmo dello spartito e quello suonato, fra due passi giusti consecutivi, se in mezzo c'è una pausa. */
    private fun judgeWaitGap(idxBefore: Int, stepBefore: com.gianlucacelante.midipiano.song.Step?, nowNanos: Long) {
        if (stepBefore == null) return
        if (prevWaitIdx >= 0 && prevWaitIdx == idxBefore - 1) {
            val prev = steps[prevWaitIdx]
            val prevEnd = prev.notes.maxOf { it.startSec + it.durationSec }
            val restLen = song.beatsBetween(prevEnd, stepBefore.startSec)
            val hadRest = restLen >= MIN_REST_BEATS // almeno una croma di vuoto in mezzo
            val expected = (stepBefore.startSec - prevWaitStart) / speed.coerceAtLeast(0.05) // a velocità ridotta lo spartito vuole più secondi reali
            val actual = (nowNanos - prevWaitPressNanos) / 1e9
            // il movimento in secondi reali: a velocità ridotta dura di più
            noteTiming(RestTiming.waitGap(expected, actual, hadRest, song.beatSecAt(stepBefore.startSec) / speed.coerceAtLeast(0.05), stepBefore.startSec, restLen))
        }
        prevWaitIdx = idxBefore; prevWaitStart = stepBefore.startSec; prevWaitPressNanos = nowNanos
    }

    fun onNoteOn(key: Int, nowNanos: Long = 0L): NoteResult {
        if (mode == StudyMode.WAIT) return onWaitNote(key, nowNanos)
        if (mode == StudyMode.LISTEN) return NoteResult.IGNORED
        if (!clock.running) return NoteResult.IGNORED // brano fermo o in pausa: i tasti provati non contano
        val t = tick(nowNanos)
        if (finished || t.songTimeSec < regionStartSec - tempo.toleranceSec) return NoteResult.IGNORED // conto alla rovescia
        val lp = clock.loop
        val out = tempo.onNoteOn(key, t.songTimeSec, lp?.start ?: Double.NaN, lp?.endInclusive ?: Double.NaN)
        return when (out.kind) {
            HitKind.HIT -> {
                val start = out.noteStartSec
                val dur = out.noteDurationSec
                if (checkHolds && start != null && dur != null && dur > 0.0) {
                    val spb = song.beatSecAt(start)
                    val (rest, restEnd) = restAfter(start + dur, spb)
                    holds[key] = Hold(nowNanos, start, dur, spb, rest, restEnd)
                }
                NoteResult.CORRECT
            }
            HitKind.WRONG -> {
                if (!out.foldedIntoPreviousError) wrongByBar.merge(song.barAt(t.songTimeSec.coerceAtLeast(0.0)), 1, Int::plus)
                wrongKeyCounts.merge(key, 1, Int::plus)
                noteTiming(timingForWrong(out, t.songTimeSec))
                NoteResult.WRONG
            }
        }
    }

    private fun onWaitNote(key: Int, nowNanos: Long): NoteResult {
        if (clock.running) { // con l'orologio in corsa si aggiorna il tempo e si ignora ciò che è troppo in anticipo
            lastTime = clock.peek(nowNanos).let { if (wait.currentStep != null && it >= wait.currentStep!!.startSec) wait.currentStep!!.startSec else it }
            val step = wait.currentStep
            if (step != null && lastTime < step.startSec - WAIT_EARLY_SEC) {
                // il tasto è quello giusto ma la pausa non è finita: si ignora, dicendo perché (solo se prima di quel passo c'è davvero una pausa)
                val restLen = song.beatsBetween(restStartBefore(step.startSec), step.startSec)
                if (key in step.keys && wait.index > 0 && restLen >= MIN_REST_BEATS) {
                    noteTiming(TimingFeedback(TimingKind.EARLY, (step.startSec - lastTime) / speed.coerceAtLeast(0.05), lastTime, step.startSec, beats = (step.startSec - lastTime) / song.beatSecAt(step.startSec), restBeats = restLen))
                }
                return NoteResult.IGNORED
            }
        }
        val before = wait.currentStep
        val idxBefore = wait.index
        val mistakesBefore = wait.mistakes
        val result = wait.onNoteOn(key, nowNanos)
        if (result == NoteResult.STEP_COMPLETED || result == NoteResult.FINISHED) judgeWaitGap(idxBefore, before, nowNanos)
        if (result == NoteResult.WRONG) wrongKeyCounts.merge(key, 1, Int::plus)
        if (result == NoteResult.WRONG && wait.mistakes > mistakesBefore) wrongByBar.merge(song.barAt((before?.startSec ?: lastTime).coerceAtLeast(0.0)), 1, Int::plus)
        if (result == NoteResult.STEP_COMPLETED || result == NoteResult.FINISHED) waitFree = false
        if (autoFlow && !clock.running && before != null && (result == NoteResult.STEP_COMPLETED) && loopBars == null) {
            clock.seek(before.startSec, nowNanos) // lo spartito scorre a tempo verso la nota successiva
            lastTime = before.startSec
            clock.start(nowNanos)
        }
        val loop = loopBars
        if (loop != null && (result == NoteResult.STEP_COMPLETED || result == NoteResult.FINISHED)) {
            val end = song.barEndSec(loop.last)
            val past = wait.isFinished || (wait.currentStep?.startSec ?: Double.MAX_VALUE) >= end - 1e-6
            if (past) { // fine del tratto: si ricomincia dall'inizio del loop
                val start = song.barStartSec(loop.first)
                wait.restart(steps.indexOfFirst { it.startSec >= start - 1e-6 }.coerceAtLeast(0)); waitRunStart = wait.index
                if (clock.running) { clock.seek(start, nowNanos); lastTime = start }
                loopsCompleted++
                return NoteResult.STEP_COMPLETED
            }
        }
        return result
    }

    // ---- posizione ----

    fun restart(nowNanos: Long = 0L) {
        resetWaitTiming()
        waitFree = false
        clock.pause(nowNanos)
        resetProgress(nowNanos)
    }

    /** Salta a una frazione del brano (0..1): in modalità aspetta ricomincia da quel passo, azzerando gli errori. */
    fun seekTo(fraction: Float) {
        resetWaitTiming()
        if (steps.isEmpty()) return
        val index = (fraction.coerceIn(0f, 1f) * steps.size).toInt().coerceAtMost(steps.size - 1)
        wait.restart(index); waitRunStart = wait.index
        judgedFromSec = steps[index].startSec
    }

    /** Salta a un istante del brano (modalità a tempo) o al passo corrispondente (modalità aspetta). */
    fun seekTo(songTimeSec: Double, nowNanos: Long) {
        resetWaitTiming()
        judgedFromSec = songTimeSec
        wrongByBar.clear()
        if (mode == StudyMode.WAIT) {
            // si riparte esattamente dal tempo scelto (anche su una pausa); la nota attesa è la prima da lì in poi
            val idx = steps.indexOfFirst { it.startSec >= songTimeSec - 1e-6 }
            wait.restart(if (idx < 0) (steps.size - 1).coerceAtLeast(0) else idx); waitRunStart = wait.index
            lastTime = songTimeSec
            waitFree = true
            if (clock.running) clock.seek(songTimeSec, nowNanos)
            return
        }
        clock.seek(songTimeSec, nowNanos)
        lastTime = songTimeSec
        finished = false
        tempo = TempoModeEngine(steps, tempo.toleranceSec).also { it.skipBefore(songTimeSec) }
    }

    fun seekToBar(bar: Int, nowNanos: Long) = seekTo(song.barStartSec(bar.coerceIn(1, song.barCount)), nowNanos)

    fun setHand(newHand: Hand) {
        resetWaitTiming()
        val keepWaitAt = if (mode == StudyMode.WAIT) currentTimeSec else -1.0
        hand = newHand
        steps = song.steps(newHand)
        wait = WaitModeEngine(steps)
        tempo = TempoModeEngine(steps, toleranceFor(speed))
        wrongByBar.clear()
        judgedFromSec = if (mode != StudyMode.WAIT) lastTime else maxOf(keepWaitAt, 0.0)
        if (mode != StudyMode.WAIT) tempo.skipBefore(lastTime) // cambiando mano a metà brano il passato non conta
        else if (keepWaitAt > regionStartSec + 1e-6) { // in "aspetta" si resta dov'eravamo: il primo passo della nuova mano da lì in poi
            val idx = steps.indexOfFirst { it.startSec >= keepWaitAt - 1e-6 }
            wait.restart(if (idx < 0) (steps.size - 1).coerceAtLeast(0) else idx); waitRunStart = wait.index
        }
        finished = false
    }

    private fun resetProgress(nowNanos: Long) {
        resetWaitTiming()
        wrongByBar.clear() // gli errori per battuta sono del giro in corso
        wait = WaitModeEngine(steps)
        waitRunStart = 0
        tempo = TempoModeEngine(steps, toleranceFor(speed))
        waitFree = false
        finished = false
        loopsCompleted = 0
        val start = loopBars?.let { song.barStartSec(it.first) } ?: 0.0
        judgedFromSec = start
        clock.seek(start, nowNanos)
        lastTime = start
        tempo.skipBefore(start)
    }

    private companion object {
        const val UPCOMING_NEAR = 0.55f
        const val UPCOMING_FAR = 0.2f
        const val NEAR_HINT_BEATS = 1.0 // un tasto giusto fuori finestra ma entro un battito dalla nota è «quasi giusto»: si dice presto/tardi
        const val LOOKAHEAD_SEC = 0.4 // note "in arrivo" da evidenziare sulla tastiera
        const val LEAD_IN_SEC = 3.0 // margine iniziale prima della prima nota
        const val WAIT_LOOKAHEAD_SEC = 0.8 // in modalità aspetta con l'orologio: i tasti si accendono poco prima dell'arrivo
        const val MIN_REST_BEATS = 0.5 // sotto mezzo movimento un vuoto è un'articolazione (staccato), non una pausa da contare
        const val WAIT_EARLY_SEC = 0.6 // un passo si può suonare al massimo così in anticipo
        const val END_PADDING_SEC = 0.3 // dopo l'ultima nota si può ancora suonare entro la tolleranza
    }
}

/**
 * Visibilità di una nota in arrivo (0..0,62) in funzione dei secondi che mancano: nulla a 2 s, cresce piano piano e
 * arriva a 0,62 quando entra fra le note «attese» (a [lookaheadSec] dall'attacco), dove diventa piena.
 */
fun upcomingWeightAt(dtSec: Double, lookaheadSec: Double = 0.4): Float {
    val x = ((dtSec - lookaheadSec) / (2.0 - lookaheadSec)).coerceIn(0.0, 1.0)
    return (0.62 * (1.0 - x)).toFloat()
}
