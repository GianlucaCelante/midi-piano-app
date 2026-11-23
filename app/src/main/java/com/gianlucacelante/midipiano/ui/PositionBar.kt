package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Barra di posizione del brano: traccia chiara con un cursore tondo. Il valore si legge solo in fase
 * di disegno ([progressProvider]), quindi muoverla a ogni fotogramma non ricompone la schermata.
 * Trascinando, [onScrub] riceve a ogni movimento la frazione 0..1 (lo spartito segue il dito in tempo reale);
 * al rilascio [onSeek] porta il brano a quel punto.
 */
@Composable
fun PositionBar(
    progressProvider: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit = {},
    onScrub: (Float) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    dragging = (down.position.x / w).coerceIn(0f, 1f)
                    scrubStart()
                    dragging?.let(scrub)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull() ?: break
                        dragging = (c.position.x / w).coerceIn(0f, 1f)
                        dragging?.let(scrub) // lo spartito segue il dito in tempo reale
                        c.consume()
                        if (!c.pressed) break
                    }
                    dragging?.let(seek)
                    dragging = null
                }
            },
    ) {
        val pad = 16.dp.toPx()
        val y = size.height / 2f
        val width = size.width - 2 * pad
        val p = (dragging ?: progressProvider()).coerceIn(0f, 1f)
        val x = pad + width * p
        drawLine(Color(0x66FFFFFF), Offset(pad, y), Offset(pad + width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(pad, y), Offset(x, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, y))
    }
}
