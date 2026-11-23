package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Step

/** Esito di una nota ricevuta durante la modalità attesa. */
enum class NoteResult { CORRECT, WRONG, STEP_COMPLETED, FINISHED, IGNORED }

/**
 * Modalità attesa: il brano non avanza finché non sono state suonate tutte le note del passo
 * corrente. L'ordine delle note dentro un accordo non conta. Una nota non attesa è un errore
 * (conteggiato, senza far retrocedere). Logica pura: nessuna dipendenza da Android.
 */
class WaitModeEngine(private val steps: List<Step>) {
    var index: Int = 0
        private set
    var mistakes: Int = 0
        private set
    private var lastWrongNanos = Long.MIN_VALUE
    private val remaining = HashSet<Int>()
    private val pressedAt = HashMap<Int, Long>() // tasto del passo -> istante (ns) dell'ultima pressione valida

    init {
        loadStep()
    }

    val isFinished: Boolean get() = index >= steps.size
    val currentStep: Step? get() = steps.getOrNull(index)

    /** Tasti ancora da premere per completare il passo corrente. */
    val expectedKeys: Set<Int> get() = remaining.toSet()

    /**
     * Un accordo va premuto "insieme": tutte le sue note entro [CHORD_WINDOW_NS] l'una dall'altra. Le note
     * premute una alla volta, a distanza, non fanno avanzare (le più vecchie scadono).
     */
    fun onNoteOn(key: Int, nowNanos: Long = 0L): NoteResult {
        if (isFinished) return NoteResult.IGNORED
        val step = currentStep ?: return NoteResult.IGNORED
        if (key !in step.keys) {
            countMistake(nowNanos)
            return NoteResult.WRONG
        }
        expire(nowNanos)
        if (key in pressedAt) { // già premuta (e ancora valida): non conta due volte
            countMistake(nowNanos)
            return NoteResult.WRONG
        }
        pressedAt[key] = nowNanos
        lastWrongNanos = Long.MIN_VALUE // un passo giusto chiude il gesto sbagliato precedente
        remaining.clear()
        remaining.addAll(step.keys.filter { it !in pressedAt })
        if (remaining.isNotEmpty()) return NoteResult.CORRECT
        index++
        loadStep()
        return if (isFinished) NoteResult.FINISHED else NoteResult.STEP_COMPLETED
    }

    /** Più tasti sbagliati nello stesso gesto (un accordo con tasti vicini di troppo) sono un solo errore. */
    private fun countMistake(nowNanos: Long) {
        val folded = lastWrongNanos != Long.MIN_VALUE && nowNanos - lastWrongNanos <= WRONG_CLUSTER_NS
        if (!folded) { lastWrongNanos = nowNanos; mistakes++ } // la finestra resta ancorata al primo tasto del gesto
    }

    /** Dimentica le note del passo premute da più di [CHORD_WINDOW_NS]. */
    fun expire(nowNanos: Long) {
        if (pressedAt.isEmpty()) return
        if (pressedAt.entries.removeAll { nowNanos - it.value > CHORD_WINDOW_NS }) {
            remaining.clear()
            currentStep?.let { st -> remaining.addAll(st.keys.filter { it !in pressedAt }) }
        }
    }

    fun restart(from: Int = 0) {
        index = from.coerceIn(0, steps.size)
        mistakes = 0
        lastWrongNanos = Long.MIN_VALUE
        loadStep()
    }

    companion object {
        const val CHORD_WINDOW_NS = 350_000_000L
        const val WRONG_CLUSTER_NS = 150_000_000L
    }

    private fun loadStep() {
        pressedAt.clear()
        remaining.clear()
        currentStep?.let { remaining.addAll(it.keys) }
    }
}
