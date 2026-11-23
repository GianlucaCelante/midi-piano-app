package com.gianlucacelante.midipiano.practice

/** Prova guidata della tastiera MIDI: ogni passo si spunta da solo appena la tastiera fa la cosa richiesta. */
class KeyboardCheck {
    enum class Step(val title: String, val hint: String) {
        CONNECTED("Tastiera collegata", "Collega la tastiera con USB o Bluetooth."),
        NOTES("Note", "Premi cinque tasti diversi, uno alla volta."),
        SOFT("Tasto piano", "Premi un tasto con delicatezza."),
        LOUD("Tasto forte", "Premi un tasto con decisione."),
        CHORD("Più tasti insieme", "Premi tre tasti contemporaneamente e tienili."),
        PEDAL_DOWN("Pedale giù", "Premi il pedale di risonanza."),
        PEDAL_UP("Pedale su", "Rilascia il pedale."),
    }

    data class State(
        val done: Set<Step> = emptySet(),
        val lastVelocity: Int? = null,
        val lowest: Int? = null,
        val highest: Int? = null,
        val distinctNotes: Int = 0,
    ) {
        val next: Step? get() = Step.entries.firstOrNull { it !in done }
        val allDone: Boolean get() = next == null
        val rangeText: String? get() = if (lowest != null && highest != null) "Dal ${lowest} al ${highest} (nota MIDI)" else null
    }

    var state = State()
        private set
    private val held = HashSet<Int>()
    private val seen = HashSet<Int>()
    private var pedalWasDown = false

    fun reset(connected: Boolean) { state = State(done = if (connected) setOf(Step.CONNECTED) else emptySet()); held.clear(); seen.clear(); pedalWasDown = false }

    fun onConnection(connected: Boolean) { state = if (connected) state.copy(done = state.done + Step.CONNECTED) else state.copy(done = state.done - Step.CONNECTED) }

    fun onNoteOn(note: Int, velocity: Int) {
        held += note
        seen += note
        var done = state.done + Step.CONNECTED // se arriva una nota, la tastiera c'è
        if (seen.size >= 5) done += Step.NOTES
        if (velocity in 1..45) done += Step.SOFT
        if (velocity >= 100) done += Step.LOUD
        if (held.size >= 3) done += Step.CHORD
        state = state.copy(done = done, lastVelocity = velocity, distinctNotes = seen.size, lowest = minOf(state.lowest ?: note, note), highest = maxOf(state.highest ?: note, note))
    }

    fun onNoteOff(note: Int) { held -= note }

    fun onPedal(value: Int) {
        val down = value >= 64
        var done = state.done + Step.CONNECTED
        if (down) { done += Step.PEDAL_DOWN; pedalWasDown = true }
        else if (pedalWasDown) done += Step.PEDAL_UP
        state = state.copy(done = done)
    }
}
