package com.gianlucacelante.midipiano.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import com.gianlucacelante.midipiano.notation.NoteNames
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

fun isBlackKey(note: Int): Boolean = (note % 12) in BLACK_PITCH_CLASSES

val PressedColor = Color(0xFF4FC3F7)
val ExpectedColor = Color(0xFFFFB74D)

/** Colori per mano: destra arancione, sinistra viola (gli stessi dei pulsanti delle mani). */
object HandColors {
    fun main(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF7E57C2) else Color(0xFFF07F00)
    fun keyTop(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFFB39DDB) else Color(0xFFFFB74D)
    fun keyBottom(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFFEDE7F6) else Color(0xFFFFE0B2)
    fun blackTop(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF9575CD) else Color(0xFFFB8C00)
    fun blackBottom(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF4527A0) else Color(0xFFD35400)
    // tasto premuto: lo stesso colore della mano che lo preme, ma più scuro del tasto «atteso»
    fun pressedTop(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF7E57C2) else Color(0xFFF07F00)
    fun pressedBottom(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF9575CD) else Color(0xFFD97200)
    fun pressedBlackTop(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF5E35B1) else Color(0xFFC25E00)
    fun pressedBlackBottom(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) Color(0xFF311B92) else Color(0xFF8A3A00)
    fun label(hand: com.gianlucacelante.midipiano.song.Hand) = if (hand == com.gianlucacelante.midipiano.song.Hand.LEFT) android.graphics.Color.rgb(69, 39, 160) else android.graphics.Color.rgb(150, 60, 0)
}

/**
 * Tastiera a schermo con, sopra i tasti attesi, il loro nome in solfeggio. Il tocco sullo schermo
 * invia [onKeyDown]/[onKeyUp] (utile per provare senza una tastiera MIDI).
 */
@Composable
fun PianoKeyboard(
    pressed: Set<Int>,
    modifier: Modifier = Modifier,
    range: IntRange = 36..96,
    expected: Set<Int> = emptySet(),
    height: Dp = 150.dp,
    onKeyDown: (Int) -> Unit = {},
    onKeyUp: (Int) -> Unit = {},
    preferFlats: Boolean = false,
    wrong: Set<Int> = emptySet(),
    expectedHands: Map<Int, com.gianlucacelante.midipiano.song.Hand> = emptyMap(),
    fingers: Map<Int, Int> = emptyMap(),
    upcomingHands: Map<Int, com.gianlucacelante.midipiano.song.Hand> = emptyMap(),
    upcomingFingers: Map<Int, Int> = emptyMap(),
    upcomingWeights: Map<Int, Float> = emptyMap(),
    /** A tempo: istanti d'attacco delle note in arrivo + orologio, per far comparire i tasti piano piano a ogni fotogramma. */
    upcomingTimes: Map<Int, Double> = emptyMap(),
    timeProvider: (() -> Float)? = null,
    /** Scala (0..1) dei pesi fissi di «Impara»: serve a farli comparire con una dissolvenza. */
    weightScale: () -> Float = { 1f },
    /** La mano che si sta studiando (per colorare i tasti premuti che non sono fra quelli attesi). */
    studiedHand: com.gianlucacelante.midipiano.song.Hand? = null,
) {
    KeyboardCanvas(pressed, modifier, range, expected, height, onKeyDown, onKeyUp, wrong, preferFlats, expectedHands, fingers, upcomingHands, upcomingFingers, upcomingWeights, upcomingTimes, timeProvider, weightScale, studiedHand)
}

