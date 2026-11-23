package com.gianlucacelante.midipiano.learn

import java.time.LocalDate

/**
 * Le sfide di oggi: una canzone e una prova (un esercizio) scelte in modo stabile in base alla data.
 * La scelta è "morbida": per la canzone diversa da ieri; per la prova prima gli esercizi da ripassare
 * (1-2 stelle), poi i prossimi nuovi, poi uno qualsiasi. Logica pura.
 */
object DailyChallenge {
    private fun mix(n: Long): Long {
        var x = n * 2654435761L + 0x9E3779B97F4A7C15uL.toLong()
        x = x xor (x ushr 29)
        x *= -4658895280553007687L
        x = x xor (x ushr 32)
        return x
    }

    private fun index(day: LocalDate, salt: Long, size: Int): Int = Math.floorMod(mix(day.toEpochDay() + salt), size.toLong()).toInt()

    fun songOfDay(day: LocalDate, songIds: List<String>): String? {
        val ids = songIds.sorted()
        if (ids.isEmpty()) return null
        var i = index(day, 0, ids.size)
        if (ids.size > 1 && i == index(day.minusDays(1), 0, ids.size)) i = (i + 1) % ids.size // mai la stessa di ieri
        return ids[i]
    }

    /** [exercises] nell'ordine dei corsi; [stars] dà le stelle già prese per esercizio. */
    fun trialOfDay(day: LocalDate, exercises: List<String>, stars: (String) -> Int): String? {
        if (exercises.isEmpty()) return null
        val review = exercises.filter { stars(it) in 1..2 }
        val pool = when {
            review.isNotEmpty() -> review
            else -> exercises.filter { stars(it) == 0 }.take(5).ifEmpty { exercises }
        }
        return pool[index(day, 7, pool.size)]
    }
}
