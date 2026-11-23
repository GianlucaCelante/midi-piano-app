package com.gianlucacelante.midipiano.learn

import java.time.LocalDate

/** Numeri per la schermata dei progressi. Logica pura. */
object Stats {
    /** Minuti di pratica degli ultimi [days] giorni, dal più vecchio a [today]. */
    fun minutesByDay(p: Progress, today: LocalDate, days: Int): List<Pair<LocalDate, Int>> =
        (days - 1 downTo 0).map { back -> val d = today.minusDays(back.toLong()); d to (p.secondsOn(d) / 60).toInt() }
}

/** Traguardi: si calcolano dai progressi, senza stato proprio. */
object Achievements {
    data class Input(
        val exercises: Int, val threeStars: Int, val songs: Int, val streak: Int, val minutes: Int,
        val courses: Int, val dailyDays: Int, val takes: Int, val runs: Int,
    )

    data class Achievement(val id: String, val title: String, val description: String, val current: Int, val target: Int) {
        val unlocked: Boolean get() = current >= target
    }

    fun compute(i: Input): List<Achievement> = listOf(
        Achievement("first-exercise", "Primo passo", "Completa il tuo primo esercizio", i.exercises, 1),
        Achievement("ten-exercises", "In cammino", "Completa 10 esercizi", i.exercises, 10),
        Achievement("twenty-five-exercises", "Allievo costante", "Completa 25 esercizi", i.exercises, 25),
        Achievement("first-three-stars", "Tre stelle", "Prendi 3 stelle in un esercizio", i.threeStars, 1),
        Achievement("ten-three-stars", "Mano sicura", "Prendi 3 stelle in 10 esercizi", i.threeStars, 10),
        Achievement("first-course", "Corso finito", "Completa tutti gli esercizi di un corso", i.courses, 1),
        Achievement("five-songs", "Repertorio", "Suona 5 brani fino in fondo", i.songs, 5),
        Achievement("streak-3", "Tre giorni di fila", "Suona 3 giorni di fila", i.streak, 3),
        Achievement("streak-7", "Una settimana", "Suona 7 giorni di fila", i.streak, 7),
        Achievement("streak-30", "Un mese", "Suona 30 giorni di fila", i.streak, 30),
        Achievement("hundred-minutes", "Cento minuti", "Accumula 100 minuti di pratica", i.minutes, 100),
        Achievement("daily-5", "Sfide del giorno", "Completa la canzone o la prova del giorno per 5 giorni", i.dailyDays, 5),
        Achievement("first-take", "Registrato", "Salva la tua prima esecuzione", i.takes, 1),
        Achievement("ten-runs", "Prove a tempo", "Finisci 10 brani in modalità A tempo", i.runs, 10),
    )
}
