package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gianlucacelante.midipiano.song.Hand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Quadrante della velocità: l'ago va da -70° (50 %) a +70° (100 %). */
object SpeedDial {
    fun needleAngleDegrees(speed: Double): Float = (-70.0 + (speed.coerceIn(0.5, 1.0) - 0.5) / 0.5 * 140.0).toFloat()
}

/** Icona di una mano disegnata a mano (palmo e cinque dita); [left] la specchia. */
@Composable
fun HandIcon(left: Boolean, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) { drawHand(left, color) }
}

/** Icona per "entrambe le mani": due mani affiancate. */
@Composable
fun HandsIcon(color: Color) {
    Row {
        HandIcon(left = true, color = color, modifier = Modifier.size(18.dp))
        HandIcon(left = false, color = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun HandIconFor(hand: Hand, color: Color) {
    when (hand) {
        Hand.LEFT -> HandIcon(true, color)
        Hand.RIGHT -> HandIcon(false, color)
        Hand.BOTH -> HandsIcon(color)
    }
}

private fun DrawScope.drawHand(left: Boolean, color: Color) {
    // Mano vista dall'alto sui tasti: il pollice sta verso il centro (a sinistra per la mano destra, a destra per la sinistra)
    val w = size.width
    val h = size.height
    fun x(f: Float) = if (left) w * (1f - f) else w * f
    val fw = w * 0.125f
    // polso e palmo
    drawRoundRect(color, Offset(minOf(x(0.24f), x(0.84f)), h * 0.5f), Size(w * 0.60f, h * 0.46f), CornerRadius(w * 0.2f))
    // indice, medio, anulare, mignolo (altezze diverse, leggermente aperti)
    val tops = floatArrayOf(0.17f, 0.08f, 0.16f, 0.30f)
    val xs = floatArrayOf(0.33f, 0.48f, 0.63f, 0.77f)
    for (i in 0 until 4) {
        val bx = x(xs[i])
        val tx = x(xs[i] + (i - 1.5f) * 0.012f)
        drawLine(color, Offset(bx, h * 0.6f), Offset(tx, h * tops[i]), strokeWidth = fw, cap = StrokeCap.Round)
    }
    // pollice: più corto e inclinato verso l'esterno
    drawLine(color, Offset(x(0.27f), h * 0.76f), Offset(x(0.07f), h * 0.47f), strokeWidth = fw * 1.15f, cap = StrokeCap.Round)
}

/** Piccolo quadrante con l'ago alla posizione della velocità. */
@Composable
fun SpeedDialIcon(speed: Double, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val r = size.width * 0.46f
        val c = Offset(size.width / 2f, size.height * 0.78f)
        drawArc(color.copy(alpha = 0.5f), 200f, 140f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(width = size.width * 0.1f, cap = StrokeCap.Round))
        val a = (needleRad(speed))
        drawLine(color, c, Offset(c.x + (r * 0.85f) * sin(a), c.y - (r * 0.85f) * cos(a)), strokeWidth = size.width * 0.12f, cap = StrokeCap.Round)
        drawCircle(color, size.width * 0.07f, c)
    }
}

private fun needleRad(speed: Double): Float = (SpeedDial.needleAngleDegrees(speed) * PI / 180.0).toFloat()
