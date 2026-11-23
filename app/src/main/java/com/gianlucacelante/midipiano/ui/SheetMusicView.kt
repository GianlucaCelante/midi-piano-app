package com.gianlucacelante.midipiano.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.gianlucacelante.midipiano.notation.Accidental
import com.gianlucacelante.midipiano.notation.BeamGroup
import com.gianlucacelante.midipiano.notation.Beaming
import com.gianlucacelante.midipiano.notation.Chord
import com.gianlucacelante.midipiano.notation.Chords
import com.gianlucacelante.midipiano.notation.Clef
import com.gianlucacelante.midipiano.notation.KeySignatureSymbol
import com.gianlucacelante.midipiano.notation.NoteNames
import com.gianlucacelante.midipiano.notation.SpacingMap
import com.gianlucacelante.midipiano.notation.NoteState
import com.gianlucacelante.midipiano.notation.NotationLayout
import com.gianlucacelante.midipiano.notation.Rests
import com.gianlucacelante.midipiano.notation.StaffNote
import com.gianlucacelante.midipiano.notation.StaffRest
import com.gianlucacelante.midipiano.song.ChordSymbol
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import kotlin.math.abs

/** Dati già pronti per disegnare lo spartito di un brano (calcolati una volta sola, mai a ogni fotogramma). */
/** Cambio di metro e/o di armatura che comincia a [timeSec]. */
data class SignatureChange(val timeSec: Double, val time: Pair<Int, Int>?, val key: Int?)

class SheetData(
    val chords: List<Chord>, // ordinati per istante
    val beamGroups: List<BeamGroup>, // ordinati per istante
    val beamed: Set<Chord>, // per identità: niente hash dell'accordo a ogni fotogramma
    val keySignatures: Map<Clef, List<KeySignatureSymbol>>,
    val rests: List<StaffRest>, // ordinate per istante
    val chordSymbols: List<ChordSymbol>, // sigle degli accordi, ordinate per istante
    val barLines: List<Double>,
    val keyFifths: Int,
    val timeNumerator: Int,
    val timeDenominator: Int,
    val durationSec: Double = 0.0,
    val firstBarIsPickup: Boolean = false,
    val spacing: SpacingMap = SpacingMap.build(emptyList()),
    /** Cambi di metro e di armatura: (istante, metro nuovo o null, alterazioni nuove o null), per istante. */
    val changes: List<SignatureChange> = emptyList(),
    /** Armature (alterazioni -> simboli per chiave) usate nel brano, e quante alterazioni al massimo (per la larghezza fissa a sinistra). */
    val keySigsByFifths: Map<Int, Map<Clef, List<KeySignatureSymbol>>> = emptyMap(),
    val maxKeyCount: Int = abs(keyFifths),
    val timeAtStart: Pair<Int, Int> = timeNumerator to timeDenominator,
    val timeChangesList: List<com.gianlucacelante.midipiano.song.TimeChange> = emptyList(),
    val keyChangesList: List<com.gianlucacelante.midipiano.song.KeyChange> = emptyList(),
    val simplifiedSpans: List<ClosedFloatingPointRange<Double>> = emptyList(),
) {
    fun keyFifthsAt(sec: Double): Int { var r = keyFifths; for (c in keyChangesList) { if (c.timeSec <= sec + 1e-6) r = c.fifths else break }; return r }
    fun timeAt(sec: Double): Pair<Int, Int> { var r = timeAtStart; for (c in timeChangesList) { if (c.timeSec <= sec + 1e-6) r = c.numerator to c.denominator else break }; return r }

    /** Istante della prima nota: da qui parte il rigo. */
    val firstSec: Double = chords.minOfOrNull { it.startSec } ?: 0.0

    companion object {
        val EMPTY = SheetData(emptyList(), emptyList(), emptySet(), emptyMap(), emptyList(), emptyList(), emptyList(), 0, 4, 4)

        fun build(song: Song): SheetData {
            val chords = Chords.group(NotationLayout.layout(song, Hand.BOTH))
            val byClef = chords.groupBy { it.clef }
            val groups = byClef.values.flatMap { Beaming.groups(it, song) }.sortedBy { it.chords.first().startSec }
            val rests = byClef.values.flatMap { Rests.compute(it, song) }.sortedBy { it.startSec }
            val identity = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Chord, Boolean>())
            identity.addAll(Beaming.beamed(groups))
            val keySigs = Clef.values().associateWith { NotationLayout.keySignature(song.keyFifths, it) }
            val allFifths = (listOf(song.keyFifths) + song.keyChanges.map { it.fifths }).distinct()
            val byFifths = allFifths.associateWith { f -> Clef.values().associateWith { NotationLayout.keySignature(f, it) } }
            // cambi: si disegnano subito dopo la stanghetta e occupano spazio fra la stanghetta e la prima nota
            val times = (song.timeChanges.map { it.timeSec } + song.keyChanges.map { it.timeSec }).distinct().sorted()
            val changes = times.map { t ->
                SignatureChange(t, song.timeChanges.firstOrNull { abs(it.timeSec - t) < 1e-6 }?.let { it.numerator to it.denominator }, song.keyChanges.firstOrNull { abs(it.timeSec - t) < 1e-6 }?.fifths)
            }
            val extra = changes.associate { c -> c.timeSec to (0.8 + (c.key?.let { abs(it) * 1.1 + 0.4 } ?: 0.0) + (if (c.time != null) 2.4 else 0.0)) }
            return SheetData(chords, groups, identity, keySigs, rests, song.chordSymbols.sortedBy { it.timeSec }, song.barLines, song.keyFifths, song.timeNumerator, song.timeDenominator, song.durationSec, (song.barLines.firstOrNull() ?: Double.MAX_VALUE) < song.barDurationSec * 0.9, SpacingMap.build(chords, song.secPerBeat, song.barLines, song.durationSec, song::beatsBetween, extra),
                changes = changes, keySigsByFifths = byFifths, maxKeyCount = allFifths.maxOf { abs(it) }, timeAtStart = song.timeNumerator to song.timeDenominator,
                timeChangesList = song.timeChanges, keyChangesList = song.keyChanges, simplifiedSpans = song.simplifiedSpans)
        }
    }
}

