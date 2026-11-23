package com.gianlucacelante.midipiano.notation

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNamesTest {
    @Test
    fun italianNamesForNaturalNotes() {
        assertEquals("do", NoteNames.italian(60))
        assertEquals("re", NoteNames.italian(62))
        assertEquals("mi", NoteNames.italian(64))
        assertEquals("fa", NoteNames.italian(65))
        assertEquals("sol", NoteNames.italian(67))
        assertEquals("la", NoteNames.italian(69))
        assertEquals("si", NoteNames.italian(71))
    }

    @Test
    fun octaveDoesNotChangeTheName() {
        assertEquals(NoteNames.italian(60), NoteNames.italian(36))
        assertEquals(NoteNames.italian(60), NoteNames.italian(96))
    }

    @Test
    fun blackKeysUseSharpSymbol() {
        assertEquals("do♯", NoteNames.italian(61))
        assertEquals("fa♯", NoteNames.italian(66))
    }
}

class SpelledNoteNamesTest {
    // indice diatonico: ottava*7 + passo (Do=0 ... Si=6); il La4 è 4*7+5 = 33, il Si4 è 34
    @Test
    fun theNameFollowsTheWrittenNoteNotJustThePitch() {
        assertEquals("si♭", NoteNames.spelled(70, 34)) // Si bemolle scritto sul Si
        assertEquals("la♯", NoteNames.spelled(70, 33)) // La diesis scritto sul La
        assertEquals("mi♭", NoteNames.spelled(63, 4 * 7 + 2))
        assertEquals("re♯", NoteNames.spelled(63, 4 * 7 + 1))
    }

    @Test
    fun naturalAndLowOctaveNotesKeepTheirName() {
        assertEquals("do", NoteNames.spelled(60, 4 * 7))
        assertEquals("sol", NoteNames.spelled(43, 2 * 7 + 4))
        assertEquals("si", NoteNames.spelled(59, 3 * 7 + 6))
    }

    @Test
    fun theOctaveBoundaryIsHandled() {
        assertEquals("si♯", NoteNames.spelled(60, 3 * 7 + 6)) // Do centrale scritto come Si diesis
        assertEquals("do♭", NoteNames.spelled(59, 4 * 7)) // Si naturale scritto come Do bemolle
    }
}
