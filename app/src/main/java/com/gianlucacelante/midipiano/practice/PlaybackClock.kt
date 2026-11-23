package com.gianlucacelante.midipiano.practice

/** Esito di un aggiornamento dell'orologio. */
data class ClockTick(val songTimeSec: Double, val looped: Boolean, val finished: Boolean)

/**
 * Orologio di riproduzione: converte il tempo reale (nanosecondi monotoni, passati da fuori per
 * poterlo testare) in tempo del brano, tenendo conto di velocità, loop e fine brano. Il tempo
 * può partire da un valore negativo (conto alla rovescia).
 */
class PlaybackClock(var durationSec: Double) {
    var running: Boolean = false
        private set
    var speed: Double = 1.0
        private set
    private var baseNanos = 0L
    private var baseSong = 0.0
    private var loopStart: Double? = null
    private var loopEnd: Double? = null

    val loop: ClosedFloatingPointRange<Double>?
        get() = if (loopStart != null && loopEnd != null) loopStart!!..loopEnd!! else null

    fun start(nowNanos: Long) {
        if (running) return
        baseNanos = nowNanos
        running = true
    }

    fun pause(nowNanos: Long) {
        if (!running) return
        baseSong = current(nowNanos)
        running = false
    }

    fun seek(songTimeSec: Double, nowNanos: Long) {
        baseSong = songTimeSec
        baseNanos = nowNanos
    }

    fun setSpeed(newSpeed: Double, nowNanos: Long) {
        require(newSpeed > 0.0) { "La velocità deve essere positiva" }
        rebase(nowNanos)
        speed = newSpeed
    }

    fun setLoop(start: Double?, end: Double?) {
        if (start != null && end != null && end > start) {
            loopStart = start
            loopEnd = end
        } else {
            loopStart = null
            loopEnd = null
        }
    }

    /** Tempo del brano all'istante [nowNanos], senza modificare nulla (niente loop, niente fine brano). */
    fun peek(nowNanos: Long): Double = current(nowNanos)

    /** Aggiorna l'orologio e restituisce il tempo corrente del brano. */
    fun tick(nowNanos: Long): ClockTick {
        if (!running) return ClockTick(baseSong, looped = false, finished = false)
        var t = current(nowNanos)
        var looped = false
        val ls = loopStart
        val le = loopEnd
        if (ls != null && le != null && t >= le) {
            val len = le - ls
            t = ls + (t - le) % len
            looped = true
            baseSong = t
            baseNanos = nowNanos
        } else if (t >= durationSec) {
            baseSong = durationSec
            running = false
            return ClockTick(durationSec, looped = false, finished = true)
        }
        return ClockTick(t, looped, finished = false)
    }

    private fun current(nowNanos: Long): Double =
        if (running) baseSong + (nowNanos - baseNanos) / 1e9 * speed else baseSong

    private fun rebase(nowNanos: Long) {
        if (running) {
            baseSong = current(nowNanos)
            baseNanos = nowNanos
        }
    }
}