// Glifi SMuFL del font Bravura (licenza SIL OFL 1.1, vedi assets/fonts/Bravura-OFL.txt)
private const val G_CLEF = ""
private const val F_CLEF = ""
private const val HEAD_WHOLE = ""
private const val HEAD_HALF = ""
private const val HEAD_BLACK = ""
private const val ACC_FLAT = ""
private const val ACC_NATURAL = ""
private const val ACC_SHARP = ""
private const val DOT = ""
private val FLAG_UP = arrayOf("", "", "") // croma, semicroma, biscroma (gambo in su)
private val FLAG_DOWN = arrayOf("", "", "")
private val CLEFS = Clef.values()
private val REST_GLYPHS: Map<Int, String> = listOf(0xE4E3, 0xE4E4, 0xE4E5, 0xE4E6, 0xE4E7, 0xE4E8).associateWith { String(Character.toChars(it)) }
private val TIME_DIGITS = Array(10) { String(Character.toChars(0xE080 + it)) }

private val Paper = Color(0xFFF8F9FB) // bianco-azzurro, come in Flowkey
private val Ink = Color(0xFF1B1B1B)
private val StaffLine = Color(0xFF555555)
private val PlayedInk = Color(0xFF1C1C1C) // come in Flowkey: le note già suonate restano scure (diverso da Ink solo per il confronto interno)
private val DimInk = Color(0xFFC4C4C4)
private val CurrentInk = Color(0xFFE65100)
private val MissedInk = Color(0xFFD32F2F)
private val CursorBand = Color(0x22000000)
private val LoopHandleInk = Color(0xFFF07F00)
private val GlowShort = Color(0xFFFFC107)    // note brevi: ambra
private val GlowMedium = Color(0xFFFF9800)   // un quarto: arancione
private val GlowLong = Color(0xFFFF5722)     // fino a due quarti: arancione rossastro
private val GlowVeryLong = Color(0xFFE91E63) // note molto lunghe: magenta

/** Legatura di valore: mezzaluna fra due teste, sotto la nota se il gambo va in su (dir = +1) e sopra se va in giù. */
private fun DrawScope.drawTie(x1: Float, x2: Float, y: Float, dir: Float, s: Float) {
    if (x2 - x1 < 1.2f * s) return
    val mid = (x1 + x2) / 2f
    val y0 = y + dir * 0.7f * s
    val path = Path().apply {
        moveTo(x1, y0)
        quadraticBezierTo(mid, y0 + dir * 2.5f * s, x2, y0)
        quadraticBezierTo(mid, y0 + dir * 1.7f * s, x1, y0)
        close()
    }
    drawPath(path, Ink)
}
private val LoopTint = Color(0x1AE65100)
private val SimplifiedTint = Color(0x121E88E5)

private const val HEAD_WIDTH = 1.18f // in spazi del rigo
private const val BEAM_THICKNESS = 0.5f
private const val BEAM_GAP = 0.8f
private const val STAFF_GAP_SPACES = 12.6f // da fondo rigo acuto a fondo rigo basso: 4 + 8,6 di intervallo (misurato su Flowkey)
private const val SYSTEM_SPACES = 9f + STAFF_GAP_SPACES + 5f // margine sopra + rigo acuto + intervallo + rigo basso + margine sotto

/**
 * Spartito a due righi che scorre in orizzontale. La parte sinistra (chiavi, armatura, tempo) è
 * fissa; il cursore indica il punto in cui ci si trova. Il tempo si legge solo in fase di disegno.
 */
