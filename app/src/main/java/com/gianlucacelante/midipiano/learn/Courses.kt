package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import org.json.JSONObject

sealed interface Slide {
    val id: String
    val title: String
}

data class TextSlide(override val id: String, override val title: String, val body: String) : Slide

data class ExerciseSlide(
    override val id: String,
    override val title: String,
    val text: String,
    val song: Song,
    val steps: List<LearnStep>,
    /** Valutazione della forza dei tasti (facoltativa). */
    val touch: com.gianlucacelante.midipiano.practice.Touch.Goal? = null,
    /** Cambi di pedale richiesti (facoltativi). */
    val pedal: com.gianlucacelante.midipiano.practice.PedalPlan? = null,
) : Slide

data class Lesson(val id: String, val title: String, val description: String, val slides: List<Slide>) {
    val exercises: List<ExerciseSlide> get() = slides.filterIsInstance<ExerciseSlide>()
}

data class Course(val id: String, val title: String, val description: String, val level: Int, val lessons: List<Lesson>) {
    val exercises: List<ExerciseSlide> get() = lessons.flatMap { it.exercises }
}

/** Lettura dei corsi dai file JSON (indice + un file per corso). */
object Courses {
    /** Esercizio che segue [slideId] nello stesso corso (stessa lezione, poi la successiva); null se è l'ultimo. */
    fun nextExercise(courses: List<Course>, slideId: String): ExerciseSlide? {
        for (c in courses) {
            val all = c.exercises
            val i = all.indexOfFirst { it.id == slideId }
            if (i >= 0) return all.getOrNull(i + 1)
        }
        return null
    }

    /** Dove riprendere: la lezione, l'esercizio da fare e quanti ne ha già fatti la lezione. */
    data class Resume(val course: Course, val lesson: Lesson, val exercise: ExerciseSlide, val done: Int, val total: Int)

    /**
     * Riprende dal punto in cui ci si era fermati: partendo dall'ultimo esercizio suonato, il primo non ancora fatto della sua
     * lezione; se la lezione è finita, il primo della lezione dopo (poi del corso dopo). Null se non c'è storia o è tutto fatto.
     */
    fun resume(courses: List<Course>, lastExerciseId: String?, starsOf: (String) -> Int): Resume? {
        if (lastExerciseId == null) return null
        val flat = courses.flatMap { c -> c.lessons.map { l -> c to l } }
        val start = flat.indexOfFirst { (_, l) -> l.exercises.any { it.id == lastExerciseId } }
        if (start < 0) return null
        // l'esercizio lasciato a metà è proprio quello da riprendere
        val (c0, l0) = flat[start]
        l0.exercises.firstOrNull { it.id == lastExerciseId && starsOf(it.id) == 0 }?.let { return Resume(c0, l0, it, l0.exercises.count { e -> starsOf(e.id) > 0 }, l0.exercises.size) }
        for (i in start until flat.size) {
            val (c, l) = flat[i]
            val next = l.exercises.firstOrNull { starsOf(it.id) == 0 } ?: continue
            return Resume(c, l, next, l.exercises.count { starsOf(it.id) > 0 }, l.exercises.size)
        }
        return null
    }

    /** La lezione che contiene la slide [slideId]. */
    fun lessonOf(courses: List<Course>, slideId: String): Lesson? =
        courses.firstNotNullOfOrNull { c -> c.lessons.firstOrNull { l -> l.slides.any { it.id == slideId } } }

    /** Primo esercizio non ancora completato, nell'ordine dei corsi (per "Continua"). */
    fun firstIncomplete(courses: List<Course>, starsOf: (String) -> Int): ExerciseSlide? =
        courses.asSequence().flatMap { it.exercises.asSequence() }.firstOrNull { starsOf(it.id) == 0 }

    fun parse(indexJson: String, readFile: (String) -> String): List<Course> {
        val index = JSONObject(indexJson).getJSONArray("courses")
        return (0 until index.length()).map { i ->
            val entry = index.getJSONObject(i)
            parseCourse(JSONObject(readFile(entry.getString("file"))), entry.optInt("level", 1), entry.optString("description", ""))
        }
    }

    private fun parseCourse(o: JSONObject, level: Int, description: String): Course {
        val lessons = o.getJSONArray("lessons")
        val courseId = o.getString("id")
        return Course(
            id = courseId, title = o.getString("title"), description = description, level = level,
            lessons = (0 until lessons.length()).map { li ->
                val l = lessons.getJSONObject(li)
                val slides = l.getJSONArray("slides")
                Lesson(
                    id = "$courseId/${l.getString("id")}", title = l.getString("title"), description = l.optString("description", ""),
                    slides = (0 until slides.length()).map { si -> parseSlide(slides.getJSONObject(si), "$courseId/${l.getString("id")}") },
                )
            },
        )
    }

    private fun parseSlide(s: JSONObject, lessonId: String): Slide {
        val id = "$lessonId/${s.getString("id")}"
        return when (val type = s.getString("type")) {
            "text" -> TextSlide(id, s.getString("title"), s.getString("body"))
            "exercise" -> {
                val song = InlineSongParser.parse(s.getJSONObject("song"), id)
                val steps = s.getJSONArray("steps")
                ExerciseSlide(
                    id, s.getString("title"), s.optString("text", ""), song,
                    (0 until steps.length()).map { parseStep(steps.getJSONObject(it)) }.let(::withNormalSpeedAfterSlow),
                    touch = com.gianlucacelante.midipiano.practice.Touch.parse(s.optString("touch", "").ifEmpty { null }),
                    pedal = s.optJSONArray("pedal")?.let { a ->
                        val spb = 60.0 / s.getJSONObject("song").getDouble("bpm")
                        com.gianlucacelante.midipiano.practice.PedalPlan((0 until a.length()).map { i -> a.getJSONObject(i).let { com.gianlucacelante.midipiano.practice.PedalEvent(it.getDouble("beat") * spb, it.getBoolean("down")) } })
                    },
                )
            }
            else -> throw IllegalArgumentException("Tipo di slide sconosciuto: $type")
        }
    }

    /** Se l'ultimo passo è la prova rallentata (facoltativa), segue sempre un passo a velocità normale: saltando si arriva lì. */
    fun withNormalSpeedAfterSlow(steps: List<LearnStep>): List<LearnStep> {
        val last = steps.lastOrNull() ?: return steps
        return if (last.optional) steps + LearnStep(last.hand, last.mode, 1.0, 1, "Ora a velocità normale.") else steps
    }

    private fun parseStep(o: JSONObject) = LearnStep(
        hand = when (o.getString("hand")) { "R" -> Hand.RIGHT; "L" -> Hand.LEFT; "B" -> Hand.BOTH; else -> throw IllegalArgumentException("mano non valida") },
        mode = when (o.getString("mode")) { "wait" -> StudyMode.WAIT; "tempo" -> StudyMode.TEMPO; else -> throw IllegalArgumentException("modalità non valida") },
        speed = o.optDouble("speed", 1.0),
        loops = o.optInt("loops", 1),
        text = o.optString("text", ""),
    )
}
