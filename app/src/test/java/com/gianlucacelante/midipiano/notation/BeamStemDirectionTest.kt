package com.gianlucacelante.midipiano.notation

import com.gianlucacelante.midipiano.song.Hand
import com.gianlucacelante.midipiano.song.Song
import com.gianlucacelante.midipiano.song.SongNote
import com.gianlucacelante.midipiano.song.SongSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le note legate da una trave hanno tutte lo stesso verso dei gambi: lo decide il gruppo, e il disegno (nomi e legature) deve seguirlo. */
class BeamStemDirectionTest {
    private fun n(t: Double, key: Int) = SongNote(t, 0.25, key, 80, Hand.RIGHT, beats = 0.5)
    private fun groupOf(vararg keys: Int): BeamGroup {
        val notes = keys.mapIndexed { i, k -> n(i * 0.25, k) }
        val song = Song("s", "t", null, SongSource("t", "t"), notes, barLines = emptyList(), timeNumerator = 4, timeDenominator = 4, secPerBeat = 0.5)
        val chords = Chords.group(NotationLayout.layout(song, Hand.BOTH))
        return Beaming.groups(chords, song).first()
    }

    @Test fun lowNotesGetStemsUp() { assertEquals(true, groupOf(60, 62, 64, 65).stemUp) }
    @Test fun highNotesGetStemsDown() { assertEquals(false, groupOf(79, 81, 83, 84).stemUp) }
    @Test fun aMixedGroupFollowsTheFarthestNoteFromTheMiddleLine() {
        assertEquals(false, groupOf(72, 84, 76, 79).stemUp) // la nota più lontana dal centro è in alto: gambi in giù
    }
}