@Composable
fun SheetMusicView(
    sheet: SheetData,
    timeProvider: () -> Float,
    noteState: (StaffNote) -> NoteState,
    hand: Hand,
    loopSec: ClosedFloatingPointRange<Double>? = null,
    showNoteNames: Boolean = false,
    modifier: Modifier = Modifier,
    loopBars: IntRange? = null,
    onScrubStart: () -> Unit = {},
    onScrub: (Double) -> Unit = {},
    /** Fine del trascinamento: [velocitySpacesPerSec] è la velocità in avanti (negativa se il dito andava a destra) in spazi del rigo al secondo. */
    onScrubEnd: (velocitySpacesPerSec: Double) -> Unit = {},
    glowByTime: Boolean = true,
    /** Letto in fase di disegno: true = scorrimento ammorbidito (play); false = posizione esatta (fermo o trascinato col dito). */
    smoothScroll: () -> Boolean = { true },
    onLoopEdit: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val typeface = remember { Typeface.createFromAsset(context.assets, "fonts/Bravura.otf") }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val numberPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(60, 60, 60); this.typeface = Typeface.SERIF } }
    val bracePath = remember { Path() }
    val fade = remember { FadeCache() }
    val chordPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(40, 40, 40); this.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) } }
    val lyricPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(40, 40, 40); textAlign = Paint.Align.CENTER; this.typeface = Typeface.SERIF } }
    val namePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(70, 70, 70); textAlign = Paint.Align.CENTER; isFakeBoldText = true } }

    val localScrub = remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) } // posizione del dito, letta dal disegno
    val barStartsList = remember(sheet) { listOf(0.0) + sheet.barLines }
    val gestures = Modifier.pointerInput(sheet, loopBars) {
        val s = minOf(13.dp.toPx(), size.height / SYSTEM_SPACES)
        val cursorX = size.width / 2f
        val sp = sheet.spacing
        fun barIndexAt(t: Double): Int { // numero di battuta (1..n) che contiene l'istante t
            var lo = 0
            var hi = barStartsList.size - 1
            while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (barStartsList[m] <= t + 1e-6) lo = m else hi = m - 1 }
            return lo + 1
        }
        fun barAtX(px: Float): Int {
            val tNow = timeProvider().toDouble()
            val xNow = if (smoothScroll() && localScrub.floatValue.isNaN()) sp.scrollAt(tNow) else sp.xAt(tNow)
            return barIndexAt(sp.timeAt(xNow + (px - cursorX) / s)).coerceIn(1, barStartsList.size)
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val xStart = sp.xAt(timeProvider().toDouble()) // il trascinamento parte dalla posizione esatta
            var anchorX = xStart // posizione di scorrimento (in spazi) che segue il dito, accumulata a ogni movimento
            var mode = 0 // 0 = scorrimento, 1 = maniglia sinistra del loop, 2 = destra, 3 = selezione col tocco prolungato
            var selStart = 1
            val lb = loopBars
            if (lb != null) {
                val endT = barStartsList.getOrElse(lb.last) { sheet.durationSec }
                val x1 = cursorX + ((sp.barX(barStartsList[lb.first - 1]) - xStart) * s).toFloat()
                val x2 = cursorX + ((sp.barX(endT) - xStart) * s).toFloat()
                val reach = 56.dp.toPx() // zona di presa ampia: le maniglie si afferrano senza precisione
                val d1 = abs(down.position.x - x1)
                val d2 = abs(down.position.x - x2)
                if (d1 < reach && d1 <= d2) mode = 1 else if (d2 < reach) mode = 2
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var started = false
            if (mode == 0) {
                // Il primo spostamento oltre la soglia avvia lo scorrimento (anche nei gesti rapidi); se il dito resta fermo
                // oltre il tempo del tocco prolungato si seleziona la battuta come loop.
                val moved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                }
                if (moved != null) {
                    started = true
                    onScrubStart()
                    tracker.addPosition(moved.uptimeMillis, moved.position)
                    anchorX -= (moved.position.x - down.position.x) / s
                    val tt = sp.timeAt(anchorX).coerceIn(0.0, sheet.durationSec)
                    localScrub.floatValue = tt.toFloat()
                    onScrub(tt)
                } else {
                    if (currentEvent.changes.none { it.id == down.id && it.pressed }) return@awaitEachGesture // tocco breve: lo gestisce il genitore
                    mode = 3 // tocco prolungato: seleziona la battuta; trascinando si estende
                    selStart = barAtX(down.position.x)
                    onLoopEdit(selStart, selStart)
                }
            }
            drag(down.id) { change ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                when (mode) {
                    0 -> {
                        anchorX -= change.positionChangeIgnoreConsumed().x / s // trascinando a sinistra si avanza (dopo consume() positionChange() vale 0)
                        val tt = sp.timeAt(anchorX).coerceIn(0.0, sheet.durationSec)
                        localScrub.floatValue = tt.toFloat()
                        onScrub(tt)
                    }
                    1 -> { val lbb = loopBars ?: return@drag; onLoopEdit(minOf(barAtX(change.position.x), lbb.last), lbb.last) }
                    2 -> { val lbb = loopBars ?: return@drag; onLoopEdit(lbb.first, maxOf(barAtX(change.position.x), lbb.first)) }
                    else -> { val b = barAtX(change.position.x); onLoopEdit(minOf(selStart, b), maxOf(selStart, b)) }
                }
            }
            localScrub.floatValue = Float.NaN
            if (mode == 0 && started) {
                val vx = tracker.calculateVelocity().x
                onScrubEnd(-vx.toDouble() / s) // il dito verso sinistra = avanti
            }
        }
    }
    Canvas(modifier = modifier.clipToBounds().then(gestures)) {
        val time = localScrub.floatValue.takeUnless { it.isNaN() } ?: timeProvider()
        paint.typeface = typeface
        // Un "spazio" del rigo: ~13 dp (Flowkey ~10, ma qui la tastiera e' piu' bassa e lo spartito ha piu' spazio); mai oltre l'altezza disponibile
        val s = minOf(13.dp.toPx(), size.height / SYSTEM_SPACES)
        val top = (size.height - SYSTEM_SPACES * s) / 2f // il sistema di righi sta al centro dell'area
        paint.textSize = 4f * s

        // Geometria verticale (in spazi): margine 5, violino 4, intervallo 6, basso 4, margine 5
        val trebleBottom = top + 9f * s
        val bassBottom = top + 9f * s + STAFF_GAP_SPACES * s
        val keyCount = sheet.maxKeyCount
        val panelW = (5.8f + keyCount * 1.1f + 3.2f) * s
        val cursorX = size.width / 2f // il cursore sta al centro, come in Flowkey
        val sp = sheet.spacing
        val xNow = if (smoothScroll() && localScrub.floatValue.isNaN()) sp.scrollAt(time.toDouble()) else sp.xAt(time.toDouble()) // in play: media mobile (niente scatti a ogni nota); fermo o col dito: esatto
        val canvas = drawContext.canvas.nativeCanvas

        fun yOf(clef: Clef, pos: Int) = (if (clef == Clef.TREBLE) trebleBottom else bassBottom) - pos * s / 2f
        fun xOf(timeSec: Double) = cursorX + ((sp.xAt(timeSec) - xNow) * s).toFloat()
        fun bx(t: Double) = cursorX + ((sp.barX(t) - xNow) * s).toFloat() // x della stanghetta che apre la battuta a t
        fun glyph(text: String, x: Float, y: Float, color: Color) {
            paint.color = color.toArgb()
            canvas.drawText(text, x, y, paint)
        }

        drawRect(Paper, Offset.Zero, size)
        // Chiavi e righi scorrono con la musica: all'inizio stanno subito a sinistra della prima nota, poi restano fissi a sinistra
        val firstX = xOf(sheet.firstSec)
        val panelX = maxOf(0f, firstX - 2.6f * s - panelW)
        val notesLeft = panelX + panelW
        val endPx = if (sp.hasEnd) cursorX + ((sp.endX - xNow) * s).toFloat() else Float.MAX_VALUE
        drawStaves(s, trebleBottom, bassBottom, panelX, minOf(size.width, endPx))

        // Note, pause e stanghette: scorrono sotto la parte fissa
        clipRect(left = notesLeft) {
            // finestra visibile, in secondi: si ricava dalla spaziatura (le note veloci occupano più spazio)
            val tLeft = sp.timeAt(xNow - ((cursorX - notesLeft) / s + 3.0)).toFloat()
            val tRight = sp.timeAt(xNow + ((size.width - cursorX) / s + 3.0)).toFloat()
            numberPaint.textSize = 1.7f * s
            namePaint.textSize = 1.5f * s
            loopSec?.let { loop ->
                val x1 = bx(loop.start).coerceAtLeast(notesLeft)
                val x2 = bx(loop.endInclusive).coerceAtMost(size.width)
                if (x2 > x1) drawRect(LoopTint, Offset(x1, trebleBottom - 6f * s), Size(x2 - x1, bassBottom - trebleBottom + 10f * s))
                val e1 = bx(loop.start)
                val e2 = bx(loop.endInclusive)
                if (e1 in notesLeft..size.width) drawLine(CurrentInk, Offset(e1, trebleBottom - 6f * s), Offset(e1, bassBottom + 4f * s), strokeWidth = 3f)
                if (e2 in notesLeft..size.width) drawLine(CurrentInk, Offset(e2, trebleBottom - 6f * s), Offset(e2, bassBottom + 4f * s), strokeWidth = 3f)
            }

            // battute cambiate dalla versione semplificata: velo leggero (la versione facile dice dove ha semplificato)
            for (span in sheet.simplifiedSpans) {
                if (span.endInclusive < tLeft || span.start > tRight) continue
                val sx = maxOf(bx(span.start), notesLeft)
                val ex = minOf(bx(span.endInclusive), size.width)
                if (ex > sx) drawRect(SimplifiedTint, Offset(sx, trebleBottom - 5.2f * s), Size(ex - sx, bassBottom - trebleBottom + 9.2f * s))
            }
            val firstBar = findFirst(sheet.barLines.size, tLeft - 2f) { sheet.barLines[it] }
            for (index in firstBar until sheet.barLines.size) {
                val bar = sheet.barLines[index]
                if (bar > tRight + 2f) break
                val x = cursorX + ((sp.barX(bar) - xNow) * s).toFloat()
                if (x in notesLeft..size.width) {
                    drawLine(StaffLine, Offset(x, trebleBottom - 4f * s), Offset(x, bassBottom), strokeWidth = 1.5f)
                    val label = (index + (if (sheet.firstBarIsPickup) 1 else 2)).toString() // con l'attacco in levare la prima battuta intera e' la 1
                    canvas.drawText(label, x - 0.3f * s - numberPaint.measureText(label), trebleBottom - 4.8f * s, numberPaint) // numero della battuta che inizia qui
                }
            }

            // cambi di armatura e di metro in mezzo al brano, subito dopo la stanghetta
            for (ch in sheet.changes) {
                if (ch.timeSec < tLeft - 2f || ch.timeSec > tRight + 2f) continue
                val x0 = bx(ch.timeSec) + 0.7f * s
                for (clef in CLEFS) {
                    var cx = x0
                    ch.key?.let { f ->
                        sheet.keySigsByFifths[f]?.get(clef).orEmpty().forEachIndexed { idx, sym -> glyph(accidentalGlyph(sym.accidental), x0 + idx * 1.1f * s, yOf(clef, sym.position), Ink) }
                        cx += (abs(f) * 1.1f + 0.4f) * s
                    }
                    ch.time?.let { (n, d) -> drawTimeSignature(n, d, cx, s, ::glyph) { pos -> yOf(clef, pos) } }
                }
            }

            chordPaint.textSize = 2.1f * s
            lyricPaint.textSize = 1.9f * s
            val symbols = sheet.chordSymbols
            var cs = findFirst(symbols.size, tLeft - 2f) { symbols[it].timeSec }
            while (cs < symbols.size && symbols[cs].timeSec <= tRight) {
                val sym = symbols[cs++]
                canvas.drawText(sym.text, xOf(sym.timeSec) + 0.3f * s, trebleBottom - 6.9f * s, chordPaint)
            }

            val rests = sheet.rests
            var r = findFirst(rests.size, tLeft) { rests[it].startSec }
            while (r < rests.size && rests[r].startSec <= tRight) {
                val rest = rests[r++]
                drawRest(rest, xOf(rest.startSec), s, ::yOf, ::glyph)
            }

            val chords = sheet.chords
            var i = findFirst(chords.size, tLeft) { chords[it].startSec }
            var hyphenFrom = Float.NaN // fine della sillaba precedente se finiva con un trattino
            val headW = HEAD_WIDTH * s
            while (i < chords.size && chords[i].startSec <= tRight) {
                val c = chords[i++]
                val beamedChord = c in sheet.beamed
                val cx = xOf(c.startSec)
                if (c.clef == Clef.TREBLE) { // sillaba sotto la nota, fra i due righi
                    val raw = c.notes.firstNotNullOfOrNull { it.lyric }
                    if (raw != null) {
                        val hyphen = raw.endsWith("-")
                        val text = if (hyphen) raw.dropLast(1) else raw
                        val lx = cx
                        val half = lyricPaint.measureText(text) / 2f
                        val ly = trebleBottom + 3.8f * s
                        canvas.drawText(text, lx, ly, lyricPaint)
                        if (!hyphenFrom.isNaN()) canvas.drawText("-", (hyphenFrom + lx - half) / 2f, ly, lyricPaint) // trattino al centro fra le due sillabe
                        hyphenFrom = if (hyphen) lx + half else Float.NaN
                    }
                }
                for (n in c.notes) { // bagliore dietro le teste da suonare ora
                    if (hand != Hand.BOTH && n.hand != hand) continue
                    val tNow = time.toDouble()
                    val byTime = glowByTime && tNow >= n.startSec - 0.02 && tNow < n.startSec + n.durationSec
                    if (noteState(n) != NoteState.CURRENT && !byTime) continue
                    // intensità: piena all'attacco e sempre più debole finché la nota finisce; colore: più caldo per le note più lunghe
                    val left = if (byTime) (1.0 - ((tNow - n.startSec) / n.durationSec).coerceIn(0.0, 1.0)).toFloat() else 1f
                    val base = when {
                        n.beats <= 0.5 -> GlowShort
                        n.beats <= 1.0 -> GlowMedium
                        n.beats <= 2.0 -> GlowLong
                        else -> GlowVeryLong
                    }
                    val gx = cx + (if (n.shifted) headW else 0f) // il centro della testa
                    val gy = yOf(c.clef, n.position)
                    // alone molto tenue e un anello sottile attorno alla testa: la nota resta nera e leggibile
                    drawCircle(base.copy(alpha = 0.06f + 0.10f * left), (1.4f + 0.3f * left) * s, Offset(gx, gy))
                    drawCircle(base.copy(alpha = 0.55f + 0.40f * left), (0.95f + 0.2f * left) * s, Offset(gx, gy), style = Stroke(0.15f * s))
                }
                drawChord(c, cx, s, ::yOf, ::glyph, stem = !beamedChord, names = if (showNoteNames) namePaint else null) { n -> inkFor(n, noteState, hand) }
                for (n in c.notes) { // legatura: arco fino alla parte successiva, dalla parte opposta al gambo
                    if (n.tieNext) {
                        val x1 = cx + headW + (if (n.dotted) 0.95f * s else 0.2f * s)
                        val x2 = xOf(n.startSec + n.durationSec) - headW * 0.15f
                        drawTie(x1, x2, yOf(c.clef, n.position), if (n.stemUp) 1f else -1f, s)
                    }
                }
            }

            val groups = sheet.beamGroups
            var g = findFirst(groups.size, tLeft - 2f) { groups[it].chords.first().startSec }
            while (g < groups.size && groups[g].chords.first().startSec <= tRight) {
                drawBeamGroup(groups[g++], ::xOf, s, ::yOf) { n -> inkFor(n, noteState, hand) }
            }
        }

        // Dissolvenza: le note che scorrono verso sinistra sfumano prima di sparire sotto la chiave
        drawRect(fade.brush(notesLeft, 7f * s), Offset(notesLeft, 0f), Size(7f * s, size.height))
        drawStaves(s, trebleBottom, bassBottom, notesLeft, notesLeft + 7f * s) // le linee del rigo restano piene

        // Doppia stanghetta finale: sottile + spessa, dopo di essa il rigo non continua
        if (sp.hasEnd && endPx > notesLeft - 2f * s && endPx < size.width + 2f * s) {
            drawLine(StaffLine, Offset(endPx - 0.75f * s, trebleBottom - 4f * s), Offset(endPx - 0.75f * s, bassBottom), strokeWidth = 1.5f)
            drawRect(Ink, Offset(endPx - 0.45f * s, trebleBottom - 4f * s), Size(0.45f * s, bassBottom - trebleBottom + 4f * s))
        }
        // Parte fissa a sinistra: carta + righi + graffa + chiavi + armatura + tempo
        drawRect(Paper, Offset(panelX, 0f), Size(panelW, size.height))
        drawStaves(s, trebleBottom, bassBottom, panelX, panelX + panelW)
        drawLine(StaffLine, Offset(panelX + 1.9f * s, trebleBottom - 4f * s), Offset(panelX + 1.9f * s, bassBottom), strokeWidth = 2f)
        drawBrace(bracePath, panelX + 1.9f * s, trebleBottom - 4f * s, bassBottom, s)
        glyph(G_CLEF, panelX + 2.5f * s, yOf(Clef.TREBLE, 2), Ink)
        glyph(F_CLEF, panelX + 2.5f * s, yOf(Clef.BASS, 6), Ink)
        // armatura e metro in vigore adesso (cambiano durante il brano): la larghezza resta quella dell'armatura più lunga
        val nowSec = time.toDouble().coerceAtLeast(0.0)
        val nowKey = sheet.keySigsByFifths[sheet.keyFifthsAt(nowSec)] ?: sheet.keySignatures
        val (nowNum, nowDen) = sheet.timeAt(nowSec)
        for (clef in CLEFS) {
            nowKey[clef].orEmpty().forEachIndexed { idx, sym ->
                glyph(accidentalGlyph(sym.accidental), panelX + (6.0f + idx * 1.1f) * s, yOf(clef, sym.position), Ink)
            }
            drawTimeSignature(nowNum, nowDen, panelX + (5.8f + keyCount * 1.1f + 0.2f) * s, s, ::glyph) { pos -> yOf(clef, pos) }
        }

        // Cursore: banda grigia semitrasparente sulla posizione corrente, su entrambi i righi
        drawRect(CursorBand, Offset(cursorX - 1.2f * s, 0f), Size(2.4f * s, size.height)) // banda a tutta altezza
        // Maniglie del loop (come in Flowkey: linguette bianche con una freccia arancione)
        loopSec?.let { loop ->
            val mid = (trebleBottom + bassBottom) / 2f
            val hw = 16.dp.toPx()
            val hh = 58.dp.toPx()
            for ((lx, dir) in listOf(bx(loop.start) to -1f, bx(loop.endInclusive) to 1f)) {
                if (lx < notesLeft - hw || lx > size.width + hw) continue
                drawRoundRect(Color.White, Offset(lx - hw, mid - hh / 2f), Size(2 * hw, hh), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                drawRoundRect(Color(0xFFCCCCCC), Offset(lx - hw, mid - hh / 2f), Size(2 * hw, hh), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                val a = 5.dp.toPx()
                drawLine(LoopHandleInk, Offset(lx + dir * a * 0.5f, mid - a * 1.4f), Offset(lx - dir * a * 0.5f, mid), strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(LoopHandleInk, Offset(lx - dir * a * 0.5f, mid), Offset(lx + dir * a * 0.5f, mid + a * 1.4f), strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }

    }
}

private val GhostLine = Color(0x22000000)

private val tupletPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC) }

/** Il gradiente di dissolvenza è lo stesso a ogni fotogramma: si ricostruisce solo se cambia la posizione o la scala. */
private class FadeCache {
    private var left = Float.NaN
    private var width = Float.NaN
    private var cached: androidx.compose.ui.graphics.Brush? = null
    fun brush(l: Float, w: Float): androidx.compose.ui.graphics.Brush {
        val c = cached
        if (c != null && l == left && w == width) return c
        left = l; width = w
        return androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Paper, Paper.copy(alpha = 0f)), startX = l, endX = l + w).also { cached = it }
    }
}

