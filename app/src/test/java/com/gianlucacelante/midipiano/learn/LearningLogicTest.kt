package com.gianlucacelante.midipiano.learn

import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LearningLogicTest {
    // ---- esercizi scritti in JSON ----

    private fun song(json: String) = InlineSongParser.parse(JSONObject(json), "ex1")

    private val sample = """
        {"title":"Prova","timeSignature":[4,4],"keyFifths":0,"bpm":120,
         "notes":[{"beat":0.0,"dur":1.0,"key":60,"hand":"R"},{"beat":1.0,"dur":1.0,"key":62,"hand":"R"},
                  {"beat":0.0,"dur":4.0,"key":48,"hand":"L"},{"beat":4.0,"dur":4.0,"key":55,"hand":"L"}]}
    """

    @Test
    fun beatsBecomeSecondsFromTheTempo() {
        val s = song(sample)
        assertEquals(0.5, s.secPerBeat, 1e-9)
        val d = s.notes.first { it.key == 62 }
        assertEquals(0.5, d.startSec, 1e-9)
        assertEquals(0.5, d.durationSec, 1e-9)
        assertEquals(1.0, d.beats, 1e-9)
    }

    @Test
    fun handsAndSignaturesAreRead() {
        val s = song(sample.replace("\"keyFifths\":0", "\"keyFifths\":-1").replace("[4,4]", "[3,4]"))
        assertEquals(Hand.RIGHT, s.notes.first { it.key == 60 }.hand)
        assertEquals(Hand.LEFT, s.notes.first { it.key == 48 }.hand)
        assertEquals(-1, s.keyFifths)
        assertEquals(3, s.timeNumerator)
    }

    @Test
    fun barLinesFallBetweenBarsNotAtTheEnd() {
        val s = song(sample) // 8 quarti in 4/4: una stanghetta a 4 quarti = 2.0 s
        assertEquals(listOf(2.0), s.barLines)
    }

    @Test
    fun invalidExercisesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { song(sample.replace("\"key\":60", "\"key\":5")) }
        assertThrows(IllegalArgumentException::class.java) { song(sample.replace("\"hand\":\"R\"", "\"hand\":\"X\"")) }
        assertThrows(IllegalArgumentException::class.java) { song(sample.replace("\"dur\":1.0,\"key\":60", "\"dur\":0,\"key\":60")) }
        assertThrows(IllegalArgumentException::class.java) { song("""{"title":"x","bpm":0,"notes":[]}""") }
    }

    // ---- stelle ----

    @Test
    fun waitModeStarsFollowTheMistakes() {
        assertEquals(3, Awards.stars(StudyMode.WAIT, 1.0, accuracy = 1.0, mistakes = 0))
        assertEquals(2, Awards.stars(StudyMode.WAIT, 1.0, 1.0, mistakes = 2))
        assertEquals(1, Awards.stars(StudyMode.WAIT, 1.0, 1.0, mistakes = 9))
    }

    @Test
    fun tempoStarsFollowTheAccuracyAndSlowSpeedsCostOne() {
        assertEquals(3, Awards.stars(StudyMode.TEMPO, 1.0, 0.97, 0))
        assertEquals(2, Awards.stars(StudyMode.TEMPO, 1.0, 0.88, 0))
        assertEquals(1, Awards.stars(StudyMode.TEMPO, 1.0, 0.6, 0))
        assertEquals(2, Awards.stars(StudyMode.TEMPO, 0.5, 0.97, 0)) // a velocità ridotta: massimo 2
        assertEquals(1, Awards.stars(StudyMode.TEMPO, 0.5, 0.6, 0)) // mai meno di 1
    }

    // ---- passi di un esercizio ----

    private fun step(loops: Int, mode: StudyMode = StudyMode.WAIT) = LearnStep(Hand.RIGHT, mode, 1.0, loops, "t")

    @Test
    fun aStepNeedsTheRequiredNumberOfGoodRuns() {
        val r = ExerciseRunner(listOf(step(2), step(1)))
        assertEquals(RunOutcome.NextRun(runsLeft = 1), r.onRunFinished(passed = true, stars = 3))
        assertEquals(RunOutcome.StepAdvanced(1), r.onRunFinished(true, 3))
        assertEquals(1, r.stepIndex)
    }

    @Test
    fun aBadRunDoesNotCountButTheStepKeepsItsProgress() {
        val r = ExerciseRunner(listOf(step(2)))
        r.onRunFinished(true, 3)
        assertEquals(RunOutcome.Retry(runsLeft = 1), r.onRunFinished(false, 1))
        assertEquals(RunOutcome.Done(stars = 2), r.onRunFinished(true, 2))
    }

    @Test
    fun theFinalStarsComeFromTheLastGoodRun() {
        val r = ExerciseRunner(listOf(step(1), step(1)))
        r.onRunFinished(true, 3)
        assertEquals(RunOutcome.Done(stars = 1), r.onRunFinished(true, 1))
        assertTrue(r.isDone)
    }

    @Test
    fun anExerciseWithoutStepsIsDoneImmediately() {
        assertTrue(ExerciseRunner(emptyList()).isDone)
    }

    // ---- progressi ----

    @Test
    fun starsKeepTheBestResult() {
        val p = Progress()
        p.recordExercise("e1", 2)
        p.recordExercise("e1", 1)
        assertEquals(2, p.starsOf("e1"))
        p.recordExercise("e1", 3)
        assertEquals(3, p.starsOf("e1"))
        assertEquals(0, p.starsOf("sconosciuto"))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        val p = Progress()
        val today = LocalDate.of(2026, 11, 22)
        assertEquals(0, p.streak(today))
        for (d in 20..22) p.recordPractice(60, LocalDate.of(2026, 11, d))
        assertEquals(3, p.streak(today))
        assertEquals(3, p.streak(today.plusDays(1))) // oggi non ancora suonato: la serie resta se ieri sì
        assertEquals(0, p.streak(today.plusDays(2))) // due giorni di pausa: serie spezzata
    }

    @Test
    fun aGapBreaksTheStreak() {
        val p = Progress()
        p.recordPractice(30, LocalDate.of(2026, 11, 18))
        p.recordPractice(30, LocalDate.of(2026, 11, 21))
        p.recordPractice(30, LocalDate.of(2026, 11, 22))
        assertEquals(2, p.streak(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun practiceSecondsAccumulate() {
        val p = Progress()
        p.recordPractice(90, LocalDate.of(2026, 11, 22))
        p.recordPractice(30, LocalDate.of(2026, 11, 22))
        assertEquals(120L, p.totalPracticeSeconds)
    }

    @Test
    fun favoritesToggle() {
        val p = Progress()
        assertTrue(p.toggleFavorite("a"))
        assertTrue(p.isFavorite("a"))
        assertFalse(p.toggleFavorite("a"))
        assertFalse(p.isFavorite("a"))
    }

    @Test
    fun progressSurvivesASaveAndLoad() {
        val p = Progress()
        p.recordExercise("e1", 3)
        p.recordPractice(75, LocalDate.of(2026, 11, 22))
        p.toggleFavorite("song-1")
        p.lastExerciseId = "e1"
        val q = Progress.fromJson(p.toJson())
        assertEquals(3, q.starsOf("e1"))
        assertEquals(75L, q.totalPracticeSeconds)
        assertTrue(q.isFavorite("song-1"))
        assertEquals("e1", q.lastExerciseId)
        assertEquals(1, q.streak(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun corruptSavedDataStartsFresh() {
        assertEquals(0, Progress.fromJson("non è json").starsOf("e1"))
        assertEquals(0L, Progress.fromJson("").totalPracticeSeconds)
    }

    @Test
    fun lessonCompletionCountsItsExercises() {
        val p = Progress()
        p.recordExercise("a", 1)
        assertEquals(1 to 3, p.completion(listOf("a", "b", "c")))
        p.recordExercise("b", 2); p.recordExercise("c", 3)
        assertEquals(3 to 3, p.completion(listOf("a", "b", "c")))
    }

    // ---- difficoltà e copertine ----

    private val whiteKeys = listOf(60, 62, 64, 65, 67, 69, 71, 72)

    private fun gen(count: Int, perSec: Double, bothHands: Boolean, fifths: Int = 0): Song {
        val notes = (0 until count).map {
            SongNote(it / perSec, 0.2, whiteKeys[it % whiteKeys.size] + if (bothHands && it % 2 == 0) -12 else 0, 80, if (bothHands && it % 2 == 0) Hand.LEFT else Hand.RIGHT)
        }
        val end = notes.last().startSec + 1.0
        val bars = generateSequence(2.0) { it + 2.0 }.takeWhile { it < end }.toList() // battute da 2 s (4/4 a 120 bpm)
        return Song("s", "t", null, SongSource("t", "t"), notes, barLines = bars, keyFifths = fifths)
    }

    @Test
    fun simpleOneHandPiecesAreEasierThanDenseTwoHandedOnes() {
        val easy = Difficulty.of(gen(20, 1.0, bothHands = false))
        val hard = Difficulty.of(gen(400, 8.0, bothHands = true, fifths = 4))
        assertTrue(hard > easy)
        assertTrue(easy in 1..5 && hard in 1..5)
        assertEquals(1, easy)
    }

    @Test
    fun anEmptySongIsLevelOne() {
        assertEquals(1, Difficulty.of(Song("s", "t", null, SongSource("t", "t"), emptyList())))
    }

    @Test
    fun coverColorsAreStablePerSongAndDifferAcrossSongs() {
        assertEquals(Covers.colors("bach-1"), Covers.colors("bach-1"))
        assertNotEquals(Covers.colors("bach-1"), Covers.colors("beethoven-9"))
        assertEquals("BW", Covers.initials("Bach Wohltemperiertes"))
        assertEquals("P", Covers.initials("Polonaise"))
        assertEquals("?", Covers.initials(""))
    }

    // ---- prova rallentata facoltativa ----

    private fun slow() = LearnStep(com.gianlucacelante.midipiano.song.Hand.RIGHT, com.gianlucacelante.midipiano.practice.StudyMode.TEMPO, 0.75, 2, "")
    private fun normal() = LearnStep(com.gianlucacelante.midipiano.song.Hand.RIGHT, com.gianlucacelante.midipiano.practice.StudyMode.TEMPO, 1.0, 2, "")
    private fun waitStep() = LearnStep(com.gianlucacelante.midipiano.song.Hand.RIGHT, com.gianlucacelante.midipiano.practice.StudyMode.WAIT, 1.0, 1, "")

    @Test
    fun onlySlowedTempoStepsAreOptional() {
        assertTrue(slow().optional); assertFalse(normal().optional); assertFalse(waitStep().optional)
    }

    @Test
    fun skippingTheSlowStepGoesToTheNextOne() {
        val r = ExerciseRunner(listOf(waitStep(), slow(), normal()))
        r.onRunFinished(true, 3) // finito il passo «aspetta»: ora si è sulla prova rallentata
        assertEquals(RunOutcome.StepAdvanced(2), r.skipOptionalStep())
        assertEquals(2, r.stepIndex)
    }

    @Test
    fun skippingTheLastSlowStepClosesTheExerciseWithTheStarsAlreadyEarned() {
        val r = ExerciseRunner(listOf(waitStep(), slow()))
        r.onRunFinished(true, 3) // passo «aspetta» riuscito con 3 stelle
        assertEquals(RunOutcome.Done(3), r.skipOptionalStep())
        assertTrue(r.isDone)
    }

    @Test
    fun aRequiredStepCannotBeSkipped() {
        val r = ExerciseRunner(listOf(waitStep(), normal()))
        assertNull(r.skipOptionalStep())
        assertEquals(0, r.stepIndex)
    }

    @Test
    fun aSlowLastStepIsFollowedByANormalSpeedOne() {
        val steps = Courses.withNormalSpeedAfterSlow(listOf(waitStep(), slow()))
        assertEquals(3, steps.size)
        assertEquals(1.0, steps.last().speed, 0.0)
        assertFalse(steps.last().optional)
        val r = ExerciseRunner(steps)
        r.onRunFinished(true, 3)
        assertEquals(RunOutcome.StepAdvanced(2), r.skipOptionalStep()) // saltando la rallentata si va al tempo normale, non alla fine
        assertFalse(r.isDone)
        assertEquals(steps, Courses.withNormalSpeedAfterSlow(steps)) // già a posto: non si aggiunge altro
    }

    @Test
    fun draggingTheScoreBackInAnExerciseRestartsTheRun() {
        assertTrue(seekRestartsExerciseRun(targetSec = 2.0, currentSec = 8.0, finished = false)) // indietro: si ricomincia
        assertFalse(seekRestartsExerciseRun(targetSec = 12.0, currentSec = 8.0, finished = false)) // avanti: non si salta
        assertFalse(seekRestartsExerciseRun(targetSec = 7.8, currentSec = 8.0, finished = false)) // un piccolo tremolio non conta
        assertFalse(seekRestartsExerciseRun(targetSec = 0.0, currentSec = 8.0, finished = true)) // esercizio concluso: niente
    }
}