@Composable
private fun KeyboardCanvas(
    pressed: Set<Int>,
    modifier: Modifier,
    range: IntRange,
    expected: Set<Int>,
    height: Dp,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    wrong: Set<Int> = emptySet(),
    preferFlats: Boolean = false,
    expectedHands: Map<Int, com.gianlucacelante.midipiano.song.Hand> = emptyMap(),
    fingers: Map<Int, Int> = emptyMap(),
    upcomingHands: Map<Int, com.gianlucacelante.midipiano.song.Hand> = emptyMap(),
    upcomingFingers: Map<Int, Int> = emptyMap(),
    upcomingWeights: Map<Int, Float> = emptyMap(),
    upcomingTimes: Map<Int, Double> = emptyMap(),
    timeProvider: (() -> Float)? = null,
    weightScale: () -> Float = { 1f },
    studiedHand: com.gianlucacelante.midipiano.song.Hand? = null,
) {
    val down by rememberUpdatedState(onKeyDown)
    val namePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) } }
    val up by rememberUpdatedState(onKeyUp)
    val brushes = remember { PlainKeyBrushes() }
    val geometry = remember { GeometryCache() }
    val baseCache = remember { BaseKeyboardCache() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(range) {
                awaitEachGesture {
                    // Layout ricalcolato a ogni gesto con la dimensione attuale (rotazione, multiwindow)
                    val layout = KeyboardLayout(range, size.width.toFloat(), size.height.toFloat())
                    val active = HashMap<PointerId, Int>() // dito -> tasto premuto
                    try {
                        val first = awaitFirstDown()
                        layout.keyAt(first.position.x, first.position.y)?.let {
                            active[first.id] = it
                            down(it)
                        }
                        first.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { c ->
                                if (!c.pressed) {
                                    active.remove(c.id)?.let(up)
                                } else if (c.id !in active) { // un nuovo dito: accordi
                                    layout.keyAt(c.position.x, c.position.y)?.let {
                                        active[c.id] = it
                                        down(it)
                                    }
                                }
                                c.consume()
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        active.values.forEach(up) // anche se il gesto viene annullato: nessun tasto resta premuto
                    }
                }
            },
    ) {
        val geo = geometry.get(range, size.width, size.height, 3.dp.toPx(), 2.5.dp.toPx())
        val layout = geo.layout
        val h = size.height
        // Tastiera «a riposo» (tutti i tasti normali, con i loro nomi): si disegna una volta sola in un'immagine e a ogni
        // fotogramma si copia; sopra si ridisegnano solo i tasti evidenziati (attesi, premuti, in arrivo).
        drawImage(baseCache.get(this, geo, preferFlats, brushes, namePaint))
        // visibilità delle note in arrivo: a tempo segue l'orologio (cresce a ogni fotogramma), in «Impara» sono pesi fissi con dissolvenza
        val tNow = if (upcomingTimes.isNotEmpty()) timeProvider?.invoke() else null
        val fade = if (tNow == null) weightScale() else 1f
        fun wOf(n: Int): Float = if (tNow != null) upcomingTimes[n]?.let { com.gianlucacelante.midipiano.practice.upcomingWeightAt(it - tNow) } ?: 0f else (upcomingWeights[n] ?: 0.5f) * fade
        val keyState = KeyHighlight(pressed, wrong, expected, expectedHands, upcomingHands, studiedHand, ::wOf)
        val hot = HashSet<Int>()
        hot.addAll(expected); hot.addAll(pressed)
        for (n in upcomingHands.keys) if (wOf(n) > 0.01f) hot.add(n)
        val plan = KeyboardOverlayPlan.of(range, hot)
        val nativeCanvas = drawContext.canvas.nativeCanvas
        // tasti bianchi evidenziati, con il loro nome; quelli «in arrivo» sono semitrasparenti: sotto va prima il tasto a riposo
        for (n in plan.whites) {
            if (keyState.translucent(n)) drawWhiteKey(n, geo, null, brushes, h)
            drawWhiteKey(n, geo, keyState, brushes, h)
            drawWhiteName(n, layout, keyState, preferFlats, namePaint, h)
        }
        // le ombre dei neri sui bianchi ridisegnati (sugli altri sono già nell'immagine base: non vanno raddoppiate)
        for (n in plan.whites) {
            val wr = layout.rect(n)
            for (b in intArrayOf(n - 1, n + 1)) if (b in plan.blacks) clipRect(wr.left, 0f, wr.left + wr.width, h) { drawBlackShadow(b, layout) }
        }
        for (n in plan.blacks) {
            // un nero «in arrivo» è semitrasparente: sotto vanno i due bianchi che copre, a riposo
            if (keyState.translucent(n)) clipPath(geo.blackPath(n)) {
                // i bianchi sotto il nero col loro stato vero (a riposo solo se non evidenziati), poi l'ombra del nero
                for (w in intArrayOf(n - 1, n + 1)) if (w in range && !isBlackKey(w)) drawWhiteKey(w, geo, if (w in hot) keyState else null, brushes, h)
                drawBlackShadow(n, layout)
            }
            drawBlackKey(n, geo, keyState, brushes)
        }
        // Numero del dito (1 = pollice ... 5 = mignolo) sui tasti da suonare, nel colore della mano
        fingers.forEach { (n, f) ->
            if (n !in range) return@forEach
            val r = layout.rect(n)
            val black = isBlackKey(n)
            val radius = minOf(10.dp.toPx(), r.width * 0.46f)
            val cy = if (black) r.height - 42.dp.toPx() else h - 46.dp.toPx()
            val hd = expectedHands[n] ?: com.gianlucacelante.midipiano.song.Hand.RIGHT
            drawCircle(Color.White, radius + 1.5f, Offset(r.left + r.width / 2f, cy))
            drawCircle(HandColors.main(hd), radius, Offset(r.left + r.width / 2f, cy))
            namePaint.color = android.graphics.Color.WHITE
            namePaint.textSize = (radius * 1.35f)
            nativeCanvas.drawText(f.toString(), r.left + r.width / 2f, cy + radius * 0.48f, namePaint)
        }
        // Dito delle note in arrivo: anello vuoto nel colore della mano
        upcomingHands.forEach { (n, hd) ->
            if (n !in range) return@forEach
            val r = layout.rect(n)
            val vis = (wOf(n) / 0.62f).coerceIn(0f, 1f) // 1 = la nota sta per arrivare, 0 = è ancora lontana
            if (vis < 0.03f) return@forEach
            val radius = minOf(8.dp.toPx(), r.width * 0.42f) * (0.7f + 0.3f * vis)
            val cy = if (isBlackKey(n)) r.height - 42.dp.toPx() else h - 46.dp.toPx()
            val c = Offset(r.left + r.width / 2f, cy)
            drawCircle(Color.White.copy(alpha = 0.35f + 0.65f * vis), radius + 1f, c)
            drawCircle(HandColors.main(hd).copy(alpha = vis), radius, c, style = Stroke(width = 2.dp.toPx() * (0.6f + 0.4f * vis)))
            upcomingFingers[n]?.let { f ->
                namePaint.color = HandColors.label(hd); namePaint.textSize = radius * 1.25f
                namePaint.alpha = (255 * vis).toInt()
                nativeCanvas.drawText(f.toString(), c.x, c.y + radius * 0.45f, namePaint)
                namePaint.alpha = 255
            }
        }
        // Tasti neri attesi: il nome sul tasto, in bianco
        for (n in expected) if (n in range && isBlackKey(n)) {
            val r = layout.rect(n)
            namePaint.color = android.graphics.Color.WHITE
            val label = NoteNames.italian(n, preferFlats)
            namePaint.textSize = 10.sp.toPx()
            val wide = namePaint.measureText(label)
            if (wide > r.width * 0.92f) namePaint.textSize = (namePaint.textSize * r.width * 0.92f / wide).coerceAtLeast(6.sp.toPx()) // il nome sta dentro il tasto nero (sol♯, fa♯…), non esce
            nativeCanvas.drawText(label, r.left + r.width / 2f, r.height - 8.dp.toPx(), namePaint)
        }
        // Fascia scura in alto: il bordo del piano sopra i tasti
        drawKeyboardBand()
    }
}

