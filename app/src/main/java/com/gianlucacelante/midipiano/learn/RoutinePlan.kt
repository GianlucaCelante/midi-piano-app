package com.gianlucacelante.midipiano.learn

/** Quali esercizi della routine si fanno oggi. Logica pura. */
object RoutinePlan {
    private const val MAX_TARGETED = 2

    /** Gli esercizi di oggi: i primi [size] della routine (0 o più del totale = tutti). */
    fun queue(course: Course, size: Int): List<ExerciseSlide> {
        val all = course.exercises
        return if (size <= 0 || size >= all.size) all else all.take(size)
    }

    /** Quanti esercizi della routine si dedicano ai punti deboli (al massimo due, e mai più della metà). */
    fun targetedSlots(size: Int, total: Int): Int = minOf(MAX_TARGETED, (if (size <= 0 || size >= total) total else size) / 2)

    /**
     * Gli id della routine di oggi: i primi esercizi della routine base e, in coda, quelli dei corsi in cui l'utente sbaglia di più
     * ([hardIds], dal più difficile). Senza difficoltà note è la routine base di sempre. Logica pura.
     */
    fun planIds(course: Course, size: Int, hardIds: List<String>): List<String> {
        val base = queue(course, size).map { it.id }
        val baseAll = course.exercises.map { it.id }.toSet()
        val extra = hardIds.filter { it !in baseAll }.distinct().take(targetedSlots(size, course.exercises.size))
        return base.take(base.size - extra.size) + extra
    }

    /** Il primo esercizio non ancora fatto oggi; null se sono tutti fatti. */
    fun nextTodo(queue: List<ExerciseSlide>, doneToday: Set<String>): ExerciseSlide? = queue.firstOrNull { it.id !in doneToday }

    /** L'esercizio da fare dopo [currentId], saltando quelli già fatti oggi; null se non ce ne sono (o l'id non è della routine). */
    fun nextAfter(queue: List<ExerciseSlide>, currentId: String, doneToday: Set<String>): ExerciseSlide? {
        val i = queue.indexOfFirst { it.id == currentId }
        if (i < 0) return null
        return queue.drop(i + 1).firstOrNull { it.id !in doneToday }
    }

    fun isComplete(queue: List<ExerciseSlide>, doneToday: Set<String>): Boolean = queue.isNotEmpty() && queue.all { it.id in doneToday }
}
