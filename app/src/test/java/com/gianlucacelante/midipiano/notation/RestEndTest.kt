package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestEndTest {
    @Test fun everyRestKnowsWhereItEnds() {
        val notes = listOf(
            SongNote(0.0, 0.5, 60, 80, Hand.RIGHT, beats = 1.0),
            SongNote(3.0, 0.5, 62, 80, Hand.RIGHT, beats = 1.0), // fra le due note c'è una pausa di 2 quarti e mezzo
        )
        val song = Song("s", "t", null, SongSource("t", "t"), notes, barLines = listOf(2.0), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)
        val chords = Chords.group(NotationLayout.layout(song, Hand.BOTH).filter { it.clef == Clef.TREBLE })
        val rests = Rests.compute(chords, song)
        assertTrue(rests.isNotEmpty())
        for (r in rests) {
            assertEquals(song.secAfter(r.startSec, r.beats), r.endSec, 1e-9)
            assertTrue(r.endSec > r.startSec)
        }
        // le pause non attraversano la stanghetta a 2,0 s e coprono il vuoto fra 0,5 e 3,0
        assertTrue(rests.all { it.endSec <= 2.0 + 1e-6 || it.startSec >= 2.0 - 1e-6 })
        assertEquals(3.0, rests.maxOf { it.endSec }, 1e-6)
    }
}