private val ghostPaths = object : LinkedHashMap<List<Float>, Path>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Float>, Path>?) = size > 8
}

private fun ghostPath(s: Float, trebleBottom: Float, bassBottom: Float, x0: Float, x1: Float): Path =
    ghostPaths.getOrPut(listOf(s, trebleBottom, bassBottom, x0, x1)) {
        Path().apply {
            for (k in 1..3) for (bottom in floatArrayOf(trebleBottom, bassBottom)) for (y in floatArrayOf(bottom + k * s, bottom - (4 + k) * s)) {
                var x = x0
                while (x < x1) { addRect(androidx.compose.ui.geometry.Rect(x, y - 0.5f, minOf(x + 0.45f * s, x1), y + 0.5f)); x += s }
            }
        }
    }

private fun DrawScope.drawStaves(s: Float, trebleBottom: Float, bassBottom: Float, x0: Float, x1: Float) {
    // Linee supplementari "fantasma": tratteggiate e quasi trasparenti, tre sopra e tre sotto ogni rigo.
    // Il tratteggio è un unico percorso costruito una volta (con PathEffect si ricalcolava a ogni fotogramma).
    drawPath(ghostPath(s, trebleBottom, bassBottom, x0, x1), GhostLine)
    for (line in 0..4) {
        val y1 = trebleBottom - line * s
        val y2 = bassBottom - line * s
        drawLine(StaffLine, Offset(x0, y1), Offset(x1, y1), strokeWidth = 1.5f)
        drawLine(StaffLine, Offset(x0, y2), Offset(x1, y2), strokeWidth = 1.5f)
    }
}

