package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.learn.Courses
import com.gianlucacelante.midipiano.notation.Clef
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.ui.SheetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifica sui corsi veri: le pause dette dal controllo («la pausa dura N battiti») sono quelle disegnate sullo spartito,
 * e suonando alla perfezione non compare mai un commento sulle pause.
 */
class RestAuditTest {
    private val dir = File("src/main/assets/courses")
    private val exercises = Courses.parse(File(dir, "index.json").readText()) { File(dir, it).readText() }.flatMap { it.exercises }
    private fun ns(sec: Double) = (sec * 1e9).toLong()

    /** Istante reale (ns) in cui il brano è a [songSec]: con una mano sola la sessione parte dalla prima nota, saltando la pausa iniziale. */
    private fun at(s: PracticeSession, songSec: Double) = ns(songSec - s.songTimeAt(0L))

    private fun tempoSession(e: com.gianlucacelante.midipiano.learn.ExerciseSlide, hand: Hand): PracticeSession {
        val s = PracticeSession(e.song, hand)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.play(0L)
        return s
    }

    @Test fun playingPerfectlyNeverProducesARestComment() {
        for (e in exercises) for (hand in e.steps.map { it.hand }.toSet()) {
            val s = tempoSession(e, hand)
            for (st in e.song.steps(hand)) {
                s.tick(at(s, st.startSec))
                for (n in st.notes) {
                    assertEquals("${e.id} ($hand) a ${st.startSec}", NoteResult.CORRECT, s.onNoteOn(n.key, at(s, st.startSec)))
                    assertNull("${e.id} ($hand): commento inatteso a ${st.startSec}", s.takeTimingFeedback())
                }
            }
        }
    }

    /** Pause (in battiti) disegnate fra due istanti, per una sola chiave; null se non si può dire. */
    private fun drawnRestBeats(e: com.gianlucacelante.midipiano.learn.ExerciseSlide, hand: Hand, from: Double, to: Double): Double? {
        val clef = when (hand) { Hand.RIGHT -> Clef.TREBLE; Hand.LEFT -> Clef.BASS; else -> return null }
        val rests = SheetData.build(e.song).rests.filter { it.clef == clef && it.startSec >= from - 1e-6 && it.endSec <= to + 1e-6 }
        return rests.sumOf { it.beats }
    }

    @Test fun theRestLengthInTheMessageMatchesTheRestsDrawnOnTheStaff() {
        val problems = ArrayList<String>()
        var checked = 0
        for (e in exercises) for (hand in listOf(Hand.RIGHT, Hand.LEFT)) {
            if (e.song.notes.none { it.hand == hand }) continue
            val st = e.song.steps(hand)
            for ((a, b) in st.zipWithNext()) {
                val end = a.notes.maxOf { it.startSec + it.durationSec }
                val gap = e.song.beatsBetween(end, b.startSec)
                if (gap < 0.5) continue
                val drawn = drawnRestBeats(e, hand, end, b.startSec) ?: continue
                checked++
                if (kotlin.math.abs(drawn - gap) > 0.26) problems += "${e.id} $hand tra ${"%.2f".format(end)} e ${"%.2f".format(b.startSec)}: dice $gap battiti, disegnate $drawn"
            }
        }
        assertTrue("nessuna pausa controllata", checked > 0)
        assertTrue("pause incoerenti (${problems.size}/$checked):\n" + problems.take(15).joinToString("\n"), problems.isEmpty())
    }

