package com.gianlucacelante.midipiano

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Unisce i file di registro in uno solo e lo manda alla finestra di condivisione di Android. */
fun shareLog(context: Context) {
    val files = AppLog.files()
    if (files.isEmpty()) return
    val out = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, "midi-piano-log.txt") }
    out.writeText(files.joinToString("\n") { it.readText() })
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", out)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Condividi il registro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
