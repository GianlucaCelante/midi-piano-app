package com.gianlucacelante.midipiano.practice

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ripartire da un punto qualsiasi del brano (anche una pausa) in modalità Aspetta. */
class WaitFreeSeekTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.4, key, 80, Hand.RIGHT)
    private fun sec(s: Double) = (s * 1e9).toLong()

    // note a 0, 2, 6 s: fra 2,4 e 6 s c'è uno spazio vuoto
    private val song = Song("s", "t", null, SongSource("t", "t"), listOf(n(0.0, 60), n(2.0, 62), n(6.0, 64)))
    private fun session() = PracticeSession(song, Hand.RIGHT).also { it.countInEnabled = false; it.autoFlow = true }

    @Test
    fun seekingToADeadSpotKeepsTheExactTime() {
        val s = session()
        s.seekTo(4.0, 0L)
        assertEquals(4.0, s.currentTimeSec, 1e-9) // non si aggancia ai 6 s della nota
        assertEquals(2, s.stepIndex) // ma la prossima nota da suonare è quella a 6 s
    }

    @Test
    fun fromADeadSpotPlayFlowsToTheNextNoteAndWaits() {
        val s = session()
        s.seekTo(4.0, 0L)
        s.play(sec(10.0))
        assertEquals(4.0, s.tick(sec(10.0)).songTimeSec, 1e-6)
        assertEquals(5.0, s.tick(sec(11.0)).songTimeSec, 1e-6)
        assertEquals(6.0, s.tick(sec(20.0)).songTimeSec, 1e-6) // arriva alla nota e aspetta
    }

    @Test
    fun seekingWhilePlayingResumesFromThatInstant() {
        val s = session()
        s.play(0L)
        s.tick(0L)
        s.seekTo(3.0, sec(0.5))
        assertEquals(3.0, s.tick(sec(0.5)).songTimeSec, 1e-6)
        assertEquals(3.5, s.tick(sec(1.0)).songTimeSec, 1e-6)
    }

    @Test
    fun pausingKeepsTheExactPlace() {
        val s = session()
        s.play(0L)
        s.tick(0L)
        s.pause(sec(0.5))
        assertFalse(s.isPlaying)
        assertEquals(0.0, s.currentTimeSec, 1e-6) // fermo sulla prima nota (si aspettava)
        val t = session()
        t.seekTo(3.0, 0L)
        t.play(sec(1.0)); t.tick(sec(1.0)); t.pause(sec(2.0))
        assertEquals(4.0, t.currentTimeSec, 1e-6) // fermo dove si era arrivati, non sulla nota dopo
    }

    @Test
    fun seekingAfterTheLastNoteDoesNotFinishTheSong() {
        val s = session()
        s.seekTo(7.0, 0L)
        assertFalse(s.isFinished)
        assertEquals(7.0, s.currentTimeSec, 1e-9)
        assertTrue(s.stepIndex <= 2)
    }

    @Test
    fun aPlayedNoteAfterAFreeSeekStillAdvancesNormally() {
        val s = session()
        s.seekTo(4.0, 0L)
        s.play(0L); s.tick(sec(2.0)); // arrivato a 6 s e fermo
        assertEquals(NoteResult.FINISHED, s.onNoteOn(64, sec(2.0)))
    }
}
