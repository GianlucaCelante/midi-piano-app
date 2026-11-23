package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.learn.Awards
import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFixesTest {
    private fun n(t: Double, key: Int, hand: Hand = Hand.RIGHT) = SongNote(t, 0.3, key, 80, hand)
    private fun sec(s: Double) = (s * 1e9).toLong()

    @Test
    fun skipBeforeSkipsEveryNoteBeforeTheStartIncludingTheOnesInsideTheWindow() {
        val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.9, 60), n(1.0, 62)))
        val e = TempoModeEngine(song.steps(Hand.BOTH), 0.2)
        e.skipBefore(1.0)
        e.onTick(2.0)
        assertEquals(1, e.misses) // solo la nota a 1.0: quella a 0.9 non si giudica
    }

    @Test
    fun aLoopStartingAfterANoteJustBeforeItDoesNotCountAPhantomMiss() {
        // battuta 2 inizia a 2.0; la nota a 1.9 chiude la battuta 1
        val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(1.9, 62), n(2.0, 64), n(3.0, 65)), barLines = listOf(2.0, 4.0))
        val s = PracticeSession(song, Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(2, 2)
        s.play(0L)
        s.tick(sec(0.0))
        s.tick(sec(0.1))
        assertEquals(0, s.misses)
    }

    @Test
    fun changingHandInWaitModeKeepsThePlace() {
        val song = Song("s", "t", null, SongSource("t", "t"),
            listOf(n(0.0, 72), n(0.0, 48, Hand.LEFT), n(1.0, 74), n(1.0, 50, Hand.LEFT), n(2.0, 76), n(2.0, 52, Hand.LEFT), n(3.0, 77), n(3.0, 53, Hand.LEFT)))
        val s = PracticeSession(song, Hand.BOTH)
        s.onNoteOn(72, 0L); s.onNoteOn(48, 0L)   // passo 0
        s.onNoteOn(74, 0L); s.onNoteOn(50, 0L)   // passo 1: ora si aspetta il passo a 2.0
        assertEquals(2.0, s.currentTimeSec, 1e-6)
        s.setHand(Hand.LEFT)
        assertEquals(2.0, s.currentTimeSec, 1e-6) // il punto non torna a 0
        assertEquals(setOf(52), s.expectedKeys)
    }

    @Test
    fun withTheCountInOffALoopFromALaterBarStillStartsAtTheLoop() {
        val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(2.0, 62), n(3.0, 64)), barLines = listOf(2.0, 4.0))
        val s = PracticeSession(song, Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(2, 2)
        s.play(0L)
        assertEquals(2.0, s.songTimeAt(0L), 1e-6)
        assertTrue(!s.isCountingIn)
    }

    @Test
    fun threeStarsAreOnlyPossibleAtFullSpeedForTheGenericAward() {
        assertEquals(3, Awards.stars(StudyMode.TEMPO, 1.0, 0.97, 0))
        assertEquals(2, Awards.stars(StudyMode.TEMPO, 0.75, 0.97, 0)) // per questo negli esercizi la velocità prescritta dal passo vale 1.0
    }

    @Test
    fun repeatedNotesCloseTogetherMatchTheOldestPendingOne() {
        val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(0.25, 60)))
        val e = TempoModeEngine(song.steps(Hand.BOTH), 0.2)
        assertEquals(HitKind.HIT, e.onNoteOn(60, 0.13).kind) // in ritardo sulla prima
        assertEquals(HitKind.HIT, e.onNoteOn(60, 0.30).kind) // e la seconda, in tempo
        assertEquals(2, e.hits)
        assertEquals(0, e.wrong)
        e.onTick(5.0)
        assertEquals(0, e.misses)
    }
}

class LoopSeamTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.3, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()

    private fun looped(): PracticeSession {
        val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(0.5, 62), n(1.0, 64), n(1.5, 65)), barLines = listOf(2.0, 4.0))
        val s = PracticeSession(song, Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.setLoopBars(1, 1)
        s.play(0L)
        return s
    }

    @Test
    fun aFirstNotePlayedJustBeforeTheLoopWrapsCountsForTheNextLap() {
        val s = looped()
        for ((t, k) in listOf(0.0 to 60, 0.5 to 62, 1.0 to 64, 1.5 to 65)) { s.tick(sec(t)); assertEquals(NoteResult.CORRECT, s.onNoteOn(k, sec(t))) }
        s.tick(sec(1.99))
        assertEquals(NoteResult.CORRECT, s.onNoteOn(60, sec(1.99))) // un attimo prima del giro: è la prima nota del giro dopo
        s.tick(sec(2.02)) // il giro si chiude
        for ((t, k) in listOf(0.5 to 62, 1.0 to 64, 1.5 to 65)) { s.tick(sec(2.0 + t)); assertEquals(NoteResult.CORRECT, s.onNoteOn(k, sec(2.0 + t))) }
        s.tick(sec(3.95))
        assertEquals(0, s.misses)
        assertEquals(0, s.mistakes)
    }
}

class SpeedTrainerTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.3, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()
    private val keys = listOf(0.0 to 60, 0.5 to 62, 1.0 to 64, 1.5 to 65)

    private fun trainer(speed: Double): PracticeSession {
        val song = Song("s", "t", null, SongSource("t", "t"), keys.map { n(it.first, it.second) }, barLines = listOf(2.0, 4.0))
        val s = PracticeSession(song, Hand.RIGHT)
        s.countInEnabled = false
        s.setMode(StudyMode.TEMPO, 0L)
        s.setSpeed(speed, 0L)
        s.setLoopBars(1, 1)
        s.speedTrainer = true
        s.play(0L)
        return s
    }

    @Test
    fun aCleanLapSpeedsUpToTheNextStep() {
        val s = trainer(0.5)
        for ((t, k) in keys) { val real = t / 0.5; s.tick(sec(real)); assertEquals(NoteResult.CORRECT, s.onNoteOn(k, sec(real))) }
        s.tick(sec(4.02)) // il giro finisce (a metà velocità servono 4 s reali)
        assertEquals(0.75, s.speed, 1e-9)
    }

    @Test
    fun aMessyLapSlowsDownOneStep() {
        val s = trainer(0.75)
        s.tick(sec(2.0)); s.onNoteOn(70, sec(2.0)) // nota sbagliata
        s.tick(sec(2.8)) // il resto non viene suonato: ci sono note mancate
        s.tick(sec(2.7 + 0.05)) // oltre la fine del giro (2 s di brano a 0,75 = 2,67 s reali)
        assertEquals(0.5, s.speed, 1e-9)
    }

    @Test
    fun speedStaysWithinTheRange() {
        val s = trainer(1.0)
        for ((t, k) in keys) { s.tick(sec(t)); s.onNoteOn(k, sec(t)) }
        s.tick(sec(2.02))
        assertEquals(1.0, s.speed, 1e-9) // già al massimo
    }
}

class ListenFirstTest {
    private fun n(t: Double, key: Int, hand: Hand) = SongNote(t, 0.3, key, 80, hand)
    private fun song() = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60, Hand.RIGHT), n(0.5, 48, Hand.LEFT), n(1.0, 62, Hand.RIGHT)), barLines = listOf(2.0), secPerBeat = 0.5)

    @Test
    fun thePreviewListensToEverythingAtFullSpeedThenGivesBackTheStudySetup() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.setMode(StudyMode.TEMPO, 0L)
        s.setSpeed(0.5, 0L)
        s.beginPreview(0L)
        assertTrue(s.isPreviewing)
        assertEquals(StudyMode.LISTEN, s.mode)
        assertEquals(1.0, s.speed, 1e-9)
        assertEquals(Hand.BOTH, s.hand)
        assertTrue(s.isPlaying)
        s.endPreview(1_000_000_000L)
        assertEquals(false, s.isPreviewing)
        assertEquals(StudyMode.TEMPO, s.mode)
        assertEquals(0.5, s.speed, 1e-9)
        assertEquals(Hand.RIGHT, s.hand)
        assertEquals(false, s.isPlaying) // si torna all'inizio, in pausa, pronti a suonare
        assertEquals(0.0, s.currentTimeSec, 1e-6)
    }

    @Test
    fun beginningTwiceOrEndingWithoutBeginningDoesNothingHarmful() {
        val s = PracticeSession(song(), Hand.RIGHT)
        s.endPreview(0L) // niente da chiudere
        assertEquals(StudyMode.WAIT, s.mode)
        s.beginPreview(0L); s.beginPreview(10L)
        s.endPreview(20L)
        assertEquals(StudyMode.WAIT, s.mode)
        assertEquals(Hand.RIGHT, s.hand)
    }
}
