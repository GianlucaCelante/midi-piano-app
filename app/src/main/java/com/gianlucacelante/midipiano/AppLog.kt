package com.gianlucacelante.midipiano

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registro d'uso dell'app, per capire cosa è successo durante i test: dispositivi MIDI, note ricevute con il loro
 * esito, azioni nel player, errori e crash. Scrive su file (rotazione a 2 × 1 MB) in
 * Android/data/<pacchetto>/files/logs/ (prelevabile via USB) e su logcat con tag "AppLog". Nessun dato esce dal tablet.
 */
object AppLog {
    private const val MAX_BYTES = 1_000_000L
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "AppLog").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    @Volatile private var dir: File? = null
    private var start = 0L

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
        dir = d
        start = System.currentTimeMillis()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("CRASH", "thread=${t.name} ${Log.getStackTraceString(e)}", sync = true)
            previous?.uncaughtException(t, e)
        }
        write("SESSIONE", "avvio app v${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()} " +
            "${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    fun logFile(): File? = dir?.let { File(it, "midi-piano.log") }

    /** Tutti i file di log, dal più vecchio al più recente. */
    fun files(): List<File> = dir?.listFiles { f -> f.name.startsWith("midi-piano") }?.sortedBy { it.lastModified() }.orEmpty()

    fun i(tag: String, msg: String) = write(tag, msg)
    fun w(tag: String, msg: String) = write("$tag!", msg)

    private fun write(tag: String, msg: String, sync: Boolean = false) {
        Log.d("AppLog", "$tag $msg")
        val d = dir ?: return
        val line = "${stamp.format(Date())} +${(System.currentTimeMillis() - start) / 1000}s [$tag] $msg\n"
        val job = Runnable {
            runCatching {
                val f = File(d, "midi-piano.log")
                if (f.length() > MAX_BYTES) {
                    val old = File(d, "midi-piano.1.log")
                    old.delete(); f.renameTo(old)
                }
                f.appendText(line)
            }
        }
        if (sync) job.run() else executor.execute(job)
    }
}
