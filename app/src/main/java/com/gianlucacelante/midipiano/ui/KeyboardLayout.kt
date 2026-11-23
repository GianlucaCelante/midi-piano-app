package com.gianlucacelante.midipiano.ui

/** Rettangolo di un tasto in pixel, con l'origine in alto a sinistra della tastiera. */
data class KeyRect(val left: Float, val top: Float, val width: Float, val height: Float, val isBlack: Boolean)

/**
 * Geometria di una tastiera per l'intervallo di note MIDI [range]: serve sia a disegnarla sia ad
 * allineare le note in caduta e a capire quale tasto è stato toccato. Pura, senza dipendenze UI.
 */
class KeyboardLayout(val range: IntRange, val widthPx: Float, val heightPx: Float) {
    /** Tasti bianchi e neri dell'intervallo, già divisi (il disegno li scorre molte volte per fotogramma). */
    val whiteKeys: List<Int> = range.filterNot(::isBlackKey)
    val blackKeys: List<Int> = range.filter(::isBlackKey)
    val whiteKeyCount: Int = whiteKeys.size
    val whiteKeyWidth: Float = if (whiteKeyCount == 0) 0f else widthPx / whiteKeyCount
    private val blackWidth = whiteKeyWidth * 0.6f
    private val blackHeight = heightPx * 0.62f

    // un rettangolo per nota, calcolato una volta sola: prima ogni richiesta rifaceva un ciclo e un oggetto nuovo
    private val rects: Array<KeyRect?> = run {
        val out = arrayOfNulls<KeyRect>(range.last - range.first + 1)
        var whiteIdx = 0
        for (n in range) {
            out[n - range.first] = if (isBlackKey(n)) {
                // Il nero sta sul confine destro del bianco che lo precede
                KeyRect(whiteIdx * whiteKeyWidth - blackWidth / 2, 0f, blackWidth, blackHeight, true)
            } else {
                KeyRect(whiteIdx++ * whiteKeyWidth, 0f, whiteKeyWidth, heightPx, false)
            }
        }
        out
    }

    fun rectOrNull(note: Int): KeyRect? = if (note !in range) null else rects[note - range.first]

    fun rect(note: Int): KeyRect = rectOrNull(note) ?: throw IllegalArgumentException("Nota fuori intervallo: $note")

    /** Tasto sotto il punto (x, y), con i neri che hanno la precedenza; null se fuori dalla tastiera. */
    fun keyAt(x: Float, y: Float): Int? {
        if (x < 0f || x > widthPx || y < 0f || y > heightPx) return null
        for (n in range) {
            val r = rectOrNull(n) ?: continue
            if (r.isBlack && x >= r.left && x <= r.left + r.width && y <= r.height) return n
        }
        for (n in range) {
            val r = rectOrNull(n) ?: continue
            if (!r.isBlack && x >= r.left && x < r.left + r.width) return n
        }
        // bordo destro esatto
        return range.lastOrNull { !isBlackKey(it) }?.takeIf { x == widthPx }
    }
}
