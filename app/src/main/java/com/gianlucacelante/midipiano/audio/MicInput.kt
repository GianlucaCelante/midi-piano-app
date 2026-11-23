package com.gianlucacelante.midipiano.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Ingresso dal microfono per un pianoforte acustico: legge l'audio, riconosce l'altezza (YIN) e
 * produce note premute/rilasciate come una tastiera MIDI. Una nota alla volta. Richiede il permesso
 * del microfono; i callback arrivano dal thread di lettura.
 */
class MicInput(
    private val context: Context,
    private val onNoteOn: (midi: Int, velocity: Int) -> Unit,
    private val onNoteOff: (midi: Int) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start() {
        if (running || !hasPermission()) return
        running = true
        thread = Thread({ loop() }, "mic-input").also { it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
    }

    @Suppress("MissingPermission") // controllato da hasPermission()
    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val rate = SAMPLE_RATE
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes, WINDOW * 4))
        } catch (e: Exception) {
            Log.e(TAG, "Microfono non disponibile", e)
            running = false
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Microfono non inizializzato")
            record.release()
            running = false
            return
        }
        val tracker = NoteTracker()
        val hop = ShortArray(HOP)
        val window = FloatArray(WINDOW)
        try {
            record.startRecording()
            while (running) {
                val n = record.read(hop, 0, HOP)
                if (n <= 0) continue
                // finestra scorrevole: si fanno scorrere i vecchi campioni e si aggiungono i nuovi
                System.arraycopy(window, n, window, 0, WINDOW - n)
                for (i in 0 until n) window[WINDOW - n + i] = hop[i] / 32768f
                val rms = PitchDetector.rms(window.copyOfRange(WINDOW - HOP * 2, WINDOW))
                val pitch = if (rms >= 0.01) PitchDetector.detect(window, rate) else null
                for (e in tracker.onFrame(rms, pitch)) {
                    when (e) {
                        is MicEvent.On -> onNoteOn(e.midi, e.velocity)
                        is MicEvent.Off -> onNoteOff(e.midi)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore del microfono", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            running = false
        }
    }

    private companion object {
        const val TAG = "MicInput"
        const val SAMPLE_RATE = 44100
        const val WINDOW = 4096 // finestra di analisi (≈ 93 ms): arriva fino ai Do gravi
        const val HOP = 1024 // un'analisi ogni ≈ 23 ms
    }
}
