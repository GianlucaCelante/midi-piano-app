package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MusicXmlParserTest {
    private val src = SongSource("test", "test")

    private fun note(step: String, octave: Int, dur: Int, extra: String = "", alter: Int? = null, staff: Int? = null) =
        "<note>$extra<pitch><step>$step</step>${alter?.let { "<alter>$it</alter>" } ?: ""}<octave>$octave</octave></pitch>" +
            "<duration>$dur</duration>${staff?.let { "<staff>$it</staff>" } ?: ""}</note>"

    private fun rest(dur: Int) = "<note><rest/><duration>$dur</duration></note>"

    private fun part(id: String, vararg measures: String, attrs: String = "<attributes><divisions>1</divisions></attributes>"): String {
        val body = measures.mapIndexed { i, m -> "<measure number=\"${i + 1}\">${if (i == 0) attrs else ""}$m</measure>" }.joinToString("")
        return "<part id=\"$id\">$body</part>"
    }

    private fun score(vararg parts: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" \"http://www.musicxml.org/dtds/partwise.dtd\">\n" +
            "<score-partwise version=\"4.0\"><part-list/>${parts.joinToString("")}</score-partwise>"

    private fun parse(xml: String) = MusicXmlParser.parse(xml.toByteArray(), "id", "titolo", null, src)

    @Test
    fun beatsAreTheNotatedLengthInQuarters() {
        val s = parse(score(part("P1", note("C", 4, 1) + note("D", 4, 2), attrs = "<attributes><divisions>2</divisions></attributes>")))
        assertEquals(listOf(0.5, 1.0), s.notes.map { it.beats })
    }

    @Test
    fun keepsTheWrittenSpelling() {
        val s = parse(score(part("P1", note("C", 4, 1, alter = 1) + note("B", 3, 1, alter = -1))))
        val cSharp = s.notes.first { it.key == 61 }
        val bFlat = s.notes.first { it.key == 58 }
        assertEquals(4 * 7 + 0, cSharp.diatonic); assertEquals(1, cSharp.alter)
        assertEquals(3 * 7 + 6, bFlat.diatonic); assertEquals(-1, bFlat.alter)
    }

    @Test
    fun readsKeyAndTimeSignature() {
        val attrs = "<attributes><divisions>1</divisions><key><fifths>-3</fifths></key><time><beats>6</beats><beat-type>8</beat-type></time></attributes>"
        val s = parse(score(part("P1", note("C", 4, 1), attrs = attrs)))
        assertEquals(-3, s.keyFifths)
        assertEquals(6, s.timeNumerator)
        assertEquals(8, s.timeDenominator)
    }

    @Test
    fun barLinesMarkTheStartOfEachMeasureAfterTheFirst() {
        val m = note("C", 4, 4) // 4 quarti = 2 s a 120 bpm
        val s = parse(score(part("P1", m, m, m)))
        assertEquals(listOf(2.0, 4.0), s.barLines)
    }

    @Test
    fun notesFollowEachOtherAt120Bpm() {
        val s = parse(score(part("P1", note("C", 4, 1) + note("D", 4, 1))))
        assertEquals(listOf(60, 62), s.notes.map { it.key })
        assertEquals(0.0, s.notes[0].startSec, 1e-9)
        assertEquals(0.5, s.notes[0].durationSec, 1e-9)
        assertEquals(0.5, s.notes[1].startSec, 1e-9)
    }

    @Test
    fun chordNotesShareTheStartTime() {
        val s = parse(score(part("P1", note("C", 4, 2) + note("E", 4, 2, extra = "<chord/>") + note("G", 4, 2, extra = "<chord/>"))))
        assertEquals(setOf(60, 64, 67), s.notes.map { it.key }.toSet())
        assertEquals(1, s.notes.map { it.startSec }.toSet().size)
    }

    @Test
    fun alterationsAndOctaves() {
        val s = parse(score(part("P1", note("C", 4, 1, alter = 1) + note("B", 3, 1, alter = -1) + note("A", 0, 1))))
        assertEquals(listOf(61, 58, 21), s.notes.map { it.key })
    }

    @Test
    fun restsAdvanceTime() {
        val s = parse(score(part("P1", note("C", 4, 1) + rest(2) + note("D", 4, 1))))
        assertEquals(1.5, s.notes[1].startSec, 1e-9)
    }

    @Test
    fun tiedNotesMergeIntoOne() {
        val tieStart = "<tie type=\"start\"/>"
        val tieStop = "<tie type=\"stop\"/>"
        val s = parse(score(part("P1", note("C", 4, 2, extra = "") .replace("</note>", "$tieStart</note>"), note("C", 4, 2).replace("</note>", "$tieStop</note>"))))
        assertEquals(1, s.notes.size)
        assertEquals(2.0, s.notes.single().durationSec, 1e-9) // 4 quarti a 120 bpm
    }

    @Test
    fun tempoMarkChangesSpeed() {
        val sound = "<direction><sound tempo=\"60\"/></direction>"
        val s = parse(score(part("P1", sound + note("C", 4, 1) + note("D", 4, 1))))
        assertEquals(1.0, s.notes[1].startSec, 1e-9)
    }

    @Test
    fun divisionsScaleDurations() {
        val s = parse(score(part("P1", note("C", 4, 2) + note("D", 4, 2), attrs = "<attributes><divisions>4</divisions></attributes>")))
        assertEquals(0.25, s.notes[0].durationSec, 1e-9) // 2/4 di quarto a 120 bpm
        assertEquals(0.25, s.notes[1].startSec, 1e-9)
    }

    @Test
    fun pianoStavesMapToHandsAndBackupAligns() {
        val attrs = "<attributes><divisions>1</divisions><staves>2</staves></attributes>"
        val m = note("C", 5, 2, staff = 1) + "<backup><duration>2</duration></backup>" + note("C", 3, 2, staff = 2)
        val s = parse(score(part("P1", m, attrs = attrs)))
        val right = s.notes.single { it.key == 72 }
        val left = s.notes.single { it.key == 48 }
        assertEquals(Hand.RIGHT, right.hand)
        assertEquals(Hand.LEFT, left.hand)
        assertEquals(right.startSec, left.startSec, 1e-9)
    }

    @Test
    fun voicePartIsIgnoredWhenAPianoPartExists() {
        val voice = part("P1", note("A", 4, 1))
        val piano = part("P2", note("C", 5, 1, staff = 1) + "<backup><duration>1</duration></backup>" + note("C", 3, 1, staff = 2),
            attrs = "<attributes><divisions>1</divisions><staves>2</staves></attributes>")
        val s = parse(score(voice, piano))
        assertEquals(setOf(72, 48), s.notes.map { it.key }.toSet())
    }

    @Test
    fun singleStavePartsSplitAtMiddleC() {
        val s = parse(score(part("P1", note("B", 3, 1) + note("C", 4, 1))))
        assertEquals(Hand.LEFT, s.notes.first { it.key == 59 }.hand)
        assertEquals(Hand.RIGHT, s.notes.first { it.key == 60 }.hand)
    }

    @Test
    fun graceNotesAreSkipped() {
        val grace = "<note><grace/><pitch><step>D</step><octave>4</octave></pitch></note>"
        val s = parse(score(part("P1", grace + note("C", 4, 1))))
        assertEquals(listOf(60), s.notes.map { it.key })
    }

    @Test
    fun readsCompressedMxl() {
        val xml = score(part("P1", note("C", 4, 1)))
        val container = "<container><rootfiles><rootfile full-path=\"score.xml\"/></rootfiles></container>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("META-INF/container.xml")); z.write(container.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("score.xml")); z.write(xml.toByteArray()); z.closeEntry()
        }
        val s = MusicXmlParser.parse(out.toByteArray(), "id", "t", null, src)
        assertEquals(listOf(60), s.notes.map { it.key })
    }

    @Test
    fun rejectsGarbageAndScoresWithoutNotes() {
        assertThrows(MusicXmlException::class.java) { parse("questo non è xml") }
        assertThrows(MusicXmlException::class.java) { parse(score(part("P1", rest(4)))) }
    }

    @Test
    fun doctypeWithBracketInsideInternalSubsetIsRejected() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x \"]\">]>" + score(part("P1", note("C", 4, 1))).substringAfter("?>\n").substringAfter(">\n")
        assertThrows(MusicXmlException::class.java) { parse(evil) }
    }

    @Test
    fun oversizedArchiveEntryIsRejectedWithoutExhaustingMemory() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("score.xml"))
            val chunk = ByteArray(1 shl 20)
            repeat(MusicXmlParser.MAX_ENTRY_BYTES / (1 shl 20) + 2) { z.write(chunk) }
            z.closeEntry()
        }
        assertThrows(MusicXmlException::class.java) { MusicXmlParser.parse(out.toByteArray(), "id", "t", null, src) }
    }

    @Test
    fun corruptArchiveIsAMusicXmlException() {
        val bad = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2, 3, 4, 5, 6, 7, 8)
        assertThrows(MusicXmlException::class.java) { MusicXmlParser.parse(bad, "id", "t", null, src) }
    }

    @Test
    fun notesOutsideThePianoRangeAreDropped() {
        val s = parse(score(part("P1", note("C", 9, 1) + note("C", 4, 1))))
        assertEquals(listOf(60), s.notes.map { it.key })
        assertThrows(MusicXmlException::class.java) { parse(score(part("P1", note("C", 9, 1)))) }
    }

    @Test
    fun honoursTheDeclaredEncoding() {
        val xml = score(part("P1", note("C", 4, 1))).replace("UTF-8", "ISO-8859-1")
            .replace("<part-list/>", "<work><work-title>Caffè</work-title></work><part-list/>")
        val s = MusicXmlParser.parse(xml.toByteArray(Charsets.ISO_8859_1), "id", null, null, src)
        assertEquals("Caffè", s.title)
    }

    private fun noteWithLyric(step: String, octave: Int, dur: Int, text: String, syllabic: String?) =
        "<note><pitch><step>$step</step><octave>$octave</octave></pitch><duration>$dur</duration>" +
            "<lyric number=\"1\">${syllabic?.let { "<syllabic>$it</syllabic>" } ?: ""}<text>$text</text></lyric></note>"

    private fun harmony(root: String, kind: String, rootAlter: Int? = null, bass: String? = null, bassAlter: Int? = null) =
        "<harmony><root><root-step>$root</root-step>${rootAlter?.let { "<root-alter>$it</root-alter>" } ?: ""}</root><kind>$kind</kind>" +
            (bass?.let { "<bass><bass-step>$it</bass-step>${bassAlter?.let { a -> "<bass-alter>$a</bass-alter>" } ?: ""}</bass>" } ?: "") + "</harmony>"

    @Test
    fun lyricsAttachToTheirNotesWithHyphensForSplitWords() {
        val m = noteWithLyric("C", 4, 1, "sol", "begin") + noteWithLyric("D", 4, 1, "le", "end") + noteWithLyric("E", 4, 1, "mare", "single")
        val s = parse(score(part("P1", m)))
        assertEquals(listOf("sol-", "le", "mare"), s.notes.map { it.lyric })
    }

    @Test
    fun notesWithoutLyricsHaveNone() {
        val s = parse(score(part("P1", note("C", 4, 1))))
        assertEquals(null, s.notes.single().lyric)
    }

    @Test
    fun chordSymbolsAreReadWithTheirPosition() {
        val m = harmony("F", "major") + note("C", 4, 2) + harmony("B", "major", rootAlter = -1, bass = "F") + note("D", 4, 2)
        val s = parse(score(part("P1", m, attrs = "<attributes><divisions>1</divisions></attributes>")))
        assertEquals(listOf("F", "B♭/F"), s.chordSymbols.map { it.text })
        assertEquals(0.0, s.chordSymbols[0].timeSec, 1e-9)
        assertEquals(1.0, s.chordSymbols[1].timeSec, 1e-9) // dopo una minima a 120 bpm
    }

    @Test
    fun chordKindsMapToTheUsualSuffixes() {
        val kinds = listOf("minor" to "Am", "dominant" to "A7", "major-seventh" to "Amaj7", "minor-seventh" to "Am7", "diminished" to "Adim", "suspended-fourth" to "Asus4")
        for ((kind, expected) in kinds) {
            val s = parse(score(part("P1", harmony("A", kind) + note("C", 4, 1))))
            assertEquals(kind, expected, s.chordSymbols.single().text)
        }
    }

    @Test
    fun usesWorkTitleAndComposerWhenPresent() {
        val xml = score(part("P1", note("C", 4, 1))).replace(
            "<part-list/>",
            "<work><work-title>Titolo di prova</work-title></work><identification><creator type=\"composer\">Autore X</creator></identification><part-list/>",
        )
        val s = MusicXmlParser.parse(xml.toByteArray(), "id", null, null, src)
        assertEquals("Titolo di prova", s.title)
        assertEquals("Autore X", s.composer)
    }

    @Test
    fun cueNotesAreNotPlayedAndDoNotMoveTheCursor() {
        val s = parse(score(part("P1", note("C", 4, 1) + note("E", 4, 1, extra = "<cue/>") + note("D", 4, 1))))
        // il richiamo (cue) non è una nota da suonare: restano Do e Re (il richiamo però occupa il suo tempo, quindi il Re è alla terza pulsazione)
        assertEquals(listOf(60, 62), s.notes.map { it.key })
    }
}