/** Graffa che unisce i due righi, a sinistra della linea iniziale (disegno originale con curve). */
private fun DrawScope.drawBrace(path: Path, x: Float, top: Float, bottom: Float, s: Float) {
    val mid = (top + bottom) / 2f
    val w = 1.6f * s
    path.reset()
    path.moveTo(x, top)
    path.cubicTo(x - w, top + (mid - top) * 0.25f, x, top + (mid - top) * 0.75f, x - w, mid)
    path.cubicTo(x, mid + (bottom - mid) * 0.25f, x - w, mid + (bottom - mid) * 0.75f, x, bottom)
    path.cubicTo(x - w * 0.45f, mid + (bottom - mid) * 0.6f, x - w * 1.0f, mid + (bottom - mid) * 0.15f, x - w * 1.05f, mid)
    path.cubicTo(x - w * 1.0f, top + (mid - top) * 0.85f, x - w * 0.45f, top + (mid - top) * 0.4f, x, top)
    path.close()
    drawPath(path, Ink)
}

private fun inkFor(n: StaffNote, noteState: (StaffNote) -> NoteState, hand: Hand): Color = when {
    hand != Hand.BOTH && n.hand != hand -> DimInk // l'altra mano: solo come contesto
    else -> when (noteState(n)) {
        NoteState.PLAYED -> PlayedInk
        NoteState.CURRENT -> Ink // la nota corrente resta nera: la evidenzia il bagliore
        NoteState.UPCOMING -> Ink
        NoteState.MISSED -> MissedInk
    }
}

