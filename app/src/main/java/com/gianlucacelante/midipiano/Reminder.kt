package com.gianlucacelante.midipiano

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gianlucacelante.midipiano.learn.ProgressStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Promemoria giornaliero: una notifica all'ora scelta, solo se oggi non hai ancora suonato. */
object Reminder {
    const val CHANNEL = "reminder"
    private const val REQUEST = 4711

    /** Il prossimo momento in cui suona l'ora [hour]: oggi se manca ancora, altrimenti domani. */
    fun nextTriggerMillis(nowMillis: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        var t = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(zone).toInstant().toEpochMilli()
    }

    fun text(streak: Int, practicedToday: Boolean): String? = when {
        practicedToday -> null
        streak >= 2 -> "Hai una serie di $streak giorni: oggi bastano pochi minuti per non perderla."
        else -> "È il momento di suonare: le sfide di oggi ti aspettano."
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, REQUEST, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programma (o toglie, con [hour] negativa) la ripetizione giornaliera. Non servono permessi per gli allarmi precisi: l'ora è approssimata dal sistema. */
    fun apply(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        if (hour < 0) return
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextTriggerMillis(System.currentTimeMillis(), hour), AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context) {
        if (!canNotify(ctx)) return
        val progress = ProgressStore(ctx).load()
        val today = LocalDate.now()
        val msg = text(progress.streak(today), progress.secondsOn(today) > 0 || progress.practicedOn(today)) ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria di pratica", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify).setContentTitle("Ora di suonare").setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg)).setContentIntent(open).setAutoCancel(true).build()
        nm.notify(REQUEST, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.show(context)
    }
}

/** Dopo un riavvio gli allarmi si perdono: si riprogrammano dalle impostazioni. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminder.apply(context, AppSettings(context).load().reminderHour)
    }
}