/** I pennelli dei tasti «normali» (senza nulla di evidenziato) sono uguali a ogni disegno: si costruiscono una volta per altezza. */
private class PlainKeyBrushes {
    // pennelli degli stati evidenziati (premuto, atteso, errore, in arrivo a livelli): un gradiente nuovo a ogni tasto e fotogramma costava
    private val hot = HashMap<Long, Brush>()
    /** Un pennello per (stato, altezza): bianchi e neri hanno altezze diverse e non si pestano a vicenda la cache. */
    fun hot(key: Int, h: Float, build: () -> Brush): Brush {
        if (hot.size > 600) hot.clear() // altezze che cambiano di continuo (ridimensionamento): la cache non cresce all'infinito
        return hot.getOrPut((h.toRawBits().toLong() shl 32) or key.toLong(), build)
    }
    private var wh = Float.NaN
    private var white: Brush? = null
    private var bh = Float.NaN
    private var black: Brush? = null
    fun white(h: Float): Brush {
        val c = white
        if (c != null && wh == h) return c
        wh = h
        return Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F1F4), Color(0xFFDCDCE2)), startY = 0f, endY = h).also { white = it }
    }
    fun black(h: Float): Brush {
        val c = black
        if (c != null && bh == h) return c
        bh = h
        return Brush.verticalGradient(listOf(Color(0xFF3A3A3F), Color(0xFF151518), Color(0xFF050506)), startY = 0f, endY = h).also { black = it }
    }
}

