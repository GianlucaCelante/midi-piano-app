package com.gianlucacelante.midipiano.ui

/**
 * Geometria dei righi e delle linee supplementari «fantasma», indipendente da quale tratto si disegna: si costruisce
 * una volta per larghezza e scala e poi si ritaglia. (Prima dipendeva dall'intervallo disegnato, che cambia mentre lo
 * spartito scorre, e si ricostruiva centinaia di rettangoli a ogni fotogramma.) Logica pura: rettangoli come
 * sequenze di (sinistra, alto, destra, basso).
 */
internal object StaffGeometry {
    const val LINE_THICKNESS = 1.5f

    fun key(s: Float, trebleBottom: Float, bassBottom: Float, width: Float): List<Float> = listOf(s, trebleBottom, bassBottom, width)

    /** Le 10 linee dei due righi, a tutta larghezza. */
    fun staffLines(s: Float, trebleBottom: Float, bassBottom: Float, width: Float): FloatArray {
        val out = FloatArray(10 * 4)
        var i = 0
        for (bottom in floatArrayOf(trebleBottom, bassBottom)) for (line in 0..4) {
            val y = bottom - line * s
            out[i++] = 0f; out[i++] = y - LINE_THICKNESS / 2; out[i++] = width; out[i++] = y + LINE_THICKNESS / 2
        }
        return out
    }

    /** Tratti delle linee supplementari tratteggiate: tre sopra e tre sotto ogni rigo, ancorati a x = 0. */
    fun ghostDashes(s: Float, trebleBottom: Float, bassBottom: Float, width: Float): FloatArray {
        val perLine = Math.ceil((width / s).toDouble()).toInt().coerceAtLeast(0)
        val out = FloatArray(12 * perLine * 4)
        var i = 0
        for (k in 1..3) for (bottom in floatArrayOf(trebleBottom, bassBottom)) for (y in floatArrayOf(bottom + k * s, bottom - (4 + k) * s)) {
            for (d in 0 until perLine) {
                val x = d * s
                out[i++] = x; out[i++] = y - 0.5f; out[i++] = minOf(x + 0.45f * s, width); out[i++] = y + 0.5f
            }
        }
        return out
    }
}
