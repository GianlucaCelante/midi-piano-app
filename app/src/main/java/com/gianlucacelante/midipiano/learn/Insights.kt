package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.notation.NoteNames
import kotlin.math.abs
import kotlin.math.roundToInt

/** Consigli a parole ricavati dalle statistiche. Logica pura. */
object Insights {
    fun keyName(key: Int): String = NoteNames.italian(key)

    /** Nome con l'ottava (do4 = il do centrale), per distinguere note con lo stesso nome. */
    fun keyNameOctave(key: Int): String = NoteNames.italian(key) + (key / 12 - 1)

    private fun handName(h: String) = when (h) { "RIGHT" -> "destra"; "LEFT" -> "sinistra"; else -> "due mani" }

    fun build(s: StatsReport): List<String> {
        if (s.runs == 0) return listOf("Suona qualche esercizio a tempo: le statistiche si riempiono man mano.")
        val out = ArrayList<String>()
        val t = s.timing

        if (t.tendency == "in anticipo") out += "Tendi ad anticipare i colpi di circa ${abs(t.meanDevMs).roundToInt()} ms: prova ad aspettare il tic prima di premere."
        else if (t.tendency == "in ritardo") out += "Tendi ad arrivare in ritardo di circa ${abs(t.meanDevMs).roundToInt()} ms: prova a premere un attimo prima del tic."
        else if (t.early + t.late > 0 || s.avgAccuracy != null) out += "Il tempo è preciso: in media sei a pochi millisecondi dal momento giusto."

        s.weakestHand?.let { w ->
            val best = s.byHand.filterKeys { it != w }.maxByOrNull { it.value.accuracy }
            val gap = if (best != null) best.value.accuracy - (s.byHand[w]?.accuracy ?: 0.0) else 0.0
            if (best != null && gap >= 0.10) out += "La mano ${handName(w)} è più debole della ${handName(best.key)} di ${(gap * 100).roundToInt()} punti: dedicale qualche giro a una mano sola."
        }

        if (t.holdShort >= 5 && t.holdShort >= t.holdLong * 2) out += "Lasci le note troppo presto: tienile premute per tutto il loro valore."
        else if (t.holdLong >= 5 && t.holdLong >= t.holdShort * 2) out += "Tieni le note troppo a lungo: rilasciale quando comincia la pausa."

        if (t.restEarly + t.restLate + t.restInRest >= 5) out += "Le pause ti danno qualche problema: conta i battiti in silenzio e riparti solo sul tic giusto."

        if (s.runs >= 10 && s.abandonRate >= 0.4) out += "Lasci spesso i giri a metà: prova a rallentare o a ripassare solo le battute difficili."

        s.improvement?.let { imp ->
            val points = (imp.delta * 100).roundToInt()
            if (points >= 5) out += "Stai migliorando: gli ultimi giri sono andati meglio di $points punti rispetto ai precedenti."
            else if (points <= -5) out += "Ultimamente va un po' peggio (${abs(points)} punti): una pausa o qualche giro più lento possono aiutare."
        }

        if (s.weakKeys.isNotEmpty()) out += "Le note che sbagli di più: " + s.weakKeys.take(3).joinToString(", ") { keyNameOctave(it.key) } + "."

        if (s.byPartOfDay.size >= 2) {
            val best = s.byPartOfDay.maxByOrNull { it.value }!!
            val worst = s.byPartOfDay.minByOrNull { it.value }!!
            if (best.value - worst.value >= 0.10) out += "Rendi di più la ${best.key}: ${(best.value * 100).roundToInt()}% di precisione contro il ${(worst.value * 100).roundToInt()}% della ${worst.key}."
        }
        return out
    }
}