class MusicXmlChangesTest {
    private val src = SongSource("test", "test")
    private fun n(step: String, dur: Int) = "<note><pitch><step>$step</step><octave>4</octave></pitch><duration>$dur</duration></note>"
    private fun xml(body: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><score-partwise version=\"4.0\"><part-list/><part id=\"P1\">$body</part></score-partwise>"

    @org.junit.Test
    fun meterAndKeyChangesInLaterMeasuresAreRecorded() {
        val m1 = "<measure number=\"1\"><attributes><divisions>1</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time></attributes>${n("C", 4)}</measure>"
        val m2 = "<measure number=\"2\"><attributes><key><fifths>2</fifths></key><time><beats>3</beats><beat-type>4</beat-type></time></attributes>${n("D", 3)}</measure>"
        val s = MusicXmlParser.parse(xml(m1 + m2).toByteArray(), "i", "t", null, src)
        org.junit.Assert.assertEquals(listOf(TimeChange(2.0, 3, 4)), s.timeChanges)
        org.junit.Assert.assertEquals(listOf(KeyChange(2.0, 2)), s.keyChanges)
        org.junit.Assert.assertEquals(0, s.keyFifthsAt(1.0))
        org.junit.Assert.assertEquals(2, s.keyFifthsAt(2.5))
    }

    @org.junit.Test
    fun notesAfterAKeyChangeAreSpelledWithTheNewKeySignature() {
        // Fa# a 120 bpm dopo il passaggio a Sol maggiore (1 diesis): nessun diesis scritto davanti alla nota
        val m1 = "<measure number=\"1\"><attributes><divisions>1</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time></attributes>${n("C", 4)}</measure>"
        val m2 = "<measure number=\"2\"><attributes><key><fifths>1</fifths></key></attributes><note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch><duration>4</duration></note></measure>"
        val s = MusicXmlParser.parse(xml(m1 + m2).toByteArray(), "i", "t", null, src)
        val staff = com.gianlucacelante.midipiano.notation.NotationLayout.layout(s, Hand.BOTH).first { it.key == 66 }
        org.junit.Assert.assertNull(staff.accidental)
    }
}