/** Geometria della tastiera e percorsi dei tasti, ricalcolati solo se cambiano intervallo, dimensioni o scala (non a ogni disegno). */
private class KeyGeometry(val layout: KeyboardLayout, whiteCorner: Float, blackCorner: Float) {
    private val white = HashMap<Int, androidx.compose.ui.graphics.Path>()
    private val black = HashMap<Int, androidx.compose.ui.graphics.Path>()
    init {
        for (n in layout.whiteKeys) {
            val r = layout.rect(n)
            val c = androidx.compose.ui.geometry.CornerRadius(whiteCorner)
            white[n] = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left + 0.5f, -c.y, r.left + r.width - 0.5f, r.height - 0.5f, c, c))
            }
        }
        for (n in layout.blackKeys) {
            val r = layout.rect(n)
            val c = androidx.compose.ui.geometry.CornerRadius(blackCorner)
            black[n] = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left, -c.y, r.left + r.width, r.height, c, c))
            }
        }
    }
    fun whitePath(n: Int) = white.getValue(n)
    fun blackPath(n: Int) = black.getValue(n)
}

private class GeometryCache {
    private var first = Int.MIN_VALUE
    private var last = Int.MIN_VALUE
    private var w = Float.NaN
    private var h = Float.NaN
    private var corner = Float.NaN
    private var geo: KeyGeometry? = null
    fun get(range: IntRange, w: Float, h: Float, whiteCorner: Float, blackCorner: Float): KeyGeometry {
        val c = geo
        if (c != null && first == range.first && last == range.last && this.w == w && this.h == h && corner == whiteCorner) return c
        first = range.first; last = range.last; this.w = w; this.h = h; corner = whiteCorner
        return KeyGeometry(KeyboardLayout(range, w, h), whiteCorner, blackCorner).also { geo = it }
    }
}

/** Stato di evidenziazione dei tasti: da qui si ricavano colori e pennelli di un tasto. */
private class KeyHighlight(
    val pressed: Set<Int>, val wrong: Set<Int>, val expected: Set<Int>,
    val expectedHands: Map<Int, com.gianlucacelante.midipiano.song.Hand>,
    val upcomingHands: Map<Int, com.gianlucacelante.midipiano.song.Hand>,
    val studiedHand: com.gianlucacelante.midipiano.song.Hand?,
    val weightOf: (Int) -> Float,
) {
    fun isWrong(n: Int) = n in wrong && n in pressed
    fun handOf(n: Int) = pressedHand(n, expectedHands[n], studiedHand)
    fun on(n: Int) = n in expected || n in pressed
    /** Il tasto è solo «in arrivo» (tinta semitrasparente): non è premuto né atteso. */
    fun translucent(n: Int) = n !in pressed && n !in expected && n in upcomingHands && weightOf(n) > 0.01f
}

