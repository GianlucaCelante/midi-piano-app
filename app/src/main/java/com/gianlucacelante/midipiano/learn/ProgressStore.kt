package com.gianlucacelante.midipiano.learn

import android.content.Context

/** Salva e legge i progressi sul dispositivo (un solo JSON in SharedPreferences). */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun load(): Progress = Progress.fromJson(prefs.getString("p", "") ?: "")

    fun save(p: Progress) {
        prefs.edit().putString("p", p.toJson()).apply()
    }
}
