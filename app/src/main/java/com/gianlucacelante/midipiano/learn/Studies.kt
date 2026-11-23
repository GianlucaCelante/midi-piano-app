package com.gianlucacelante.midipiano.learn

/** Gli studi tecnici (Czerny, Burgmüller, Diabelli…) fra i brani: stanno nella sezione Esercizi, non fra i brani da suonare. */
object Studies {
    private val PREFIXES = listOf("czernyc-", "burgmullerjff-", "diabellia-", "hanon")

    fun isStudy(id: String): Boolean = PREFIXES.any { id.startsWith(it) }

    /** I corsi di tecnica: i loro esercizi compaiono nella sezione Esercizi, divisi per livello. */
    val TECHNIQUE_COURSES = setOf("tecnica-scale", "tecnica-quotidiana", "sinistra-pattern", "ritmi-di-oggi", "ritmo-figure", "arpeggi-accompagnamenti", "accordi-rivolti", "scale-arpeggi-tonalita", "accordi-tonalita", "prime-due-mani", "accordi-per-cominciare")
}