private fun DrawScope.drawWhiteKey(n: Int, geo: KeyGeometry, st: KeyHighlight?, brushes: PlainKeyBrushes, h: Float) {
    val key = geo.whitePath(n)
    val brush = when {
        st == null -> brushes.white(h)
        st.isWrong(n) -> brushes.hot(1, h) { Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935)), startY = 0f, endY = h) }
        n in st.pressed -> { val hd = st.handOf(n); brushes.hot(10 + hd.ordinal, h) { Brush.verticalGradient(listOf(HandColors.pressedTop(hd), HandColors.pressedBottom(hd)), startY = 0f, endY = h) } } // il colore della mano che preme, più scuro
        n in st.expected -> { val hd = st.handOf(n); brushes.hot(3 + hd.ordinal, h) { Brush.verticalGradient(listOf(HandColors.keyTop(hd), HandColors.keyBottom(hd)), startY = 0f, endY = h) } } // bagliore per mano, che sfuma verso il basso
        n in st.upcomingHands && st.weightOf(n) > 0.01f -> { val hd = st.upcomingHands[n]!!; val lv = BrushLevels.level(st.weightOf(n) / 0.62f); brushes.hot(100 + hd.ordinal * 20 + lv, h) { Brush.verticalGradient(listOf(HandColors.keyBottom(hd).copy(alpha = BrushLevels.alpha(lv)), Color(0xFFFFFFFF)), startY = 0f, endY = h) } } // in arrivo: tinta che cresce avvicinandosi
        else -> brushes.white(h)
    }
    drawPath(key, brush)
    drawPath(key, Color(0xFF9A9CA6), style = Stroke(width = 1f))
}

private fun DrawScope.drawBlackShadow(n: Int, layout: KeyboardLayout) {
    val r = layout.rect(n)
    drawRect(Color(0x33000000), Offset(r.left + 2f, 0f), Size(r.width, r.height + 5.dp.toPx()))
}

private fun DrawScope.drawBlackKey(n: Int, geo: KeyGeometry, st: KeyHighlight?, brushes: PlainKeyBrushes) {
    val r = geo.layout.rect(n)
    val key = geo.blackPath(n)
    val brush = when {
        st == null -> brushes.black(r.height)
        st.isWrong(n) -> brushes.hot(501, r.height) { Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFF8E0000)), startY = 0f, endY = r.height) }
        n in st.pressed -> { val hd = st.handOf(n); brushes.hot(510 + hd.ordinal, r.height) { Brush.verticalGradient(listOf(HandColors.pressedBlackTop(hd), HandColors.pressedBlackBottom(hd)), startY = 0f, endY = r.height) } }
        n in st.expected -> { val hd = st.handOf(n); brushes.hot(503 + hd.ordinal, r.height) { Brush.verticalGradient(listOf(HandColors.blackTop(hd), HandColors.blackBottom(hd)), startY = 0f, endY = r.height) } } // nero atteso: colore della mano
        n in st.upcomingHands && st.weightOf(n) > 0.01f -> { val hd = st.upcomingHands[n]!!; val lv = BrushLevels.level(st.weightOf(n) / 0.62f); val a = (BrushLevels.alpha(lv) * 0.55f).coerceIn(0f, 0.55f); brushes.hot(600 + hd.ordinal * 20 + lv, r.height) { Brush.verticalGradient(listOf(HandColors.blackTop(hd).copy(alpha = a), HandColors.blackBottom(hd).copy(alpha = a)), startY = 0f, endY = r.height) } }
        else -> brushes.black(r.height)
    }
    drawPath(key, brush)
    drawRect(Color(0x33FFFFFF), Offset(r.left + 1.5f, 0f), Size(r.width * 0.14f, r.height - 6.dp.toPx())) // riflesso
}

/** Nome della nota in fondo al tasto bianco (i Do con il numero dell'ottava: Do4 = Do centrale). */
private fun DrawScope.drawWhiteName(n: Int, layout: KeyboardLayout, st: KeyHighlight?, preferFlats: Boolean, namePaint: Paint, h: Float) {
    val r = layout.rect(n)
    val on = st != null && st.on(n)
    namePaint.textSize = (if (on) 15.sp else 12.sp).toPx().coerceAtMost(r.width * 0.62f)
    namePaint.color = when {
        st != null && (st.isWrong(n) || n in st.pressed) -> android.graphics.Color.WHITE // sul colore scuro del tasto premuto il nome è bianco
        on -> HandColors.label(st!!.handOf(n))
        n % 12 == 0 -> android.graphics.Color.rgb(40, 40, 50)
        else -> android.graphics.Color.rgb(110, 112, 124)
    }
    val nativeCanvas = drawContext.canvas.nativeCanvas
    nativeCanvas.drawText(NoteNames.italian(n, preferFlats), r.left + r.width / 2f, h - 6.dp.toPx(), namePaint)
    if (n % 12 == 0) { // numero d'ottava sotto il Do
        namePaint.textSize = 9.sp.toPx().coerceAtMost(r.width * 0.45f)
        nativeCanvas.drawText((n / 12 - 1).toString(), r.left + r.width / 2f, h - 6.dp.toPx() - 14.dp.toPx(), namePaint)
    }
}