private fun accidentalGlyph(a: Accidental) = when (a) {
    Accidental.SHARP -> ACC_SHARP
    Accidental.FLAT -> ACC_FLAT
    Accidental.NATURAL -> ACC_NATURAL
}

/** Teste, alterazioni, punti e tagli di un accordo; il gambo e le code solo se [stem] (altrimenti li disegna la trave). */
private fun DrawScope.drawChord(
    chord: Chord,
    centerX: Float,
    s: Float,
    yOf: (Clef, Int) -> Float,
    glyph: (String, Float, Float, Color) -> Unit,
    stem: Boolean,
    names: Paint?,
    inkOf: (StaffNote) -> Color,
) {
    val clef = chord.clef
    val notes = chord.notes
    val headW = HEAD_WIDTH * s
    val left = centerX - headW / 2f
    val hasShifted = notes.any { it.shifted }

    for (n in notes) { // tagli addizionali (poche note: nessuna allocazione)
        for (pos in n.ledgerPositions) {
            val ly = yOf(clef, pos)
            val right = left + headW * (if (hasShifted) 2 else 1)
            drawLine(StaffLine, Offset(left - 0.45f * s, ly), Offset(right + 0.45f * s, ly), strokeWidth = 1.5f)
        }
    }
    var accIndex = 0 // alterazioni sfalsate per non sovrapporsi
    for (k in notes.indices.reversed()) {
        val n = notes[k]
        n.accidental?.let { glyph(accidentalGlyph(it), left - (1.15f + accIndex++ * 0.95f) * s, yOf(clef, n.position), inkOf(n)) }
    }
    for (n in notes) {
        val x = left + if (n.shifted) headW else 0f
        val y = yOf(clef, n.position)
        glyph(if (n.hasStem) (if (n.filled) HEAD_BLACK else HEAD_HALF) else HEAD_WHOLE, x, y, inkOf(n))
        if (names != null) { // nome in solfeggio sotto la testa (o sopra se il gambo scende)
            val down = !chord.stemUp
            drawContext.canvas.nativeCanvas.drawText(n.noteName, x + headW / 2f, if (down) y - 1.1f * s else y + 2.0f * s, names)
        }
        if (n.dotted) glyph(DOT, x + headW + 0.35f * s, if (n.position % 2 == 0) y - 0.5f * s else y, inkOf(n))
    }

    if (!stem || !chord.hasStem) return
    val ink = inkOf(notes[0])
    val stemLen = 3.5f * s
    val yLow = yOf(clef, chord.lowest.position)
    val yHigh = yOf(clef, chord.highest.position)
    val up = chord.stemUp
    val stemX = if (up) left + headW - 0.06f * s else left + 0.06f * s
    val yEnd = if (up) yHigh - stemLen else yLow + stemLen
    drawLine(ink, Offset(stemX, if (up) yLow else yHigh), Offset(stemX, yEnd), strokeWidth = 0.12f * s)
    if (chord.flags > 0) glyph((if (up) FLAG_UP else FLAG_DOWN)[chord.flags.coerceAtMost(3) - 1], stemX, yEnd, ink)
}

