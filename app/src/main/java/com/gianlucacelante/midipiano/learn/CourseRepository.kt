package com.gianlucacelante.midipiano.learn

import android.content.Context

/** Corsi inclusi nell'app (assets/courses). Lettura bloccante: chiamare fuori dal thread principale. */
class CourseRepository(private val context: Context) {
    fun load(): List<Course> {
        fun read(name: String) = context.assets.open("courses/$name").bufferedReader().use { it.readText() }
        return Courses.parse(read("index.json"), ::read)
    }
}
