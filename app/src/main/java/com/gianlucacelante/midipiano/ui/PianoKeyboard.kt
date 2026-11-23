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
) {
    KeyboardCanvas(pressed, modifier, range, expected, height, onKeyDown, onKeyUp, wrong, preferFlats, expectedHands, fingers, upcomingHands, upcomingFingers)
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
) {
    val down by rememberUpdatedState(onKeyDown)
    val namePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) } }
    val up by rememberUpdatedState(onKeyUp)

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
        val layout = KeyboardLayout(range, size.width, size.height)
        val h = size.height
        // Tasti bianchi: sfumatura d'avorio, bordo fine, bordo inferiore leggermente più scuro, angoli bassi arrotondati
        range.filterNot(::isBlackKey).forEach { n ->
            val r = layout.rect(n)
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            val key = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left + 0.5f, -corner.y, r.left + r.width - 0.5f, r.height - 0.5f, corner, corner))
            }
            val isWrong = n in wrong && n in pressed
            val brush = when {
                isWrong -> Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935)), startY = 0f, endY = h)
                n in pressed -> Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFF0288D1)), startY = 0f, endY = h)
                n in expected -> { val hd = expectedHands[n] ?: com.gianlucacelante.midipiano.song.Hand.RIGHT; Brush.verticalGradient(listOf(HandColors.keyTop(hd), HandColors.keyBottom(hd)), startY = 0f, endY = h) } // bagliore per mano, che sfuma verso il basso
                n in upcomingHands -> { val hd = upcomingHands[n]!!; Brush.verticalGradient(listOf(HandColors.keyBottom(hd), Color(0xFFFFFFFF)), startY = 0f, endY = h) } // in arrivo: tinta leggera
                else -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F1F4), Color(0xFFDCDCE2)), startY = 0f, endY = h)
            }
            drawPath(key, brush)
            drawPath(key, Color(0xFF9A9CA6), style = Stroke(width = 1f))
        }
        // Ombra dei tasti neri sui bianchi
        range.filter(::isBlackKey).forEach { n ->
            val r = layout.rect(n)
            drawRect(Color(0x33000000), Offset(r.left + 2f, 0f), Size(r.width, r.height + 5.dp.toPx()))
        }
        // Tasti neri: lucidi, con riflesso sul bordo sinistro e angoli bassi arrotondati
        range.filter(::isBlackKey).forEach { n ->
            val r = layout.rect(n)
            val corner = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
            val key = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(r.left, -corner.y, r.left + r.width, r.height, corner, corner))
            }
            val brush = when {
                n in wrong && n in pressed -> Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFF8E0000)), startY = 0f, endY = r.height)
                n in pressed -> Brush.verticalGradient(listOf(Color(0xFF29B6F6), Color(0xFF01579B)), startY = 0f, endY = r.height)
                n in expected -> { val hd = expectedHands[n] ?: com.gianlucacelante.midipiano.song.Hand.RIGHT; Brush.verticalGradient(listOf(HandColors.blackTop(hd), HandColors.blackBottom(hd)), startY = 0f, endY = r.height) } // nero atteso: colore della mano
                n in upcomingHands -> { val hd = upcomingHands[n]!!; Brush.verticalGradient(listOf(HandColors.blackTop(hd).copy(alpha = 0.55f), HandColors.blackBottom(hd).copy(alpha = 0.55f)), startY = 0f, endY = r.height) }
                else -> Brush.verticalGradient(listOf(Color(0xFF3A3A3F), Color(0xFF151518), Color(0xFF050506)), startY = 0f, endY = r.height)
            }
            drawPath(key, brush)
            drawRect(Color(0x33FFFFFF), Offset(r.left + 1.5f, 0f), Size(r.width * 0.14f, r.height - 6.dp.toPx())) // riflesso
        }
        // Nomi delle note sempre visibili in fondo ai tasti (i Do con il numero dell'ottava: Do4 = Do centrale)
        val nativeCanvas = drawContext.canvas.nativeCanvas
        range.filterNot(::isBlackKey).forEach { n ->
            val r = layout.rect(n)
            val on = n in expected || n in pressed
            namePaint.textSize = (if (on) 15.sp else 12.sp).toPx().coerceAtMost(r.width * 0.62f)
            namePaint.color = when {
                n in wrong && n in pressed -> android.graphics.Color.WHITE
                on -> HandColors.label(expectedHands[n] ?: com.gianlucacelante.midipiano.song.Hand.RIGHT)
                n % 12 == 0 -> android.graphics.Color.rgb(40, 40, 50)
                else -> android.graphics.Color.rgb(110, 112, 124)
            }
            val label = NoteNames.italian(n, preferFlats)
            nativeCanvas.drawText(label, r.left + r.width / 2f, h - 6.dp.toPx(), namePaint)
            if (n % 12 == 0) { // numero d'ottava sotto il Do
                namePaint.textSize = 9.sp.toPx().coerceAtMost(r.width * 0.45f)
                nativeCanvas.drawText((n / 12 - 1).toString(), r.left + r.width / 2f, h - 6.dp.toPx() - 14.dp.toPx(), namePaint)
            }
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
            val radius = minOf(8.dp.toPx(), r.width * 0.42f)
            val cy = if (isBlackKey(n)) r.height - 42.dp.toPx() else h - 46.dp.toPx()
            val c = Offset(r.left + r.width / 2f, cy)
            drawCircle(Color.White, radius + 1f, c)
            drawCircle(HandColors.main(hd), radius, c, style = Stroke(width = 2.dp.toPx()))
            upcomingFingers[n]?.let { f ->
                namePaint.color = HandColors.label(hd); namePaint.textSize = radius * 1.25f
                nativeCanvas.drawText(f.toString(), c.x, c.y + radius * 0.45f, namePaint)
            }
        }
        // Tasti neri attesi: il nome sul tasto, in bianco
        range.filter { isBlackKey(it) && it in expected }.forEach { n ->
            val r = layout.rect(n)
            namePaint.color = android.graphics.Color.WHITE
            namePaint.textSize = 10.sp.toPx().coerceAtMost(r.width * 0.9f)
            nativeCanvas.drawText(NoteNames.italian(n, preferFlats), r.left + r.width / 2f, r.height - 8.dp.toPx(), namePaint)
        }
        // Fascia scura in alto: il bordo del piano sopra i tasti
        drawRect(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0x00000000)), startY = 0f, endY = 9.dp.toPx()), Offset.Zero, Size(size.width, 9.dp.toPx()))
    }
}