    @Test fun earlyAndLateKeysAfterARestAreDescribedWithTheRealRestLength() {
        val problems = ArrayList<String>()
        var checked = 0
        for (e in exercises) for (hand in listOf(Hand.RIGHT, Hand.LEFT)) {
            if (e.song.notes.none { it.hand == hand }) continue
            val st = e.song.steps(hand)
            for ((idx, pair) in st.zipWithNext().withIndex()) {
                val (a, b) = pair
                val end = a.notes.maxOf { it.startSec + it.durationSec }
                val gap = e.song.beatsBetween(end, b.startSec)
                if (gap < 1.0) continue
                val spb = e.song.beatSecAt(b.startSec)
                // troppo presto di mezzo battito + un po': fuori finestra ma dentro la pausa
                val early = b.startSec - 0.9 * spb
                if (early <= end + 0.05) continue
                val s = tempoSession(e, hand)
                for (prev in st.take(idx + 1)) { // si è suonato bene fino alla nota prima della pausa
                    s.tick(at(s, prev.startSec))
                    for (n in prev.notes) s.onNoteOn(n.key, at(s, prev.startSec))
                }
                s.takeTimingFeedback()
                s.tick(at(s, early))
                val key = b.notes.first().key
                val r = s.onNoteOn(key, at(s, early))
                val f = s.takeTimingFeedback()
                checked++
                if (r != NoteResult.WRONG || f == null || f.kind != TimingKind.EARLY) problems += "${e.id} $hand presto a ${"%.2f".format(early)}: esito $r, commento ${f?.kind}"
                else if (kotlin.math.abs(f.restBeats - gap) > 0.01) problems += "${e.id} $hand: pausa detta ${f.restBeats}, vera $gap"
                else if (kotlin.math.abs(f.beats - 0.9) > 0.05) problems += "${e.id} $hand: scarto detto ${f.beats}, vero 0.9"
            }
        }
        assertTrue("nessun caso controllato", checked > 0)
        assertTrue("commenti sbagliati (${problems.size}/$checked):\n" + problems.take(15).joinToString("\n"), problems.isEmpty())
    }

    @Test fun lateKeysAndKeysPressedInsideARestAreDescribedCorrectly() {
        val problems = ArrayList<String>()
        var late = 0
        var inRest = 0
        for (e in exercises) for (hand in listOf(Hand.RIGHT, Hand.LEFT)) {
            if (e.song.notes.none { it.hand == hand }) continue
            val st = e.song.steps(hand)
            for ((idx, pair) in st.zipWithNext().withIndex()) {
                val (a, b) = pair
                val end = a.notes.maxOf { it.startSec + it.durationSec }
                val gap = e.song.beatsBetween(end, b.startSec)
                if (gap < 1.0) continue
                val spb = e.song.beatSecAt(b.startSec)
                fun played(): PracticeSession {
                    val s = tempoSession(e, hand)
                    for (prev in st.take(idx + 1)) { s.tick(at(s, prev.startSec)); for (n in prev.notes) s.onNoteOn(n.key, at(s, prev.startSec)) }
                    s.takeTimingFeedback()
                    return s
                }
                // tasto della nota dopo la pausa, ma in ritardo di 0,9 battiti (la nota è già scaduta)
                val lateAt = b.startSec + 0.9 * spb
                val nextAfter = st.getOrNull(idx + 2)?.startSec ?: Double.MAX_VALUE
                if (lateAt < nextAfter - 0.3 * spb) {
                    val s = played()
                    s.tick(at(s, lateAt))
                    val r = s.onNoteOn(b.notes.first().key, at(s, lateAt))
                    val f = s.takeTimingFeedback()
                    late++
                    if (r != NoteResult.WRONG || f?.kind != TimingKind.LATE) problems += "${e.id} $hand tardi a ${"%.2f".format(lateAt)}: $r ${f?.kind}"
                    else if (kotlin.math.abs(f.restBeats - gap) > 0.01 || kotlin.math.abs(f.beats - 0.9) > 0.05) problems += "${e.id} $hand tardi: pausa ${f.restBeats} (vera $gap), scarto ${f.beats}"
                }
                // un tasto qualsiasi a metà pausa: «qui c'è una pausa», con la durata giusta
                val mid = end + (b.startSec - end) / 2
                if (mid > end + 0.3 * spb && b.startSec - mid > 0.3 * spb) {
                    val s = played()
                    s.tick(at(s, mid))
                    val wrongKey = (b.notes.first().key + 1).coerceAtMost(100)
                    s.onNoteOn(if (e.song.notes.any { it.key == wrongKey }) 20 else wrongKey, at(s, mid))
                    val f = s.takeTimingFeedback()
                    inRest++
                    if (f?.kind != TimingKind.IN_REST) problems += "${e.id} $hand a metà pausa: ${f?.kind}"
                    else if (kotlin.math.abs(f.restBeats - gap) > 0.01) problems += "${e.id} $hand a metà pausa: pausa ${f.restBeats}, vera $gap"
                }
            }
        }
        assertTrue("nessun caso", late > 0 && inRest > 0)
        assertTrue("commenti sbagliati (${problems.size}; $late tardi, $inRest in pausa):" + problems.take(15).joinToString(prefix = " | ", separator = " | "), problems.isEmpty())
    }
}
