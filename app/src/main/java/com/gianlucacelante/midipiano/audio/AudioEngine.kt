package com.gianlucacelante.midipiano.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Uscita audio a bassa latenza: un thread dedicato riempie un AudioTrack con i campioni del
 * [PianoSynth]. I comandi (nota, rilascio, tic) arrivano da qualsiasi thread tramite una coda e si
 * applicano all'inizio del blocco successivo (pochi millisecondi).
 */
class AudioEngine(context: Context) {
    private sealed interface Command {
        data class NoteOn(val key: Int, val velocity: Int) : Command
        data class NoteOff(val key: Int) : Command
        data class Click(val accent: Boolean) : Command
        data class Sustain(val on: Boolean) : Command
        data object AllOff : Command
    }

    private val queue = ConcurrentLinkedQueue<Command>()
    private val sampleRate: Int
    private val framesPerBuffer: Int

    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        framesPerBuffer = (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256).coerceIn(64, 1024)
    }

    val isRunning: Boolean get() = running

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "piano-audio").also { it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
        queue.clear()
    }

    fun noteOn(key: Int, velocity: Int) {
        if (running) queue.add(Command.NoteOn(key, velocity))
    }

    fun noteOff(key: Int) {
        if (running) queue.add(Command.NoteOff(key))
    }

    fun click(accent: Boolean) {
        if (running) queue.add(Command.Click(accent))
    }

    fun sustain(on: Boolean) {
        if (running) queue.add(Command.Sustain(on))
    }

    fun allNotesOff() {
        if (running) queue.add(Command.AllOff)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val synth = PianoSynth(sampleRate)
        val buffer = FloatArray(framesPerBuffer)
        var failures = 0
        while (running && failures < MAX_FAILURES) {
            val track = try {
                buildTrack()
            } catch (e: Exception) {
                Log.e(TAG, "Impossibile aprire l'uscita audio", e)
                failures++
                sleepQuietly(300)
                continue
            }
            try {
                track.play()
                while (running) {
                    while (true) {
                        when (val c = queue.poll() ?: break) {
                            is Command.NoteOn -> synth.noteOn(c.key, c.velocity)
                            is Command.NoteOff -> synth.noteOff(c.key)
                            is Command.Click -> synth.click(c.accent)
                            is Command.Sustain -> synth.setSustain(c.on)
                            Command.AllOff -> synth.allNotesOff()
                        }
                    }
                    buffer.fill(0f)
                    synth.render(buffer, framesPerBuffer)
                    if (track.write(buffer, 0, framesPerBuffer, AudioTrack.WRITE_BLOCKING) < 0) {
                        Log.w(TAG, "Uscita audio interrotta (cuffie scollegate?): la ricreo")
                        failures++
                        break // si esce dal ciclo di scrittura e si ricrea la traccia
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore audio", e)
                failures++
            } finally {
                runCatching { track.stop() }
                track.release()
            }
            if (running && failures > 0) sleepQuietly(200)
        }
        running = false // se si esce per troppi errori, start() potrà ripartire
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun buildTrack(): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBytes, framesPerBuffer * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private companion object {
        const val TAG = "AudioEngine"
        const val MAX_FAILURES = 20
    }
}