/** Gambi e travi di un gruppo di accordi brevi (trave piatta; livelli 2 e 3 con "mozziconi" per le note isolate). */
private fun DrawScope.drawBeamGroup(
    group: BeamGroup,
    xOf: (Double) -> Float,
    s: Float,
    yOf: (Clef, Int) -> Float,
    inkOf: (StaffNote) -> Color,
) {
    val chords = group.chords
    val clef = chords[0].clef
    var minLow = Int.MAX_VALUE
    var maxHigh = Int.MIN_VALUE
    for (c in chords) {
        if (c.lowest.position < minLow) minLow = c.lowest.position
        if (c.highest.position > maxHigh) maxHigh = c.highest.position
    }
    val up = (4 - minLow) >= (maxHigh - 4)
    val stemLen = 3.3f * s
    val headW = HEAD_WIDTH * s

    var beamY = if (up) Float.MAX_VALUE else -Float.MAX_VALUE
    for (c in chords) {
        val tip = if (up) yOf(clef, c.highest.position) - stemLen else yOf(clef, c.lowest.position) + stemLen
        beamY = if (up) minOf(beamY, tip) else maxOf(beamY, tip)
    }
    var allPlayed = true
    for (c in chords) if (inkOf(c.notes[0]) != PlayedInk) { allPlayed = false; break }
    val beamInk = if (allPlayed) PlayedInk else Ink

    fun stemX(c: Chord): Float {
        val left = xOf(c.startSec) - headW / 2f
        return if (up) left + headW - 0.06f * s else left + 0.06f * s
    }
    for (c in chords) {
        val x = stemX(c)
        drawLine(inkOf(c.notes[0]), Offset(x, if (up) yOf(clef, c.lowest.position) else yOf(clef, c.highest.position)), Offset(x, beamY), strokeWidth = 0.12f * s)
    }
    // terzina: il numero 3 sopra la trave (gambi in su) o sotto (gambi in giù)
    if (chords.size >= 3 && chords.all { c -> c.notes.any { it.isTriplet } }) {
        val cx = (stemX(chords.first()) + stemX(chords.last())) / 2f
        tupletPaint.textSize = 1.7f * s
        tupletPaint.color = beamInk.toArgb()
        val label = "3"
        drawContext.canvas.nativeCanvas.drawText(label, cx - tupletPaint.measureText(label) / 2f, if (up) beamY - 0.7f * s else beamY + 2.1f * s, tupletPaint)
    }
    for (level in 0 until group.maxLevel) {
        val top = if (up) beamY + level * BEAM_GAP * s else beamY - BEAM_THICKNESS * s - level * BEAM_GAP * s
        var i = 0
        while (i < chords.size) {
            if (chords[i].flags <= level) { i++; continue }
            var j = i
            while (j + 1 < chords.size && chords[j + 1].flags > level) j++
            val xa = stemX(chords[i])
            val xb = stemX(chords[j])
            if (j > i) {
                drawRect(beamInk, Offset(xa - 0.06f * s, top), Size(xb - xa + 0.12f * s, BEAM_THICKNESS * s))
            } else { // nota isolata a quel livello: mozzicone verso il vicino
                val stubLen = 1.3f * s
                val x0 = if (i > 0) xa - stubLen else xa
                drawRect(beamInk, Offset(x0, top), Size(stubLen, BEAM_THICKNESS * s))
            }
            i = j + 1
        }
    }
}

