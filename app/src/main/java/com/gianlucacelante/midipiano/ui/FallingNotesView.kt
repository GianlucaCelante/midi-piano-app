package com.gianlucacelante.midipiano.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.gianlucacelante.midipiano.PracticeSnapshot
import com.gianlucacelante.midipiano.practice.FallingNotes
import com.gianlucacelante.midipiano.song.Hand

private val RightHandColor = Color(0xFF42A5F5)
private val LeftHandColor = Color(0xFF66BB6A)
private const val HORIZON_SEC = 4.0

/**
 * Note che "cadono" verso la tastiera, allineate ai suoi tasti. In modalità attesa il tempo non
 * scorre da solo: le note si avvicinano solo quando suoni quelle giuste.
 */
@Composable
fun FallingNotesView(snapshot: PracticeSnapshot, modifier: Modifier = Modifier) {
    val time by animateFloatAsState(snapshot.currentTimeSec.toFloat(), tween(250), label = "time")

    Canvas(modifier = modifier) {
        val layout = KeyboardLayout(snapshot.keyRange, size.width, size.height)
        val pxPerSec = size.height / HORIZON_SEC.toFloat()
        FallingNotes.visible(snapshot.notes, time.toDouble(), HORIZON_SEC, snapshot.hand).forEach { n ->
            val r = layout.rectOrNull(n.key) ?: return@forEach
            val bottom = size.height - (n.offsetSec * pxPerSec).toFloat()
            val top = bottom - (n.durationSec * pxPerSec).toFloat().coerceAtLeast(8f)
            val y0 = top.coerceIn(0f, size.height)
            val y1 = bottom.coerceIn(0f, size.height)
            if (y1 <= y0) return@forEach
            val inset = if (r.isBlack) 0f else 2f
            drawRoundRect(
                color = if (n.hand == Hand.LEFT) LeftHandColor else RightHandColor,
                topLeft = Offset(r.left + inset, y0),
                size = Size((r.width - 2 * inset).coerceAtLeast(2f), y1 - y0),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