private fun DrawScope.drawKeyboardBand() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0x00000000)), startY = 0f, endY = 9.dp.toPx()), Offset.Zero, Size(size.width, 9.dp.toPx()))
}

/** La tastiera senza nessun tasto evidenziato, disegnata una volta sola e rifatta solo se cambiano intervallo, dimensioni, nomi o scala. */
private class BaseKeyboardCache {
    private var key: List<Any>? = null
    private var image: androidx.compose.ui.graphics.ImageBitmap? = null
    fun get(scope: DrawScope, geo: KeyGeometry, preferFlats: Boolean, brushes: PlainKeyBrushes, namePaint: Paint): androidx.compose.ui.graphics.ImageBitmap {
        val k = listOf(geo.layout.range, scope.size, preferFlats, scope.density, scope.fontScale)
        val c = image
        if (c != null && key == k) return c
        val w = kotlin.math.ceil(scope.size.width).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(scope.size.height).toInt().coerceAtLeast(1)
        val bmp = androidx.compose.ui.graphics.ImageBitmap(w, h)
        val canvas = androidx.compose.ui.graphics.Canvas(bmp)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(scope, scope.layoutDirection, canvas, Size(w.toFloat(), h.toFloat())) {
            for (n in geo.layout.whiteKeys) drawWhiteKey(n, geo, null, brushes, scope.size.height)
            for (n in geo.layout.blackKeys) drawBlackShadow(n, geo.layout)
            for (n in geo.layout.blackKeys) drawBlackKey(n, geo, null, brushes)
            for (n in geo.layout.whiteKeys) drawWhiteName(n, geo.layout, null, preferFlats, namePaint, scope.size.height)
        }
        key = k
        image = bmp
        return bmp
    }
}

/** Tasti da ridisegnare sopra la tastiera «a riposo»: i bianchi evidenziati (in ordine) e i neri evidenziati o accanto a un bianco ridisegnato. */
internal class KeyboardOverlayPlan private constructor(val whites: List<Int>, val blacks: Set<Int>) {
    companion object {
        fun of(range: IntRange, hot: Collection<Int>): KeyboardOverlayPlan {
            val whites = hot.filter { it in range && !isBlackKey(it) }.sorted()
            val blacks = LinkedHashSet<Int>()
            for (n in hot) if (n in range && isBlackKey(n)) blacks.add(n)
            for (n in whites) { // un bianco ridisegnato ha coperto in parte i neri accanto
                if (n - 1 in range && isBlackKey(n - 1)) blacks.add(n - 1)
                if (n + 1 in range && isBlackKey(n + 1)) blacks.add(n + 1)
            }
            return KeyboardOverlayPlan(whites, blacks)
        }
    }
}

/** Livelli discreti di opacità (0..STEPS) per i pennelli dei tasti «in arrivo». */
internal object BrushLevels {
    const val STEPS = 16
    fun level(alpha: Float): Int = Math.round(alpha.coerceIn(0f, 1f) * STEPS)
    fun alpha(level: Int): Float = level.coerceIn(0, STEPS) / STEPS.toFloat()
}

/** Quale mano ha premuto un tasto: quella della nota attesa; altrimenti la mano studiata; con due mani decide l'altezza (sotto il Do centrale = sinistra). */
internal fun pressedHand(note: Int, expected: com.gianlucacelante.midipiano.song.Hand?, studied: com.gianlucacelante.midipiano.song.Hand?): com.gianlucacelante.midipiano.song.Hand {
    if (expected != null) return expected
    if (studied != null && studied != com.gianlucacelante.midipiano.song.Hand.BOTH) return studied
    return if (note < 60) com.gianlucacelante.midipiano.song.Hand.LEFT else com.gianlucacelante.midipiano.song.Hand.RIGHT
}