private fun DrawScope.drawRest(
    rest: StaffRest,
    x: Float,
    s: Float,
    yOf: (Clef, Int) -> Float,
    glyph: (String, Float, Float, Color) -> Unit,
) {
    val (code, dotted) = when {
        rest.beats >= 4.0 -> 0xE4E3 to false
        rest.beats >= 3.0 -> 0xE4E4 to true
        rest.beats >= 2.0 -> 0xE4E4 to false
        rest.beats >= 1.5 -> 0xE4E5 to true
        rest.beats >= 1.0 -> 0xE4E5 to false
        rest.beats >= 0.75 -> 0xE4E6 to true
        rest.beats >= 0.5 -> 0xE4E6 to false
        rest.beats >= 0.25 -> 0xE4E7 to false
        else -> 0xE4E8 to false
    }
    val y = yOf(rest.clef, if (code == 0xE4E3) 6 else 4) // la pausa di semibreve pende dalla quarta linea; le altre stanno sulla centrale
    glyph(REST_GLYPHS.getValue(code), x + 0.5f * s, y, Ink)
    if (dotted) glyph(DOT, x + 2.1f * s, yOf(rest.clef, 5), Ink)
}

private fun DrawScope.drawTimeSignature(
    numerator: Int,
    denominator: Int,
    x: Float,
    s: Float,
    glyph: (String, Float, Float, Color) -> Unit,
    yOfPos: (Int) -> Float,
) {
    numerator.toString().forEachIndexed { i, d -> glyph(TIME_DIGITS[d - '0'], x + i * 1.6f * s, yOfPos(6), Ink) }
    denominator.toString().forEachIndexed { i, d -> glyph(TIME_DIGITS[d - '0'], x + i * 1.6f * s, yOfPos(2), Ink) }
}

/** Primo indice in [0, size) il cui tempo (da [timeAt]) è >= [time]; la sequenza è ordinata. */
private inline fun findFirst(size: Int, time: Float, timeAt: (Int) -> Double): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (timeAt(mid) < time) lo = mid + 1 else hi = mid
    }
    return lo
}
